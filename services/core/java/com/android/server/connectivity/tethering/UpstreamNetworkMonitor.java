/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.server.connectivity.tethering;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.NetworkState;
import android.os.Handler;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.StateMachine;

import java.util.HashMap;


/**
 * A class to centralize all the network and link properties information
 * pertaining to the current and any potential upstream network.
 *
 * Calling #start() registers two callbacks: one to track the system default
 * network and a second to specifically observe TYPE_MOBILE_DUN networks.
 *
 * The methods and data members of this class are only to be accessed and
 * modified from the tethering master state machine thread. Any other
 * access semantics would necessitate the addition of locking.
 *
 * Methods of this class can only safely be called from the same thread as
 * the target StateMachine that receives this class's notifications.
 *
 * TODO: Investigate whether more "upstream-specific" logic/functionality
 * could/should be moved here.
 *
 * @hide
 */
public class UpstreamNetworkMonitor {
    public static final int EVENT_ON_AVAILABLE      = 1;
    public static final int EVENT_ON_CAPABILITIES   = 2;
    public static final int EVENT_ON_LINKPROPERTIES = 3;
    public static final int EVENT_ON_LOST           = 4;

    private final static String TAG = UpstreamNetworkMonitor.class.getSimpleName();
    private final static boolean VDBG = false;

    private final Context mContext;
    private final StateMachine mTargetSM;
    private final Handler mHandler;
    private final int mWhat;
    private final HashMap<Network, NetworkState> mNetworkMap;
    private ConnectivityManager mCM;
    private NetworkCallback mDefaultNetworkCallback;
    private NetworkCallback mDunTetheringCallback;

    public UpstreamNetworkMonitor(Context context, StateMachine target, int what) {
        mContext = context;
        mTargetSM = target;
        // So as to operate on the same thread as the target state machine.
        mHandler = mTargetSM.getHandler();
        mWhat = what;
        mNetworkMap = new HashMap<>();
    }

    public void start() {
        stop();

        mDefaultNetworkCallback = new UpstreamNetworkCallback();
        cm().registerDefaultNetworkCallback(mDefaultNetworkCallback);

        final NetworkRequest dunTetheringRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_DUN)
                .build();
        mDunTetheringCallback = new UpstreamNetworkCallback();
        cm().registerNetworkCallback(dunTetheringRequest, mDunTetheringCallback);
    }

    public void stop() {
        if (mDefaultNetworkCallback != null) {
            cm().unregisterNetworkCallback(mDefaultNetworkCallback);
            mDefaultNetworkCallback = null;
        }

        if (mDunTetheringCallback != null) {
            cm().unregisterNetworkCallback(mDunTetheringCallback);
            mDunTetheringCallback = null;
        }

        mNetworkMap.clear();
    }

    public NetworkState lookup(Network network) {
        return (network != null) ? mNetworkMap.get(network) : null;
    }

    private void handleAvailable(Network network) {
        if (VDBG) {
            Log.d(TAG, "AVAILABLE for " + network);
        }
        if (!mNetworkMap.containsKey(network)) {
            mNetworkMap.put(network,
                    new NetworkState(null, null, null, network, null, null));
        }

        if (mDefaultNetworkCallback != null) {
            cm().requestNetworkCapabilities(mDefaultNetworkCallback);
            cm().requestLinkProperties(mDefaultNetworkCallback);
        }

        // Requesting updates for mDunTetheringCallback is not
        // necessary. Because it's a listen, it will already have
        // heard all NetworkCapabilities and LinkProperties updates
        // since UpstreamNetworkMonitor was started. Because we
        // start UpstreamNetworkMonitor before chooseUpstreamType()
        // is ever invoked (it can register a DUN request) this is
        // mostly safe. However, if a DUN network is already up for
        // some reason (unlikely, because DUN is restricted and,
        // unless the DUN network is shared with another APN, only
        // the system can request it and this is the only part of
        // the system that requests it) we won't know its
        // LinkProperties or NetworkCapabilities.

        notifyTarget(EVENT_ON_AVAILABLE, network);
    }

    private void handleNetworkCapabilites(Network network, NetworkCapabilities newNc) {
        final NetworkState prev = mNetworkMap.get(network);
        if (prev == null) {
            // Ignore updates for networks for which we have not yet
            // received onAvailable() - which should never happen -
            // or for which we have already received onLost().
            return;
        }

        if (VDBG) {
            Log.d(TAG, String.format("NEWCAP for %s: %s", network, newNc));
        }

        mNetworkMap.put(network, new NetworkState(
                null, prev.linkProperties, newNc, network, null, null));

        // TODO: send mNetworkMap.get(network);
        notifyTarget(EVENT_ON_CAPABILITIES, network);
    }

    private void handleLinkProperties(Network network, LinkProperties newLp) {
        final NetworkState prev = mNetworkMap.get(network);
        if (prev == null) {
            // Ignore updates for networks for which we have not yet
            // received onAvailable() - which should never happen -
            // or for which we have already received onLost().
            return;
        }
        if (VDBG) {
            Log.d(TAG, String.format("NEWLP for %s: %s", network, newLp));
        }

        mNetworkMap.put(network, new NetworkState(
                null, newLp, prev.networkCapabilities, network, null, null));

        notifyTarget(EVENT_ON_LINKPROPERTIES, network);
    }

    private void handleLost(Network network) {
        if (VDBG) {
            Log.d(TAG, "LOST for " + network);
        }
        notifyTarget(EVENT_ON_LOST, mNetworkMap.remove(network));
    }

    private void notifyTarget(int which, Network network) {
        notifyTarget(which, mNetworkMap.get(network));
    }

    private void notifyTarget(int which, NetworkState netState) {
        mTargetSM.sendMessage(mWhat, which, 0, netState);
    }

    private ConnectivityManager cm() {
        if (mCM == null) {
            mCM = mContext.getSystemService(ConnectivityManager.class);
        }
        return mCM;
    }

    /**
     * A NetworkCallback class that relays information of interest to the
     * handler for subsequent processing.
     */
    private class UpstreamNetworkCallback extends NetworkCallback {
        @Override
        public void onAvailable(Network network) {
            mHandler.post(() -> handleAvailable(network));
        }

        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities newNc) {
            mHandler.post(() -> handleNetworkCapabilites(network, newNc));
        }

        @Override
        public void onLinkPropertiesChanged(Network network, LinkProperties newLp) {
            mHandler.post(() -> handleLinkProperties(network, newLp));
        }

        @Override
        public void onLost(Network network) {
            mHandler.post(() -> handleLost(network));
        }
    }
}
