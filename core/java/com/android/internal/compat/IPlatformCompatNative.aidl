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


/**
 * Platform private API for talking with the PlatformCompat service.
 *
 * <p> Should be used for gating and logging from non-app processes.
 * For app processes please use android.compat.Compatibility API.
 *
 * {@hide}
 */
interface IPlatformCompat {

    /**
     * Reports that a compatibility change is affecting an app process now.
     *
     * <p>Note: for changes that are gated using {@link #isChangeEnabled(long, String)},
     * you do not need to call this API directly. The change will be reported for you.
     *
     * @param changeId    The ID of the compatibility change taking effect.
     * @param packageName The package name of the app in question.
     */
     void reportChangeByPackageName(long changeId, @utf8InCpp String packageName);

    /**
     * Reports that a compatibility change is affecting an app process now.
     *
     * <p>Note: for changes that are gated using {@link #isChangeEnabled(long, int)},
     * you do not need to call this API directly. The change will be reported for you.
     *
     * @param changeId The ID of the compatibility change taking effect.
     * @param uid      The UID of the app in question.
     */
    void reportChangeByUid(long changeId, int uid);

    void startSession(@utf8InCpp String sessionId, int type, in ParcelFileDescriptor deviceFd,
         @utf8InCpp String upperPath, @utf8InCpp String lowerPath, in RemoteCallback callback);
    void endSession(@utf8InCpp String sessionId, in RemoteCallback callback);
}