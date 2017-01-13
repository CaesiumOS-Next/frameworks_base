/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.net;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.net.InetAddress;
import java.net.Socket;

/**
 * This class contains methods for managing IPSec sessions.
 *
 * @hide
 */
public final class IpSecTransform {
    private static final String TAG = IpSecTransform.class.getSimpleName();

    public static final int DIRECTION_IN = 0;
    public static final int DIRECTION_OUT = 1;

    public static final int ENCAP_ESP_OVER_IP = 0;
    public static final int ENCAP_ESP_OVER_UDP_IKE = 1;
    public static final int ENCAP_ESP_OVER_UDP_NON_IKE = 2;

    public static final int INVALID_TRANSFORM_ID = -1;

    private IpSecTransform(IpSecConfig config) {
        mConfig = config;
        mTransformId = INVALID_TRANSFORM_ID;
    }

    IpSecConfig getConfig() {
        return mConfig;
    }

    public static IpSecTransform.Builder transportModeTransform(
            Socket boundSocket, InetAddress remoteIp, int remotePort, int spi, int direction) {
        return new IpSecTransform.Builder(boundSocket, remoteIp, remotePort, spi, direction);
    }

    public static IpSecTransform.Builder tunnelModeTransform(
            InetAddress localIp, InetAddress remoteIp, int spi, int direction) {
        return new IpSecTransform.Builder(localIp, remoteIp, spi, direction);
    }

    private final IpSecConfig mConfig;
    private int mTransformId;

    private ConnectivityManager.PacketKeepalive mKeepalive;
    private Integer mKeepaliveStatus = ConnectivityManager.PacketKeepalive.NO_KEEPALIVE;
    private ConnectivityManager.PacketKeepaliveCallback mKeepaliveCallback =
            new ConnectivityManager.PacketKeepaliveCallback() {

                @Override
                public void onStarted() {
                    mKeepaliveStatus = ConnectivityManager.PacketKeepalive.SUCCESS;
                    synchronized (mKeepaliveStatus) {
                        mKeepaliveStatus.notifyAll();
                    }
                }

                @Override
                public void onStopped() {
                    mKeepaliveStatus = ConnectivityManager.PacketKeepalive.NO_KEEPALIVE;
                    synchronized (mKeepaliveStatus) {
                        mKeepaliveStatus.notifyAll();
                    }
                }

                @Override
                public void onError(int error) {
                    mKeepaliveStatus = error;
                    synchronized (mKeepaliveStatus) {
                        mKeepaliveStatus.notifyAll();
                    }
                }
            };

    /** Package */
    void startKeepalive(Context c) {
        if (mConfig.hasProperty(IpSecConfig.Properties.NATT_KEEPALIVE) == false) {
            return;
        }

        ConnectivityManager cm =
                (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (mKeepalive != null) {
            Log.e(TAG, "Keepalive already started for this IpSecTransform.");
            return;
        }

        mKeepalive =
                cm.startNattKeepalive(
                        mConfig.getNetwork(),
                        mConfig.getNattKeepaliveInterval(),
                        mKeepaliveCallback,
                        mConfig.getLocalIp(),
                        mConfig.getEncapLocalPort(),
                        mConfig.getRemoteIp());

        synchronized (mKeepaliveStatus) {
            if (mKeepaliveStatus == ConnectivityManager.PacketKeepalive.NO_KEEPALIVE) {
                try {
                    mKeepaliveStatus.wait(2000);
                } catch (InterruptedException e) {
                }
            }
        }
        if (mKeepaliveStatus != ConnectivityManager.PacketKeepalive.SUCCESS) {
            throw new UnsupportedOperationException("Packet Keepalive cannot be started");
        }
    }

    /** Package */
    void stopKeepalive() {
        if (mKeepalive == null) {
            return;
        }
        mKeepalive.stop();
        synchronized (mKeepaliveStatus) {
            if (mKeepaliveStatus == ConnectivityManager.PacketKeepalive.SUCCESS) {
                try {
                    mKeepaliveStatus.wait(2000);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    /** Package */
    void setTransformId(int transformId) {
        mTransformId = transformId;
    }

    /** Package */
    int getTransformId() {
        return mTransformId;
    }

    public static class Builder {
        private IpSecConfig mConfig;

        private void addSelector(InetAddress remoteIp, int spi, int direction) {
            mConfig.remoteIp = remoteIp;
            mConfig.spi = spi;
            mConfig.direction = direction;
            mConfig.features |= IpSecConfig.Properties.SELECTOR;
        }

        public IpSecTransform.Builder addEncryption(IpSecAlgorithm algo) {
            mConfig.encryptionAlgo = algo;
            mConfig.features |= IpSecConfig.Properties.ENCRYPTION_ALGO;
            return this;
        }

        public IpSecTransform.Builder addAuthentication(IpSecAlgorithm algo) {
            mConfig.authenticationAlgo = algo;
            mConfig.features |= IpSecConfig.Properties.AUTHENTICATION_ALGO;
            return this;
        }

        public IpSecTransform.Builder addNetwork(Network net) {
            mConfig.network = net;
            mConfig.features |= IpSecConfig.Properties.NETWORK;
            return this;
        }

        public IpSecTransform.Builder addIpv4Encapsulation(
                int encapType, int localPort, int remotePort) {
            mConfig.encapType = encapType;
            mConfig.encapLocalPort = localPort;
            mConfig.encapRemotePort = remotePort;
            mConfig.features |= IpSecConfig.Properties.UDP_ENCAP;
            return this;
        }

        /**
         * Provides encapsulation of the traffic on a given socket bound for a specific destination.
         * This option is only applicable when destination IP address is IPv4, the direction is
         * {@link IpSecTransform#DIRECTION_OUT}, and UDP Encapsulation is requested.
         *
         * @param intervalSeconds the maximum number of seconds between keepalive
         *     packets. @SystemApi
         * @hide
         */
        public IpSecTransform.Builder addNattKeepalive(int intervalSeconds) {
            mConfig.nattKeepaliveInterval = intervalSeconds;
            mConfig.features |= IpSecConfig.Properties.NATT_KEEPALIVE;
            return this;
        }

        public IpSecTransform.Builder addSelectorProto(int selectorProto) {
            mConfig.selectorProto = selectorProto;
            return this;
        }

        public IpSecTransform build() {
            if (mConfig.hasProperty(IpSecConfig.Properties.NATT_KEEPALIVE)
                    != mConfig.hasProperty(IpSecConfig.Properties.UDP_ENCAP)) {
                throw new IllegalArgumentException("Natt Keepalive requires UDP Encapsulation");
            }
            return new IpSecTransform(mConfig);
        }

        private Builder(
                Socket boundSocket, InetAddress remoteIp, int remotePort, int spi, int direction) {
            mConfig = new IpSecConfig();
            mConfig.localIp = boundSocket.getInetAddress();
            mConfig.localSocket = ParcelFileDescriptor.fromSocket(boundSocket);
            mConfig.remotePort = remotePort;
            mConfig.features |= IpSecConfig.Properties.MODE_TRANSPORT;

            addSelector(remoteIp, spi, direction);
        }

        private Builder(InetAddress localIp, InetAddress remoteIp, int spi, int direction) {
            mConfig = new IpSecConfig();
            mConfig.localIp = localIp;
            mConfig.features |= IpSecConfig.Properties.MODE_TUNNEL;

            addSelector(remoteIp, spi, direction);
        }
    }
}
