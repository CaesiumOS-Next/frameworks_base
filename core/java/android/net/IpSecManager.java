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
import android.os.INetworkManagementService;
import android.os.RemoteException;
import android.system.ErrnoException;
import android.util.Log;
import java.io.IOException;

/** This class contains methods for managing IPSec sessions. */
public class IpSecManager {
    private static final String TAG = "IpSecManager";

    /**
     * Indicates that the combination of source IP, destination IP, and SPI was non-unique for a
     * given request. If encountered, selection of a new SPI is recommended.
     */
    public class TransformCollisionException extends Exception {}

    private final Context mContext;
    private final INetworkManagementService mService;

    /**
     * Request creation of a security association and security policy to perform transport-mode
     * IPsec encapsulation of the traffic on a given socket bound for a specific destination. The
     * socket must be bound to a port prior to requesting an IPsec transform.
     *
     * @param transform an {@link IpSecTransform}, which is either a tunnel or transport mode
     *     specifider for an IPSec session.
     * @throws TransformCollisionException if the SPI collides with an existing transform
     *     (unlikely).
     */
    public void addTransform(IpSecTransform transform)
            throws TransformCollisionException, IOException {
        int transformId;
        synchronized (transform) {
            try {
                transformId = mService.addTransform(transform.getConfig(), new Binder());
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }

            if (transformId < 0) {
                throw new ErrnoException("addTransform", -transformId).rethrowAsIOException();
            }

            transform.startKeepalive(mContext); // Will silently fail if not required
            transform.setTransformId(transformId);
            Log.d(TAG, "Added Transform with Id " + transformId);
        }
    }

    /**
     * Remove a specified transform that was previously added.
     *
     * <p>This method will silently succeed if the specified transform has already been removed.
     *
     * @param transform a transport mode IpSecTransform object
     */
    public void removeTransform(IpSecTransform transform) {
        final int transformId;
        synchronized (transform) {
            transformId = transform.getTransformId();
            Log.d(TAG, "Removing Transform with Id " + transformId);

            // Always safe to attempt cleanup
            if (transformId == IpSecTransform.INVALID_TRANSFORM_ID) {
                return;
            }
            try {
                transform.stopKeepalive();
                mService.removeTransform(transformId);
            } catch (RemoteException e) {
                transform.setTransformId(transformId);
                throw e.rethrowFromSystemServer();
            } finally {
                transform.setTransformId(IpSecTransform.INVALID_TRANSFORM_ID);
            }
        }
    }

    /**
     * Retrieve an instance of an IpSecManager within you application context
     *
     * @param context the application context for this manager
     * @hide
     */
    public IpSecManager(Context context, INetworkManagementService service) {
        mContext = checkNotNull(context, "missing context");
        mService = checkNotNull(service, "missing service");
    }
}
