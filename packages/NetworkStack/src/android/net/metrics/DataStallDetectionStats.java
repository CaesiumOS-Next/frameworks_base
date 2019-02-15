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
import android.annotation.Nullable;
import android.net.wifi.WifiInfo;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.server.connectivity.nano.CellularData;
import com.android.server.connectivity.nano.DataStallEventProto;
import com.android.server.connectivity.nano.WifiData;

import com.google.protobuf.nano.MessageNano;

import java.util.ArrayList;
import java.util.List;

/**
 * Class to record the stats of detection level information for data stall.
 *
 * @hide
 */
public final class DataStallDetectionStats implements Parcelable {
    private static final int UNKNOWN_SIGNAL_STRENGTH = -1;
    @NonNull
    public byte[] cellularInfo;
    @NonNull
    public byte[] wifiInfo;
    @NonNull
    public int[] dnsReturnCode;
    @NonNull
    public long[] dnsTimeStamp;
    public final int evaluationType;
    public final int networkType;
    public int seqNo;

    public DataStallDetectionStats(@Nullable byte[] cell, @Nullable byte[] wifi,
            @NonNull int[] returnCode, @NonNull long[] dnsTime, int evalType, int netType, int no) {
        if (cell != null) {
            this.cellularInfo = cell;
        } else {
            initCellularData();
        }
        if (wifi != null) {
            this.wifiInfo = wifi;
        } else {
            initWifiInfo();
        }

        this.dnsReturnCode = returnCode;
        this.dnsTimeStamp = dnsTime;
        this.evaluationType = evalType;
        this.networkType = netType;
        this.seqNo = no;
    }

    private void initCellularData() {
        CellularData data  = new CellularData();
        data.ratType = DataStallEventProto.RADIO_TECHNOLOGY_UNKNOWN;
        data.networkMccmnc = "";
        data.simMccmnc = "";
        data.signalStrength = UNKNOWN_SIGNAL_STRENGTH;
        cellularInfo = MessageNano.toByteArray(data);
    }

    private void initWifiInfo() {
        WifiData data = new WifiData();
        data.wifiBand = DataStallEventProto.AP_BAND_UNKNOWN;
        data.signalStrength = UNKNOWN_SIGNAL_STRENGTH;
        wifiInfo = MessageNano.toByteArray(data);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("type: ").append(networkType)
          .append(", evaluation type: ").append(evaluationType)
          .append(", sequence no: ").append(seqNo)
          .append(", wifi info: ").append(new String(wifiInfo))
          .append(", cell info: ").append(new String(cellularInfo));
        return sb.toString();
    }

    /**
     * Utility to create an instance of {@Link DataStallStats}
     *
     * @hide
     */
    public static class Builder {
        @Nullable
        private byte[] mCellularInfo = null;
        @Nullable
        private byte[] mWifiInfo = null;
        @NonNull
        private final List<Integer> mDnsReturnCode = new ArrayList<Integer>();
        @NonNull
        private final List<Long> mDnsTimeStamp = new ArrayList<Long>();
        private int mEvaluationType;
        private int mNetworkType;
        private int mSeqNo;

        /**
         * Set a dns event into Builder.
         *
         * @param code the return code of the dns event.
         * @param timeMs the elapsedRealtime in ms that the the dns event was received from netd.
         * @return {@code this} {@link Builder} instance.
         */
        public Builder setDnsEvent(int code, long timeMs) {
            mDnsReturnCode.add(code);
            mDnsTimeStamp.add(timeMs);
            return this;
        }

        /**
         * Set the dns evaluation type into Builder.
         *
         * @param type the return code of the dns event.
         * @return {@code this} {@link Builder} instance.
         */
        public Builder setEvaluationType(int type) {
            mEvaluationType = type;
            return this;
        }

        /**
         * Log the network type into Builder.
         *
         * @param type the network type of the logged network.
         * @return {@code this} {@link Builder} instance.
         */
        public Builder setNetworkType(int type) {
            mNetworkType = type;
            return this;
        }

        /**
         * Set the wifi data into Builder.
         *
         * @param ss the signal strength of the logged wifi network.
         * @param info a {@link WifiInfo} of the connected wifi network.
         * @return {@code this} {@link Builder} instance.
         */
        public Builder setWiFiData(int ss, @NonNull WifiInfo info) {
            WifiData data = new WifiData();
            int freq = info.getFrequency();
            // Refer to ScanResult.is5GHz() and ScanResult.is24GHz().
            if (freq > 4900 && freq < 5900) {
                data.wifiBand = DataStallEventProto.AP_BAND_5GHZ;
            } else if (freq > 2400 && freq < 2500) {
                data.wifiBand = DataStallEventProto.AP_BAND_2GHZ;
            } else {
                data.wifiBand = DataStallEventProto.AP_BAND_UNKNOWN;
            }

            data.signalStrength = ss;
            mWifiInfo = MessageNano.toByteArray(data);
            return this;
        }

        /**
         * Set the cellular data into Builder.
         *
         * @param rat the radio technology of the logged cellular network.
         * @param roaming a boolean indicates if logged cellular network is roaming or not.
         * @param networkMccmnc the mccmnc of the camped network.
         * @param simMccmnc the mccmnc of the sim.
         * @return {@code this} {@link Builder} instance.
         */
        public Builder setCellData(int rat, boolean roaming,
                @NonNull String networkMccmnc, @NonNull String simMccmnc, int ss) {
            CellularData data  = new CellularData();
            data.ratType = rat;
            data.isRoaming = roaming;
            data.networkMccmnc = networkMccmnc;
            data.simMccmnc = simMccmnc;
            data.signalStrength = ss;
            mCellularInfo = MessageNano.toByteArray(data);
            return this;
        }

        /**
         * Set the sequence number which is used to map the evaluation result in
         * DataStallStatsManager.
         *
         * @param no the sequence number expected
         */
        public Builder setSeqNo(final int no) {
            mSeqNo = no;
            return this;
        }

        /**
         * Create a new {@Link DataStallDetectionStats}.
         */
        public DataStallDetectionStats build() {
            return new DataStallDetectionStats(mCellularInfo, mWifiInfo,
                    mDnsReturnCode.stream().mapToInt(i -> i).toArray(),
                    mDnsTimeStamp.stream().mapToLong(i -> i).toArray(),
                    mEvaluationType, mNetworkType, mSeqNo);
        }
    }

    private DataStallDetectionStats(Parcel in) {
        if (in.readInt() == 1) {
            int cellInfoLength = in.readInt();
            cellularInfo = new byte[cellInfoLength];
            in.readByteArray(cellularInfo);
        }
        if (in.readInt() == 1) {
            int wifiInfoLength = in.readInt();
            wifiInfo = new byte[wifiInfoLength];
            in.readByteArray(wifiInfo);
        }
        int codeLength = in.readInt();
        dnsReturnCode = new int[codeLength];
        in.readIntArray(dnsReturnCode);

        int timeLength = in.readInt();
        dnsTimeStamp = new long[timeLength];
        in.readLongArray(dnsTimeStamp);
        evaluationType = in.readInt();
        networkType = in.readInt();
        seqNo = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(cellularInfo == null ? 0 : 1);
        if (cellularInfo != null) {
            out.writeInt(cellularInfo.length);
            out.writeByteArray(cellularInfo);
        }
        if (wifiInfo != null) {
            out.writeInt(wifiInfo == null ? 0 : 1);
            out.writeInt(wifiInfo.length);
        }
        out.writeByteArray(wifiInfo);
        out.writeInt(dnsReturnCode.length);
        out.writeIntArray(dnsReturnCode);
        out.writeInt(dnsTimeStamp.length);
        out.writeLongArray(dnsTimeStamp);
        out.writeInt(evaluationType);
        out.writeInt(networkType);
        out.writeInt(seqNo);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<DataStallDetectionStats> CREATOR =
            new Parcelable.Creator<DataStallDetectionStats>() {
            public DataStallDetectionStats createFromParcel(Parcel in) {
                return new DataStallDetectionStats(in);
            }

            public DataStallDetectionStats[] newArray(int size) {
                return new DataStallDetectionStats[size];
            }
    };
}
