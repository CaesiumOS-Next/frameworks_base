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

package android.service.tracing;

import static android.annotation.SystemApi.Client.PRIVILEGED_APPS;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.tracing.TraceReportParams;
import android.util.Log;

import java.io.IOException;
import java.util.UUID;

/**
 * Helper class for use by services in (privileged) apps which want to report system traces.
 * <p>
 * Such services should call bindWithCallback to receive an IBinder class which can be returned
 * in OnBind.
 * </p>
 * <pre>
 *    public class SampleService extends Service implements TraceReportServiceHelper.Callback {
 *        private IBinder mBinder = null;
 *        public void onReportTrace(TraceParams args) {
 *            // --- Implementation goes here ---
 *        }
 *
 *        public IBinder onBind(@NonNull Intent intent){
 *            if (mBinder == null) {
 *                mBinder = TraceReportServiceHelper.bindWithCallback(this);
 *            }
 *            return mBinder;
 *        }
 *    }
 * </pre>
 * <p>
 * The service declaration in the application manifest must specify
 * BIND_TRACE_REPORT_SERVICE in the permission attribute.
 * </p>
 * <pre>
 *   &lt;application>
 *        &lt;service android:name=".SampleService"
 *               android:permission="android.permission.BIND_TRACE_REPORT_SERVICE">
 *       &lt;/service>
 *   &lt;/application>
 * </pre>
 *
 * Moreover, the package containing this service must hold the DUMP and PACKAGE_USAGE_STATS
 * permissions.
 *
 * @hide
 */
@SystemApi(client = PRIVILEGED_APPS)
public class TraceReportServiceHelper {
    private static final String TAG = "TraceReportServiceHelp";

    /**
     * Public to allow this to be used by TracingServiceProxy in system_server.
     *
     * @hide
     */
    public static final int MSG_REPORT_TRACE = 1;

    private final Callback mCallback;

    /**
     * Contains information about the trace which is being reported.
     *
     * @hide
     */
    @SystemApi(client = PRIVILEGED_APPS)
    public static final class TraceParams {
        private final ParcelFileDescriptor mFd;
        private final UUID mUuid;

        private TraceParams(TraceReportParams params) {
            mFd = params.fd;
            mUuid = new UUID(params.uuidMsb, params.uuidLsb);
        }

        /**
         * Returns the ParcelFileDescriptor for the collected trace.
         */
        @NonNull
        public ParcelFileDescriptor getFd() {
            return mFd;
        }

        /**
         * Returns the UUID of the trace; this is exactly the UUID created by the tracing system
         * (i.e. Perfetto) and is also present inside the trace file.
         */
        @NonNull
        public UUID getUuid() {
            return mUuid;
        }
    }

    public interface Callback {
        /**
         * Called when a trace is reported and sent to this service.
         *
         * Note: the trace file descriptor should not be persisted beyond the lifetime of this
         * function as it is owned by the framework and will be closed immediately after this function
         * returns: if future use of the fd is needed, it should be duped.
         */
        void onReportTrace(@NonNull TraceParams args);
    }

    private TraceReportServiceHelper(@NonNull Callback callback) {
        mCallback = callback;
    }

    private boolean onMessage(@NonNull Message msg) {
        if (msg.what == MSG_REPORT_TRACE) {
            if (!(msg.obj instanceof TraceReportParams)) {
                Log.e(TAG, "Received invalid type for report trace message.");
                return false;
            }
            TraceParams params = new TraceParams((TraceReportParams) msg.obj);
            try {
                mCallback.onReportTrace(params);
            } finally {
                try {
                    params.getFd().close();
                } catch (IOException ignored) {
                }
            }
            return true;
        }
        return false;
    }

    public static IBinder bindWithCallback(@NonNull Callback callback) {
        TraceReportServiceHelper helper = new TraceReportServiceHelper(callback);
        return new Messenger(new Handler(Looper.getMainLooper(), helper::onMessage)).getBinder();
    }
}