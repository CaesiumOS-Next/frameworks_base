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

package com.android.internal.compat;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

/**
 * A wrapper class for interactions with {@link Context.PLATFORM_COMPAT_SERVICE} through {@link
 * IPlatformCompat} API and a binder call. Checks for errors, and returns provided default values if
 * an error occurs.
 *
 * @hide
 */
public class Compatibility {
    private static final String TAG = "Compatibility";

    /**
     * A wrapper for {@link IPlatformCompat.isChangeEnabled(long, ApplicationInfo)}, that returns
     * defaultValue if there was an error getting the service or communicating with it.
     *
     * @param changeId     The ID of the compatibility change in question.
     * @param appInfo      Representing the app in question.
     * @param defaultValue The default fallback response in case of an error.
     * @return {@code true} if the change is enabled for the provided app, or if there was an error
     * and defaultValue is true.
     */
    public static boolean isChangeEnabledWithDefault(long changeId, ApplicationInfo appInfo,
            boolean defaultValue) {
        IBinder b = ServiceManager.getService(Context.PLATFORM_COMPAT_SERVICE);
        IPlatformCompat platformCompat = IPlatformCompat.Stub.asInterface(b);
        try {
            return platformCompat.isChangeEnabled(changeId, appInfo);
        } catch (RemoteException | NullPointerException e) {
            Log.e(TAG, "Failed to get a response from PLATFORM_COMPAT_SERVICE: " + e);
        }
        // Fall back to previous behaviour.
        return defaultValue;
    }

    /**
     * A wrapper for {@link IPlatformCompat.isChangeEnabledByPackageName(long, String)}, that
     * returns defaultValue if there was an error getting the service or communicating with it.
     *
     * @param changeId     The ID of the compatibility change in question.
     * @param packageName  Package name of the app in question.
     * @param defaultValue The default fallback response in case of an error.
     * @return {@code true} if the change is enabled for the provided app, or if there was an error
     * and defaultValue is true.
     */
    public static boolean isChangeEnabledWithDefault(long changeId, String packageName,
            boolean defaultValue) {
        IBinder b = ServiceManager.getService(Context.PLATFORM_COMPAT_SERVICE);
        IPlatformCompat platformCompat = IPlatformCompat.Stub.asInterface(b);
        try {
            return platformCompat.isChangeEnabledByPackageName(changeId, packageName);
        } catch (RemoteException | NullPointerException e) {
            Log.e(TAG, "Failed to get a response from PLATFORM_COMPAT_SERVICE: " + e);
        }
        // Fall back to previous behaviour.
        return defaultValue;
    }


    /**
     * A wrapper for {@link IPlatformCompat.isChangeEnabledByUid(long, int)}, that returns
     * defaultValue if there was an error getting the service or communicating with it.
     *
     * @param changeId     The ID of the compatibility change in question.
     * @param uid          The UID of the app in question.
     * @param defaultValue The default fallback response in case of an error.
     * @return {@code true} if the change is enabled for the provided app, or if there was an error
     * and defaultValue is true.
     */
    public static boolean isChangeEnabledWithDefault(long changeId, int uid, boolean defaultValue) {
        IBinder b = ServiceManager.getService(Context.PLATFORM_COMPAT_SERVICE);
        IPlatformCompat platformCompat = IPlatformCompat.Stub.asInterface(b);
        try {
            return platformCompat.isChangeEnabledByUid(changeId, uid);
        } catch (RemoteException | NullPointerException e) {
            Log.e(TAG, "Failed to get a response from PLATFORM_COMPAT_SERVICE: " + e);
        }
        // Fall back to previous behaviour.
        return defaultValue;
    }
}
