/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.Manifest.permission.NETWORK_STACK;
import static android.net.ConnectivityManager.TETHER_ERROR_NO_ERROR;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.util.SharedLog;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;

import java.io.PrintWriter;
import java.util.StringJoiner;

/**
 * Service used to communicate with the tethering, which is running in a separate module.
 * @hide
 */
public class TetheringManager {
    private static final String TAG = TetheringManager.class.getSimpleName();

    private static TetheringManager sInstance;

    @Nullable
    private ITetheringConnector mConnector;
    private ITetherInternalCallback mCallback;
    private Network mTetherUpstream;
    private TetheringConfigurationParcel mTetheringConfiguration;
    private TetherStatesParcel mTetherStatesParcel;

    private final RemoteCallbackList<ITetheringEventCallback> mTetheringEventCallbacks =
            new RemoteCallbackList<>();
    @GuardedBy("mLog")
    private final SharedLog mLog = new SharedLog(TAG);

    private TetheringManager() { }

    /**
     * Get the TetheringManager singleton instance.
     */
    public static synchronized TetheringManager getInstance() {
        if (sInstance == null) {
            sInstance = new TetheringManager();
        }
        return sInstance;
    }

    private class TetheringConnection implements
            ConnectivityModuleConnector.ModuleServiceCallback {
        @Override
        public void onModuleServiceConnected(@NonNull IBinder service) {
            logi("Tethering service connected");
            registerTetheringService(service);
        }
    }

    private void registerTetheringService(@NonNull IBinder service) {
        final ITetheringConnector connector = ITetheringConnector.Stub.asInterface(service);

        log("Tethering service registered");

        mConnector = connector;
        mCallback = new TetherInternalCallback();
        try {
            mConnector.registerTetherInternalCallback(mCallback);
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }
    }

    private class TetherInternalCallback extends ITetherInternalCallback.Stub {
        @Override
        public void onUpstreamChanged(Network network) {
            mTetherUpstream = network;
            reportUpstreamChanged(network);
        }

        @Override
        public void onConfigurationChanged(TetheringConfigurationParcel config) {
            mTetheringConfiguration = config;
        }

        @Override
        public void onTetherStatesChanged(TetherStatesParcel states) {
            mTetherStatesParcel = states;
        }
    }

    private void reportUpstreamChanged(Network network) {
        final int length = mTetheringEventCallbacks.beginBroadcast();
        try {
            for (int i = 0; i < length; i++) {
                try {
                    mTetheringEventCallbacks.getBroadcastItem(i).onUpstreamChanged(network);
                } catch (RemoteException e) {
                    // Not really very much to do here.
                }
            }
        } finally {
            mTetheringEventCallbacks.finishBroadcast();
        }
    }

    /**
     * Start the tethering service. Should be called only once on device startup.
     *
     * <p>This method will start the tethering service either in the process which shared the same
     * process as network stack, or inside the system server on devices that do not support the
     * tethering module.
     *
     * {@hide}
     */
    public void start() {
        // Using MAINLINE_NETWORK_STACK permission after cutting off the dpendency of system server.
        ConnectivityModuleConnector.getInstance().startModuleService(
                ITetheringConnector.class.getName(), NETWORK_STACK,
                new TetheringConnection());
        log("Tethering service start requested");
    }

    /**
     * Attempt to tether the named interface.  This will setup a dhcp server
     * on the interface, forward and NAT IP v4 packets and forward DNS requests
     * to the best active upstream network interface.  Note that if no upstream
     * IP network interface is available, dhcp will still run and traffic will be
     * allowed between the tethered devices and this device, though upstream net
     * access will of course fail until an upstream network interface becomes
     * active. Note: return value do not have any meaning. It is better to use
     * #getTetherableIfaces() to ensure corresponding interface is available for
     * tethering before calling #tether().
     *
     * TODO: Deprecate this API. The only usages should be in PanService and Wifi P2P which
     * need direct access.
     *
     * @param iface the interface name to tether.
     * @return error do not have any meaning. It always be TETHER_ERROR_NO_ERROR.
     *
     * {@hide}
     */
    public int tether(String iface) {
        try {
            mConnector.tether(iface);
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }
        return TETHER_ERROR_NO_ERROR;
    }

    /**
     * Stop tethering the named interface.
     *
     * @param iface the interface name to tether.
     * @return error do not have any meaning. It always be TETHER_ERROR_NO_ERROR.
     *
     * {@hide}
     */
    public int untether(String iface) {
        try {
            mConnector.untether(iface);
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }
        return TETHER_ERROR_NO_ERROR;
    }

    /**
     *
     * {@hide}
     */
    public int setUsbTethering(boolean enable) {
        try {
            mConnector.setUsbTethering(enable);
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }
        return TETHER_ERROR_NO_ERROR;
    }

    /**
     *
     * {@hide}
     */
    public void startTethering(int type, ResultReceiver receiver, boolean showProvisioningUi) {
        try {
            mConnector.startTethering(type, receiver, showProvisioningUi);
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }
    }

    /**
     *
     * {@hide}
     */
    public void stopTethering(int type) {
        try {
            mConnector.stopTethering(type);
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }
    }

    /**
     * Get the latest value of the tethering entitlement check.
     *
     * Note: Allow privileged apps who have TETHER_PRIVILEGED permission to access. If it turns
     * out some such apps are observed to abuse this API, change to per-UID limits on this API
     * if it's really needed.
     */
    public void getLatestTetheringEntitlementResult(int type, ResultReceiver receiver,
            boolean showEntitlementUi) {
        try {
            mConnector.getLatestTetheringEntitlementResult(type, receiver, showEntitlementUi);
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }
    }

    /**
     * Register tethering event callback.
     *
     * {@hide}
     */
    public void registerTetheringEventCallback(ITetheringEventCallback callback) {
        mTetheringEventCallbacks.register(callback);
    }

    /**
     * Unregister tethering event callback.
     *
     * {@hide}
     */
    public void unregisterTetheringEventCallback(ITetheringEventCallback callback) {
        mTetheringEventCallbacks.unregister(callback);
    }

    /**
     *
     * {@hide}
     */
    public int getLastTetherError(String iface) {
        if (mTetherStatesParcel == null) return TETHER_ERROR_NO_ERROR; //TODO
        int i = 0;
        for (String errored : mTetherStatesParcel.erroredIfaceList) {
            if (iface.equals(errored)) return mTetherStatesParcel.lastErrorList[i];

            i++;
        }
        return TETHER_ERROR_NO_ERROR;
    }

    /**
     *
     * {@hide}
     */
    public String[] getTetherableUsbRegexs() {
        if (mTetheringConfiguration == null) return null; //TODO
        return mTetheringConfiguration.tetherableUsbRegexs;
    }

    /**
     *
     * {@hide}
     */
    public String[] getTetherableWifiRegexs() {
        if (mTetheringConfiguration == null) return null; //TODO
        return mTetheringConfiguration.tetherableWifiRegexs;
    }

    /**
     *
     * {@hide}
     */
    public String[] getTetherableBluetoothRegexs() {
        if (mTetheringConfiguration == null) return null; //TODO
        return mTetheringConfiguration.tetherableBluetoothRegexs;
    }

    /**
     *
     * {@hide}
     */
    public String[] getTetherableIfaces() {
        if (mTetherStatesParcel == null) return null; //TODO
        return mTetherStatesParcel.availableList;
    }

    /**
     *
     * {@hide}
     */
    public String[] getTetheredIfaces() {
        if (mTetherStatesParcel == null) return null; //TODO
        return mTetherStatesParcel.tetheredList;
    }

    /**
     *
     * {@hide}
     */
    public String[] getTetheringErroredIfaces() {
        if (mTetherStatesParcel == null) return null; //TODO
        return mTetherStatesParcel.erroredIfaceList;
    }

    /**
     *
     * {@hide}
     */
    public String[] getTetheredDhcpRanges() {
        if (mTetheringConfiguration == null) return null; //TODO
        return mTetheringConfiguration.legacyDhcpRanges;
    }

    /**
     *
     * {@hide}
     */
    public boolean isTetheringSupported() {
        // TODO
        return true;
    }

    /**
     * Log a message in the local log.
     */
    private void log(@NonNull String message) {
        synchronized (mLog) {
            mLog.log(message);
        }
    }

    /**
     * Log a condition that should never happen.
     */
    private void logWtf(@NonNull String message, @Nullable Throwable e) {
        Slog.wtf(TAG, message);
        synchronized (mLog) {
            mLog.e(message, e);
        }
    }

    /**
     * Log a ERROR level message in the local and system logs.
     */
    private void loge(@NonNull String message, @Nullable Throwable e) {
        synchronized (mLog) {
            mLog.e(message, e);
        }
    }

    /**
     * Log a INFO level message in the local and system logs.
     */
    private void logi(@NonNull String message) {
        synchronized (mLog) {
            mLog.i(message);
        }
    }

    /**
     * Dump TetheringManager logs to the specified {@link PrintWriter}.
     */
    public void dump(PrintWriter pw) {
        // dump is thread-safe on SharedLog
        mLog.dump(null, pw, null);

        pw.print("subId: ");
        pw.println(mTetheringConfiguration.subId);

        dumpStringArray(pw, "tetherableUsbRegexs",
                mTetheringConfiguration.tetherableUsbRegexs);
        dumpStringArray(pw, "tetherableWifiRegexs",
                mTetheringConfiguration.tetherableWifiRegexs);
        dumpStringArray(pw, "tetherableBluetoothRegexs",
                mTetheringConfiguration.tetherableBluetoothRegexs);

        pw.print("isDunRequired: ");
        pw.println(mTetheringConfiguration.isDunRequired);

        pw.print("chooseUpstreamAutomatically: ");
        pw.println(mTetheringConfiguration.chooseUpstreamAutomatically);

        dumpStringArray(pw, "legacyDhcpRanges", mTetheringConfiguration.legacyDhcpRanges);
        dumpStringArray(pw, "defaultIPv4DNS", mTetheringConfiguration.defaultIPv4DNS);

        dumpStringArray(pw, "provisioningApp", mTetheringConfiguration.provisioningApp);
        pw.print("provisioningAppNoUi: ");
        pw.println(mTetheringConfiguration.provisioningAppNoUi);

        pw.print("enableLegacyDhcpServer: ");
        pw.println(mTetheringConfiguration.enableLegacyDhcpServer);

        pw.println();
    }

    private static void dumpStringArray(PrintWriter pw, String label, String[] values) {
        pw.print(label);
        pw.print(": ");

        if (values != null) {
            final StringJoiner sj = new StringJoiner(", ", "[", "]");
            for (String value : values) sj.add(value);

            pw.print(sj.toString());
        } else {
            pw.print("null");
        }

        pw.println();
    }
}
