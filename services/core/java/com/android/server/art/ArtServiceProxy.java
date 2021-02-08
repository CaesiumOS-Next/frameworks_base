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

package com.android.server.art;

import android.content.Context;
import android.os.Handler;
import android.os.IArtServiceProxy;
import android.os.Looper;
import android.os.Message;

import com.android.internal.os.BackgroundThread;
import com.android.server.SystemService;

/**
 * This class provides a proxy to the ART Service located in the ART Mainline module.
 */
public class ArtServiceProxy extends IArtServiceProxy.Stub {
    static final String LOG_TAG = "ArtServiceProxy";

    private final ArtHandler mHandler;
    private final ArtService mArtService = new ArtService();

    private ArtServiceProxy() {
        this(BackgroundThread.get().getLooper());
    }

    ArtServiceProxy(Looper looper) {
        mHandler = new ArtHandler(looper);
    }

    class ArtHandler extends Handler {

        ArtHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
        }
    }

    /**
     * SystemService lifecycle for ArtServiceProxy.
     */
    public static class Lifecycle extends SystemService {
        private ArtServiceProxy mService;

        public Lifecycle(Context context) {
            super(context);
        }

        @Override
        public void onStart() {
            mService = new ArtServiceProxy();
            publishBinderService(Context.ART_SERVICE, mService);
        }
    }

    /*
     * Interface Methods
     */

    @Override
    public String getMsg() {
        return mArtService.getMsg();
    }
}
