/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.networkstack.tethering;

import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static android.net.TetheringManager.TETHERING_WIFI_P2P;
import static android.net.util.PrefixUtils.asIpPrefix;

import static com.android.net.module.util.Inet4AddressUtils.inet4AddressToIntHTH;
import static com.android.net.module.util.Inet4AddressUtils.prefixLengthToV4NetmaskIntHTH;

import static java.util.Arrays.asList;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.Network;
import android.net.ip.IpServer;
import android.net.util.PrefixUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.SparseArray;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * This class coordinate IP addresses conflict problem.
 *
 * Tethering downstream IP addresses may conflict with network assigned addresses. This
 * coordinator is responsible for recording all of network assigned addresses and dispatched
 * free address to downstream interfaces.
 *
 * This class is not thread-safe and should be accessed on the same tethering internal thread.
 * @hide
 */
public class PrivateAddressCoordinator {
    public static final int PREFIX_LENGTH = 24;

    // Upstream monitor would be stopped when tethering is down. When tethering restart, downstream
    // address may be requested before coordinator get current upstream notification. To ensure
    // coordinator do not select conflict downstream prefix, mUpstreamPrefixMap would not be cleared
    // when tethering is down. Instead tethering would remove all deprecated upstreams from
    // mUpstreamPrefixMap when tethering is starting. See #maybeRemoveDeprecatedUpstreams().
    private final ArrayMap<Network, List<IpPrefix>> mUpstreamPrefixMap;
    private final ArraySet<IpServer> mDownstreams;
    private static final String LEGACY_WIFI_P2P_IFACE_ADDRESS = "192.168.49.1/24";
    private static final String LEGACY_BLUETOOTH_IFACE_ADDRESS = "192.168.44.1/24";
    private final ArrayList<IpPrefix> mReservedPrefixes;
    private final ArrayList<IpPrefix> mTetheringPrefixes;
    private final ConnectivityManager mConnectivityMgr;
    private final TetheringConfiguration mConfig;
    private final SparseArray<LinkAddress> mCachedAddress;

    public PrivateAddressCoordinator(Context context, TetheringConfiguration config) {
        mDownstreams = new ArraySet<>();
        mUpstreamPrefixMap = new ArrayMap<>();
        mConnectivityMgr = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        mConfig = config;
        mCachedAddress = new SparseArray();

        mTetheringPrefixes = new ArrayList<>(Arrays.asList(
                new IpPrefix("192.168.0.0/16"),
                new IpPrefix("172.16.0.0/12"),
                new IpPrefix("10.0.0.0/8")));

        mReservedPrefixes = new ArrayList<>(Arrays.asList(
                new IpPrefix(LEGACY_WIFI_P2P_IFACE_ADDRESS),
                new IpPrefix(LEGACY_BLUETOOTH_IFACE_ADDRESS)));
    }

    /**
     * Record a new upstream IpPrefix which may conflict with tethering downstreams.
     * The downstreams will be notified if a conflict is found. When updateUpstreamPrefix is called,
     * UpstreamNetworkState must have an already populated LinkProperties.
     */
    public void updateUpstreamPrefix(final UpstreamNetworkState ns) {
        // Do not support VPN as upstream
        if (ns.networkCapabilities != null && ns.networkCapabilities.hasTransport(TRANSPORT_VPN)) {
            removeUpstreamPrefix(ns.network);
            return;
        }

        final ArrayList<IpPrefix> ipv4Prefixes = getIpv4Prefixes(
                ns.linkProperties.getAllLinkAddresses());
        if (ipv4Prefixes.isEmpty()) {
            removeUpstreamPrefix(ns.network);
            return;
        }

        mUpstreamPrefixMap.put(ns.network, ipv4Prefixes);
        handleMaybePrefixConflict(ipv4Prefixes);
    }

    private ArrayList<IpPrefix> getIpv4Prefixes(final List<LinkAddress> linkAddresses) {
        final ArrayList<IpPrefix> list = new ArrayList<>();
        for (LinkAddress address : linkAddresses) {
            if (!address.isIpv4()) continue;

            list.add(PrefixUtils.asIpPrefix(address));
        }

        return list;
    }

    private void handleMaybePrefixConflict(final List<IpPrefix> prefixes) {
        for (IpServer downstream : mDownstreams) {
            final IpPrefix target = getDownstreamPrefix(downstream);
            if (target == null) continue;

            for (IpPrefix source : prefixes) {
                if (isConflictPrefix(source, target)) {
                    downstream.sendMessage(IpServer.CMD_NOTIFY_PREFIX_CONFLICT);
                    break;
                }
            }
        }
    }

    /** Remove IpPrefix records corresponding to input network. */
    public void removeUpstreamPrefix(final Network network) {
        mUpstreamPrefixMap.remove(network);
    }

    /**
     * Maybe remove deprecated upstream records, this would be called once tethering started without
     * any exiting tethered downstream.
     */
    public void maybeRemoveDeprecatedUpstreams() {
        if (mUpstreamPrefixMap.isEmpty()) return;

        // Remove all upstreams that are no longer valid networks
        final Set<Network> toBeRemoved = new HashSet<>(mUpstreamPrefixMap.keySet());
        toBeRemoved.removeAll(asList(mConnectivityMgr.getAllNetworks()));

        mUpstreamPrefixMap.removeAll(toBeRemoved);
    }

    /**
     * Pick a random available address and mark its prefix as in use for the provided IpServer,
     * returns null if there is no available address.
     */
    @Nullable
    public LinkAddress requestDownstreamAddress(final IpServer ipServer, boolean useLastAddress) {
        if (mConfig.shouldEnableWifiP2pDedicatedIp()
                && ipServer.interfaceType() == TETHERING_WIFI_P2P) {
            return new LinkAddress(LEGACY_WIFI_P2P_IFACE_ADDRESS);
        }

        final LinkAddress cachedAddress = mCachedAddress.get(ipServer.interfaceType());
        if (useLastAddress && cachedAddress != null && !isConflict(asIpPrefix(cachedAddress))) {
            return cachedAddress;
        }

        for (IpPrefix prefixRange : mTetheringPrefixes) {
            // Check whether given prefixRange has non-conflict prefixes before go through all of
            // prefixes from prefixRange.
            if (noValidPrefixSpace(prefixRange)) continue;

            final LinkAddress newAddress = chooseDownstreamAddress(ipServer, prefixRange);
            if (newAddress != null) {
                mCachedAddress.put(ipServer.interfaceType(), newAddress);
                return newAddress;
            }
        }

        // No available address.
        return null;
    }

    private boolean noValidPrefixSpace(final IpPrefix prefixRange) {
        for (int i = 0; i < mUpstreamPrefixMap.size(); i++) {
            final List<IpPrefix> list = mUpstreamPrefixMap.valueAt(i);
            for (IpPrefix upstream : list) {
                if (upstream.getPrefixLength() <= prefixRange.getPrefixLength()
                        && upstream.contains(prefixRange.getAddress())) {
                    return true;
                }
            }
        }
        return false;
    }

    private LinkAddress chooseDownstreamAddress(final IpServer ipServer,
            final IpPrefix prefixRange) {
        final int suffixMask = ~prefixLengthToV4NetmaskIntHTH(prefixRange.getPrefixLength());
        final int rootAddress = inet4AddressToIntHTH((Inet4Address) prefixRange.getAddress());
        final int subAddress = getSanitizedSubAddr(suffixMask);
        final int num_of_prefixes = 1 << (PREFIX_LENGTH - prefixRange.getPrefixLength());
        for (int i = 0; i < num_of_prefixes; i++) {
            final int newSubAddress = subAddress + (i << (32 - PREFIX_LENGTH));

            final int newAddress = rootAddress | (newSubAddress & suffixMask);
            final byte[] addrBytes = ByteBuffer.allocate(4).putInt(newAddress).array();
            final InetAddress address;
            try {
                address = InetAddress.getByAddress(addrBytes);
            } catch (UnknownHostException e) {
                throw new IllegalStateException("Invalid address, shouldn't happen.", e);
            }

            if (isConflict(new IpPrefix(address, PREFIX_LENGTH))) continue;

            mDownstreams.add(ipServer);
            return new LinkAddress(address, PREFIX_LENGTH);
        }

        return null;
    }

    private boolean isConflict(final IpPrefix prefix) {
        // Check whether this prefix is conflict with reserved prefixes, already be in use or
        // conflict with any current upstream network.
        return isConflictWithReservedPrefixes(prefix) || isDownstreamPrefixInUse(prefix)
                || isConflictWithUpstream(prefix);
    }

    /** Get random int which could be used to generate random address. */
    @VisibleForTesting
    public int getRandomInt() {
        return (new Random()).nextInt();
    }

    private int getSanitizedSubAddr(final int subAddrMask) {
        final int randomSubAddr = getRandomInt() & subAddrMask;
        if (PREFIX_LENGTH > 30) return randomSubAddr;

        final int candidate = randomSubAddr & 0xff;
        if (candidate == 0 || candidate == 1 || candidate == 255) {
            return (randomSubAddr & 0xfffffffc) + 2;
        }

        return randomSubAddr;
    }

    /** Release downstream record for IpServer. */
    public void releaseDownstream(final IpServer ipServer) {
        mDownstreams.remove(ipServer);
    }

    /** Clear current upstream prefixes records. */
    public void clearUpstreamPrefixes() {
        mUpstreamPrefixMap.clear();
    }

    private boolean isConflictWithReservedPrefixes(final IpPrefix source) {
        for (IpPrefix target : mReservedPrefixes) {
            if (isConflictPrefix(source, target)) return true;
        }

        return false;
    }

    private boolean isConflictWithUpstream(final IpPrefix source) {
        for (int i = 0; i < mUpstreamPrefixMap.size(); i++) {
            final List<IpPrefix> list = mUpstreamPrefixMap.valueAt(i);
            for (IpPrefix target : list) {
                if (isConflictPrefix(source, target)) return true;
            }
        }
        return false;
    }

    private boolean isConflictPrefix(final IpPrefix prefix1, final IpPrefix prefix2) {
        if (prefix2.getPrefixLength() < prefix1.getPrefixLength()) {
            return prefix2.contains(prefix1.getAddress());
        }

        return prefix1.contains(prefix2.getAddress());
    }

    private boolean isDownstreamPrefixInUse(final IpPrefix source) {
        // This class always generates downstream prefixes with the same prefix length, so
        // prefixes cannot be contained in each other. They can only be equal to each other.
        for (IpServer downstream : mDownstreams) {
            final IpPrefix prefix = getDownstreamPrefix(downstream);
            if (source.equals(prefix)) return true;
        }
        return false;
    }

    private IpPrefix getDownstreamPrefix(final IpServer downstream) {
        final LinkAddress address = downstream.getAddress();
        if (address == null) return null;

        return PrefixUtils.asIpPrefix(address);
    }

    void dump(final IndentingPrintWriter pw) {
        pw.println("mUpstreamPrefixMap:");
        pw.increaseIndent();
        for (int i = 0; i < mUpstreamPrefixMap.size(); i++) {
            pw.println(mUpstreamPrefixMap.keyAt(i) + " - " + mUpstreamPrefixMap.valueAt(i));
        }
        pw.decreaseIndent();
        pw.println("mDownstreams:");
        pw.increaseIndent();
        for (IpServer ipServer : mDownstreams) {
            pw.println(ipServer.interfaceType() + " - " + ipServer.getAddress());
        }
        pw.decreaseIndent();
    }
}
