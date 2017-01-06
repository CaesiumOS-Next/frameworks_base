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

package android.app.timezone;

import android.annotation.SystemApi;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import java.io.File;
import java.io.IOException;

/**
 * The interface through which an application interacts with the Android time zone rules manager to
 * handle rules updates.
 * Applications instantiate it using the constructor and issue calls through that instance.
 * @hide
 */
@SystemApi
public class TimeZoneRulesManager {
    private static final String TAG = "TimeZoneRulesManager";

    /**
     * Indicates that an operation succeeded.
     *
     * @hide
     */
    @SystemApi
    public static final int SUCCESS = 0;

    /**
     * Indicates that an install/uninstall cannot be initiated because there is one already in
     * progress.
     *
     * @hide
     */
    @SystemApi
    public static final int ERROR_OPERATION_IN_PROGRESS = 1;

    /**
     * Indicates a general failure associated with install/uninstallation operations.
     *
     * @hide
     */
    @SystemApi
    public static final int ERROR_GENERAL_FAILURE = 2;

    private Context mContext;
    private static ITimeZoneRulesManager sService;

    private static void checkServiceBinder() {
        if (sService == null) {
            sService = ITimeZoneRulesManager.Stub.asInterface(
                    ServiceManager.getService(Context.TIME_ZONE_RULES_MANAGER_SERVICE));
        }
    }

    /** @hide */
    public TimeZoneRulesManager(Context context) {
        mContext = context;
    }

    public RulesState getRulesState() throws RemoteException {
        checkServiceBinder();
        try {
            return sService.getRulesState();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public int requestInstall(File bundleFile, Observer observer) throws IOException, RemoteException {
        checkServiceBinder();
        // Convert the bundleFile to a ParcelFileHandle
        ParcelFileDescriptor bundleFileDescriptor =
                ParcelFileDescriptor.open(bundleFile, ParcelFileDescriptor.MODE_READ_ONLY);

        IObserver iObserver = new ObserverWrapper(mContext, observer);
        try {
            return sService.requestInstall(bundleFileDescriptor, iObserver);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        } finally {
            // Tidy up bundleFileDescriptor. TODO Here or on callback or not needed?
            bundleFileDescriptor.close();
        }
    }

    public int requestUninstall(Observer observer) throws RemoteException {
        checkServiceBinder();
        IObserver iObserver = new ObserverWrapper(mContext, observer);
        try {
            return sService.requestUninstall(iObserver);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /*
     * We wrap incoming binder calls with a private class implementation that
     * redirects them into main-thread actions.  This serializes the backup
     * progress callbacks nicely within the usual main-thread lifecycle pattern.
     */
    private class ObserverWrapper extends IObserver.Stub {
        final Handler mHandler;
        final Observer mObserver;

        static final int MSG_FINISHED = 1;

        ObserverWrapper(Context context, Observer observer) {
            mHandler = new Handler(context.getMainLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case MSG_FINISHED:
                            mObserver.onFinished(msg.arg1);
                            break;
                        default:
                            Log.w(TAG, "Unknown message: " + msg);
                            break;
                    }
                }
            };
            mObserver = observer;
        }

        // Binder calls into this object just enqueue on the main-thread handler
        @Override
        public void onFinished(int status) {
            mHandler.sendMessage(
                mHandler.obtainMessage(MSG_FINISHED, status));
        }
    }
}
