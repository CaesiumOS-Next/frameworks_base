/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.server;


import android.content.Context;
import android.net.INetdUnsolicitedEventCallback;
import android.net.INetdUnsolicitedEventListener;
import android.os.RemoteException;
import android.util.Log;

/**
 * Implementation of the INetdUnsolicitedEventListener interface.
 */
public class NetdUnsolicitedEventListenerService extends INetdUnsolicitedEventListener.Stub {

    public static final String SERVICE_NAME = "netd_ulistener";

    private static final String TAG = NetdUnsolicitedEventListenerService.class.getSimpleName();
    private static final boolean DBG = false;

    /**
     * Binder context for this service
     */
    private final Context mContext;

    /**
     * There are 1 possible callbacks currently.
     *
     * mNetdEventCallbackList[CALLBACK_CALLER_NETWORKMANAGEMENT_SERVICE]
     * Callback registered/unregistered by NetworkManagementService.
     */

    private static final int[] ALLOWED_CB_REGISTRAR_IDENTITIES = {
        INetdUnsolicitedEventCallback.CALLBACK_CALLER_NETWORKMANAGEMENT_SERVICE
    };

    private final INetdUnsolicitedEventCallback[] mNetdEventCallbackList =
            new INetdUnsolicitedEventCallback[ALLOWED_CB_REGISTRAR_IDENTITIES.length];

    public synchronized boolean setNetdUnsolicitedEventCallback(int callerType,
            INetdUnsolicitedEventCallback callback) {
        final int callerIndex = getCallerIndexFromType(callerType);
        if (!isValidCallerIndex(callerIndex)) {
            Log.e(TAG, "Invalid caller type: " + callerType);
            return false;
        }
        mNetdEventCallbackList[callerIndex] = callback;
        return true;
    }

    public synchronized boolean removeNetdEventCallback(int callerType) {
        final int callerIndex = getCallerIndexFromType(callerType);
        if (!isValidCallerIndex(callerIndex)) {
            Log.e(TAG, "Invalid caller type: " + callerType);
            return false;
        }
        mNetdEventCallbackList[callerIndex] = null;
        return true;
    }

    private boolean isValidCallerIndex(int callerIndex) {
        return ((callerIndex < 0 || callerIndex > mNetdEventCallbackList.length - 1)
                ? false : true);
    }
    private static int getCallerIndexFromType(int callerType) {
        for (int i = 0; i < ALLOWED_CB_REGISTRAR_IDENTITIES.length; i++) {
            if (callerType == ALLOWED_CB_REGISTRAR_IDENTITIES[i]) {
                return i;
            }
        }
        return -1;
    }

    public NetdUnsolicitedEventListenerService(Context context) {
        mContext = context;
    }

    @Override
    public synchronized void onInterfaceClassActivityChangedEvent(boolean isActive,
            int label, long timestamp, int uid)
            throws RemoteException {
        for (INetdUnsolicitedEventCallback callback : mNetdEventCallbackList) {
            if (callback != null) {
                callback.onInterfaceClassActivityChangedEvent(isActive, label, timestamp, uid);
            }
        }
    }

    @Override
    public synchronized void onQuotaLimitReachedEvent(String alertName, String ifName)
            throws RemoteException {
        for (INetdUnsolicitedEventCallback callback : mNetdEventCallbackList) {
            if (callback != null) {
                callback.onQuotaLimitReachedEvent(alertName, ifName);
            }
        }
    }

    @Override
    public synchronized void onInterfaceDnsServersAddedEvent(String ifName,
            long lifetime, String[] servers)
            throws RemoteException {
        for (INetdUnsolicitedEventCallback callback : mNetdEventCallbackList) {
            if (callback != null) {
                callback.onInterfaceDnsServersAddedEvent(ifName, lifetime, servers);
            }
        }
    }

    @Override
    public synchronized void onInterfaceAddressChangedEvent(boolean updated,
            String addr, String ifName, int flags, int scope)
            throws RemoteException {
        for (INetdUnsolicitedEventCallback callback : mNetdEventCallbackList) {
            if (callback != null) {
                callback.onInterfaceAddressChangedEvent(updated, addr, ifName, flags, scope);
            }
        }
    }

    @Override
    public synchronized void onInterfaceAddedEvent(String ifName)
            throws RemoteException {
        for (INetdUnsolicitedEventCallback callback : mNetdEventCallbackList) {
            if (callback != null) {
                callback.onInterfaceAddedEvent(ifName);
            }
        }
    }

    @Override
    public synchronized void onInterfaceRemovedEvent(String ifName)
            throws RemoteException {
        for (INetdUnsolicitedEventCallback callback : mNetdEventCallbackList) {
            if (callback != null) {
                callback.onInterfaceRemovedEvent(ifName);
            }
        }
    }

    @Override
    public synchronized void onInterfaceChangedEvent(String ifName, boolean status)
            throws RemoteException {
        for (INetdUnsolicitedEventCallback callback : mNetdEventCallbackList) {
            if (callback != null) {
                callback.onInterfaceChangedEvent(ifName, status);
            }
        }
    }

    @Override
    public synchronized void onInterfaceLinkStatusChangedEvent(String ifName, boolean status)
            throws RemoteException {
        for (INetdUnsolicitedEventCallback callback : mNetdEventCallbackList) {
            if (callback != null) {
                callback.onInterfaceLinkStatusChangedEvent(ifName, status);
            }
        }
    }

    @Override
    public synchronized void onRouteChangedEvent(boolean updated,
            String route, String gateway, String ifName)
            throws RemoteException {
        for (INetdUnsolicitedEventCallback callback : mNetdEventCallbackList) {
            if (callback != null) {
                callback.onRouteChangedEvent(updated, route, gateway, ifName);
            }
        }
    }

    @Override
    public synchronized void onStrictCleartextDetectedEvent(int uid, String firstPacket)
            throws RemoteException {
        for (INetdUnsolicitedEventCallback callback : mNetdEventCallbackList) {
            if (callback != null) {
                callback.onStrictCleartextDetectedEvent(uid, firstPacket);
            }
        }
    }
}
