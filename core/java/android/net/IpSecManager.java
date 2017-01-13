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

import static com.android.internal.util.Preconditions.checkNotNull;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.INetworkManagementService;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.system.ErrnoException;
import android.util.Log;

/**
 * This class contains methods for managing IPSec sessions.
 *
 * @hide
 */
public class IpSecManager {
    private static final String TAG = IpSecManager.class.getSimpleName();

    /** Indicates that the combination of source IP, destination IP, and SPI was non-unique */
    public class TransformCollisionException extends Exception {}

    /**
     * Request creation of a security association and security policy to perform transport-mode
     * IPsec encapsulation of the traffic on a given socket bound for a specific destination. The
     * socket must be bound to a port prior to requesting an IPsec transform.
     *
     * @param transform a transport mode IpSecTransform object
     */
    public void addTransform(IpSecTransform transform)
            throws TransformCollisionException, ErrnoException {
        int transformId;
        try {
            transformId = mService.addTransform(transform.getConfig(), new Binder());
            if (transformId >= 0) {
                transform.startKeepalive(mContext); // Will silently fail if not required
                transform.setTransformId(transformId);
            } else {
                throw new ErrnoException("addTransform", -transformId);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Binder call failed");
        }
    }

    /**
     * Remove a transform that was created with the provided IpSecTransform argument
     *
     * @param transform a transport mode IpSecTransform object
     */
    public void removeTransform(IpSecTransform transform) {
        int transformId;
        synchronized (transform) {
            transformId = transform.getTransformId();
            Log.d(TAG, "Transform Id for this transform is " + transformId);
            transform.setTransformId(IpSecTransform.INVALID_TRANSFORM_ID);
        }

        // Always safe to attempt cleanup
        if (transformId == IpSecTransform.INVALID_TRANSFORM_ID) {
            return;
        }
        try {
            transform.stopKeepalive();
            mService.removeTransform(transformId);
        } catch (RemoteException e) {
            synchronized (transform) {
                transform.setTransformId(transformId);
            }
        }
    }

    private final Context mContext;
    private final INetworkManagementService mService;

    public IpSecManager(Context context) {
        IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
        checkNotNull(b, "missing NetworkManagementService");
        mContext = checkNotNull(context, "missing context");
        mService = INetworkManagementService.Stub.asInterface(b);
    }
}
