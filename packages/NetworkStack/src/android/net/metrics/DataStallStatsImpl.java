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

package android.net.metrics;

import android.annotation.NonNull;
import android.net.INetworkMonitorCallbacks;
import android.net.captiveportal.CaptivePortalProbeResult;
import android.os.RemoteException;
import android.util.Log;
import android.util.StatsLog;

import com.android.server.connectivity.nano.DataStallEventProto;
import com.android.server.connectivity.nano.DnsEvent;

import com.google.protobuf.nano.MessageNano;

import java.util.ArrayList;

/**
 * Class to gather data stall data needed for metrics and send it to statsLog.
 *
 * To see if the logs are properly sent to statsd, execute following command.
 *
 * $ adb shell cmd stats print-logs
 * $ adb logcat | grep statsd  OR $ adb logcat -b stats
 *
 * @hide
 */
public class DataStallStatsImpl{
    private static final String TAG = DataStallStatsImpl.class.getSimpleName();
    private static final boolean DBG = true; // false
    @NonNull
    private ArrayList<DataStallDetectionStats> mStatsList = new ArrayList<>();

    /**
     * Add a {@Link DataStallDetectionStats}.
     * @param stat
     */
    public void addDetectionStats(@NonNull final DataStallDetectionStats stat) {
        mStatsList.add(stat);
    }

    /**
     * Reset the array list of {@Link DataStallDetectionStats}
     */
    public void resetDetectionStats() {
        mStatsList.clear();
    }

    /**
     * Update network evaluation result and see if it's the expected result. Write to stats if the
     * evaluation result is triggered from data stall detection.
     *
     * @param result a {@Link CaptivePortalProbeResult} indicating the probe result.
     * @param token a token number to exam if the probe result is expected or not.
     */
    public void updateEvalStatsAndWriteStats(@NonNull final CaptivePortalProbeResult result,
            final int token, @NonNull final INetworkMonitorCallbacks cb) {
        if (mStatsList.size() > 0) {
            for (int i = 0; i < mStatsList.size(); i++) {
                // Expected evaluation result received.
                if (mStatsList.get(i).seqNo == token) {
                    write(mStatsList.get(i), probeResultToEnum(result), cb);
                }
            }
        }
    }


    private int probeResultToEnum(@NonNull final CaptivePortalProbeResult result) {
        if (result.isSuccessful()) {
            return DataStallEventProto.VALID;
        } else if (result.isPortal()) {
            return DataStallEventProto.PORTAL;
        } else {
            return DataStallEventProto.INVALID;
        }
    }

    /**
     * Write the metric to {@link StatsLog}.
     */
    public void write(@NonNull final DataStallDetectionStats stats, int probeResult,
            @NonNull final INetworkMonitorCallbacks cb) {
        DnsEvent dns = new DnsEvent();
        dns.dnsReturnCode = stats.dnsReturnCode;
        dns.dnsTime = stats.dnsTimeStamp;
        if (DBG) {
            Log.d(TAG, "write: " + stats + " with result: " + probeResult
                    + ", dns: " + MessageNano.toByteArray(dns));
        }
        try {
            cb.sendDataStallStats(stats.evaluationType,     /* evaluation_type */
                    probeResult,                            /* validation_result */
                    stats.networkType,                      /* network_type */
                    stats.wifiInfo,                         /* wifi_info */
                    stats.cellularInfo,                     /* cell_info */
                    MessageNano.toByteArray(dns)            /* dns_event */
            );
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending stats", e);
        }
    }
}
