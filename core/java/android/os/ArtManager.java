/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.os;

import static android.annotation.SystemApi.Client.MODULE_LIBRARIES;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.annotation.SystemService;
import android.annotation.TestApi;
import android.content.Context;

/**
 * The ArtManager allows system server to issue various queries and commands
 * regarding the compilation process and artifacts.
 *
 * This class should not be confused with android.content.pm.dex.ArtManager,
 * which is a SystemAPI, while this class is a Service wrapper for the
 * ArtService inside the System Server.
 *
 * @hide
 */
@SystemService(Context.ART_SERVICE)
@SystemApi(client = MODULE_LIBRARIES)
@TestApi
public final class ArtManager {

    private static final String LOG_TAG = "ArtManager";

    private final IArtServiceProxy mService;

    /** @hide */
    public ArtManager(@NonNull IArtServiceProxy service) {
        mService = service;
    }

    /**
     * A proxy for the ArtService test method.
     *
     * @hide
     */
    @TestApi
    public @NonNull String getMsg() {
        try {
            return mService.getMsg();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
