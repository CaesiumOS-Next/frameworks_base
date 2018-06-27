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

import java.io.IOException;
import java.net.InetAddress;

/**
 * This class contains methods for building and managing IPsec-secured tunnels.
 *
 * @hide
 */
public final class IpSecSecureTunnel {
    private static final String TAG = "IpSecSecureTunnel";

    public final IpSecTransform mTransformIn;
    public final IpSecTransform mTransformOut;
    public final IpSecManager.UdpEncapsulationSocket mEncapSocket;
    public final IpSecManager.IpSecTunnelInterface mTunnelIntf;

    private IpSecSecureTunnel(
            IpSecTransform transformIn,
            IpSecTransform transformOut,
            IpSecManager.UdpEncapsulationSocket encapSocket,
            IpSecManager.IpSecTunnelInterface tunnelInterface) {
        mTransformIn = transformIn;
        mTransformOut = transformOut;
        mEncapSocket = encapSocket;
        mTunnelIntf = tunnelInterface;
    }

    public void addInnerAddress(InetAddress address, int prefixLen) throws IOException {
        mTunnelIntf.addAddress(address, prefixLen);
    }

    public void removeInnerAddress(InetAddress address, int prefixLen) throws IOException {
        mTunnelIntf.removeAddress(address, prefixLen);
    }

    public static class Builder {
        private final IpSecManager mISM;
        private final InetAddress mSourceAddr;
        private final InetAddress mDestAddr;
        private final Network mUnderlyingNetwork;
        private final IpSecTransform.Builder mInTransformBuilder;
        private final IpSecTransform.Builder mOutTransformBuilder;

        private boolean mIsEncapEnabled = false;

        public Builder(
                Context context,
                InetAddress sourceAddr,
                InetAddress destAddr,
                Network underlyingNetwork) {
            mISM = (IpSecManager) context.getSystemService(Context.IPSEC_SERVICE);
            mSourceAddr = sourceAddr;
            mDestAddr = destAddr;
            mUnderlyingNetwork = underlyingNetwork;
            mInTransformBuilder = new IpSecTransform.Builder(context);
            mOutTransformBuilder = new IpSecTransform.Builder(context);

            if (!context.getOpPackageName().equals("android.net.cts")) {
                throw new SecurityException("Test methods for CTS only");
            }
        }

        private IpSecTransform.Builder getBuilderForDirection(int direction) {
            switch (direction) {
                case IpSecManager.DIRECTION_IN:
                    return mInTransformBuilder;
                case IpSecManager.DIRECTION_OUT:
                    return mOutTransformBuilder;
                default:
                    throw new IllegalArgumentException("Invalid direction");
            }
        }

        public Builder setAuthentication(int direction, IpSecAlgorithm auth) {
            getBuilderForDirection(direction).setAuthentication(auth);
            return this;
        }

        public Builder setEncryption(int direction, IpSecAlgorithm crypt) {
            getBuilderForDirection(direction).setEncryption(crypt);
            return this;
        }

        public Builder setAuthenticatedEncryption(int direction, IpSecAlgorithm aead) {
            getBuilderForDirection(direction).setAuthenticatedEncryption(aead);
            return this;
        }

        public Builder useEncap(boolean enabled) {
            mIsEncapEnabled = enabled;
            return this;
        }

        public IpSecSecureTunnel build()
                throws IOException, IpSecManager.SpiUnavailableException,
                        IpSecManager.ResourceUnavailableException {
            IpSecManager.SecurityParameterIndex inSpi =
                    mISM.allocateSecurityParameterIndex(mSourceAddr);
            IpSecManager.SecurityParameterIndex outSpi =
                    mISM.allocateSecurityParameterIndex(mDestAddr);

            IpSecManager.UdpEncapsulationSocket encapSocket = null;
            if (mIsEncapEnabled) {
                encapSocket = mISM.openUdpEncapsulationSocket();
                mInTransformBuilder.setIpv4Encapsulation(encapSocket, encapSocket.getPort());
                mOutTransformBuilder.setIpv4Encapsulation(encapSocket, encapSocket.getPort());
            }

            IpSecManager.IpSecTunnelInterface tunnelIntf =
                    mISM.createIpSecTunnelInterface(mSourceAddr, mDestAddr, mUnderlyingNetwork);

            IpSecTransform transformIn =
                    mInTransformBuilder.buildTunnelModeTransform(mDestAddr, inSpi);
            IpSecTransform transformOut =
                    mOutTransformBuilder.buildTunnelModeTransform(mSourceAddr, outSpi);

            mISM.applyTunnelModeTransform(tunnelIntf, IpSecManager.DIRECTION_IN, transformIn);
            mISM.applyTunnelModeTransform(tunnelIntf, IpSecManager.DIRECTION_OUT, transformOut);

            return new IpSecSecureTunnel(transformIn, transformOut, encapSocket, tunnelIntf);
        }
    }
}
