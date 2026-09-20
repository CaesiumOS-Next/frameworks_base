/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.power.stats;

import static android.net.NetworkStats.DEFAULT_NETWORK_NO;
import static android.net.NetworkStats.METERED_NO;
import static android.net.NetworkStats.ROAMING_NO;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.power.stats.EnergyConsumerResult;
import android.hardware.power.stats.EnergyConsumerType;
import android.net.NetworkStats;
import android.net.wifi.WifiManager;
import android.os.BatteryConsumer;
import android.os.BatteryStats;
import android.os.BatteryStatsManager;
import android.os.Handler;
import android.os.WorkSource;
import android.os.connectivity.WifiActivityEnergyInfo;
import android.os.connectivity.WifiBatteryStats;
import android.platform.test.ravenwood.RavenwoodRule;
import android.util.IndentingPrintWriter;
import android.util.SparseArray;

import com.android.internal.os.Clock;
import com.android.internal.os.PowerStats;
import com.android.server.power.stats.format.WifiPowerStatsLayout;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class WifiPowerStatsCollectorTest {
    private static final int APP_UID1 = 42;
    private static final int APP_UID2 = 24;
    private static final int APP_UID3 = 44;
    private static final int ISOLATED_UID = 99123;

    @Rule(order = 0)
    public final RavenwoodRule mRavenwood = new RavenwoodRule.Builder()
            .setProvideMainThread(true)
            .build();

    @Rule(order = 1)
    public final BatteryUsageStatsRule mStatsRule = new BatteryUsageStatsRule()
            .setPowerStatsThrottlePeriodMillis(BatteryConsumer.POWER_COMPONENT_WIFI, 1000);

    private MockBatteryStatsImpl mBatteryStats;

    private final MockClock mClock = mStatsRule.getMockClock();

    @Mock
    private Context mContext;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private WifiManager mWifiManager;
    @Mock
    private PowerStatsCollector.ConsumedEnergyRetriever mConsumedEnergyRetriever;
    @Mock
    private Supplier<NetworkStats> mNetworkStatsSupplier;
    @Mock
    private PowerStatsUidResolver mPowerStatsUidResolver;

    private NetworkStats mNetworkStats;
    private List<NetworkStats.Entry> mNetworkStatsEntries;

    private static class ScanTimes {
        public long scanTimeMs;
        public long batchScanTimeMs;
    }

    private final SparseArray<ScanTimes> mScanTimes = new SparseArray<>();
    private long mWifiActiveDuration;

    private final WifiPowerStatsCollector.WifiStatsRetriever mWifiStatsRetriever =
            new WifiPowerStatsCollector.WifiStatsRetriever() {
        @Override
        public void retrieveWifiScanTimes(Callback callback) {
            for (int i = 0; i < mScanTimes.size(); i++) {
                int uid = mScanTimes.keyAt(i);
                ScanTimes scanTimes = mScanTimes.valueAt(i);
                callback.onWifiScanTime(uid, scanTimes.scanTimeMs, scanTimes.batchScanTimeMs);
            }
        }

        @Override
        public long getWifiActiveDuration() {
            return mWifiActiveDuration;
        }
    };

    private final List<PowerStats> mRecordedPowerStats = new ArrayList<>();

    private WifiPowerStatsCollector.Injector mInjector = new WifiPowerStatsCollector.Injector() {
        @Override
        public Handler getHandler() {
            return mStatsRule.getHandler();
        }

        @Override
        public Clock getClock() {
            return mStatsRule.getMockClock();
        }

        @Override
        public PowerStatsUidResolver getUidResolver() {
            return mPowerStatsUidResolver;
        }

        @Override
        public long getPowerStatsCollectionThrottlePeriod(String powerComponentName) {
            return 0;
        }

        @Override
        public PackageManager getPackageManager() {
            return mPackageManager;
        }

        @Override
        public PowerStatsCollector.ConsumedEnergyRetriever getConsumedEnergyRetriever() {
            return mConsumedEnergyRetriever;
        }

        @Override
        public Supplier<NetworkStats> getWifiNetworkStatsSupplier() {
            return mNetworkStatsSupplier;
        }

        @Override
        public WifiPowerStatsCollector.WifiStatsRetriever getWifiStatsRetriever() {
            return mWifiStatsRetriever;
        }

        @Override
        public WifiManager getWifiManager() {
            return mWifiManager;
        }
    };

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)).thenReturn(true);
        when(mPowerStatsUidResolver.mapUid(anyInt())).thenAnswer(invocation -> {
            int uid = invocation.getArgument(0);
            if (uid == ISOLATED_UID) {
                return APP_UID2;
            } else {
                return uid;
            }
        });
        when(mContext.getSystemService(Context.WIFI_SERVICE)).thenReturn(mWifiManager);
        mBatteryStats = mStatsRule.getBatteryStats();
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void triggering() throws Throwable {
        PowerStatsCollector collector = mBatteryStats.getPowerStatsCollector(
                BatteryConsumer.POWER_COMPONENT_WIFI);
        collector.addConsumer(mRecordedPowerStats::add);

        mBatteryStats.setPowerStatsCollectorEnabled(BatteryConsumer.POWER_COMPONENT_WIFI, true);

        mockWifiActivityInfo(1000, 2000, 3000, 600, 100);

        // This should trigger a sample collection to establish a baseline
        mBatteryStats.onSystemReady(mContext);

        mStatsRule.waitForBackgroundThread();
        assertThat(mRecordedPowerStats).hasSize(1);

        mRecordedPowerStats.clear();
        mStatsRule.setTime(20000, 20000);
        mBatteryStats.noteWifiOnLocked(mClock.realtime, mClock.uptime);
        mStatsRule.waitForBackgroundThread();
        assertThat(mRecordedPowerStats).hasSize(1);

        mRecordedPowerStats.clear();
        mStatsRule.setTime(40000, 40000);
        mBatteryStats.noteWifiOffLocked(mClock.realtime, mClock.uptime);
        mStatsRule.waitForBackgroundThread();
        assertThat(mRecordedPowerStats).hasSize(1);

        mRecordedPowerStats.clear();
        mStatsRule.setTime(50000, 50000);
        mBatteryStats.noteWifiRunningLocked(new WorkSource(APP_UID1), mClock.realtime,
                mClock.uptime);
        mStatsRule.waitForBackgroundThread();
        assertThat(mRecordedPowerStats).hasSize(1);

        mRecordedPowerStats.clear();
        mStatsRule.setTime(60000, 60000);
        mBatteryStats.noteWifiStoppedLocked(new WorkSource(APP_UID1), mClock.realtime,
                mClock.uptime);
        mStatsRule.waitForBackgroundThread();
        assertThat(mRecordedPowerStats).hasSize(1);

        mRecordedPowerStats.clear();
        mStatsRule.setTime(70000, 70000);
        mBatteryStats.noteWifiStateLocked(BatteryStatsManager.WIFI_STATE_ON_CONNECTED_STA,
                "mywyfy", mClock.realtime);
        mStatsRule.waitForBackgroundThread();
        assertThat(mRecordedPowerStats).hasSize(1);
    }

    @Test
    public void collectStats_powerReportingSupported() throws Throwable {
        PowerStats powerStats = collectPowerStats(true);
        assertThat(powerStats.durationMs).isEqualTo(7500);

        PowerStats.Descriptor descriptor = powerStats.descriptor;
        WifiPowerStatsLayout layout = new WifiPowerStatsLayout(descriptor);
        assertThat(layout.isPowerReportingSupported()).isTrue();
        assertThat(layout.getDeviceRxTime(powerStats.stats)).isEqualTo(6000);
        assertThat(layout.getDeviceTxTime(powerStats.stats)).isEqualTo(1000);
        assertThat(layout.getDeviceScanTime(powerStats.stats)).isEqualTo(200);
        assertThat(layout.getDeviceIdleTime(powerStats.stats)).isEqualTo(300);
        assertThat(layout.getConsumedEnergy(powerStats.stats, 0))
                .isEqualTo((64321 - 10000) * 1000 / 3500);

        verifyUidStats(powerStats);
    }

    @Test
    public void collectStats_powerReportingUnsupported() {
        PowerStats powerStats = collectPowerStats(false);
        assertThat(powerStats.durationMs).isEqualTo(13200);

        PowerStats.Descriptor descriptor = powerStats.descriptor;
        WifiPowerStatsLayout layout = new WifiPowerStatsLayout(descriptor);
        assertThat(layout.isPowerReportingSupported()).isFalse();
        assertThat(layout.getDeviceActiveTime(powerStats.stats)).isEqualTo(7500);
        assertThat(layout.getDeviceBasicScanTime(powerStats.stats)).isEqualTo(234 + 100 + 300);
        assertThat(layout.getDeviceBatchedScanTime(powerStats.stats)).isEqualTo(345 + 200 + 400);
        assertThat(layout.getConsumedEnergy(powerStats.stats, 0))
                .isEqualTo((64321 - 10000) * 1000 / 3500);

        verifyUidStats(powerStats);
    }

    private void verifyUidStats(PowerStats powerStats) {
        WifiPowerStatsLayout layout = new WifiPowerStatsLayout(powerStats.descriptor);
        assertThat(powerStats.uidStats.size()).isEqualTo(2);
        long[] actual1 = powerStats.uidStats.get(APP_UID1);
        assertThat(layout.getUidRxBytes(actual1)).isEqualTo(1000);
        assertThat(layout.getUidTxBytes(actual1)).isEqualTo(2000);
        assertThat(layout.getUidRxPackets(actual1)).isEqualTo(100);
        assertThat(layout.getUidTxPackets(actual1)).isEqualTo(200);
        assertThat(layout.getUidScanTime(actual1)).isEqualTo(234);
        assertThat(layout.getUidBatchedScanTime(actual1)).isEqualTo(345);

        // Combines APP_UID2 and ISOLATED_UID
        long[] actual2 = powerStats.uidStats.get(APP_UID2);
        assertThat(layout.getUidRxBytes(actual2)).isEqualTo(6000);
        assertThat(layout.getUidTxBytes(actual2)).isEqualTo(3000);
        assertThat(layout.getUidRxPackets(actual2)).isEqualTo(60);
        assertThat(layout.getUidTxPackets(actual2)).isEqualTo(30);
        assertThat(layout.getUidScanTime(actual2)).isEqualTo(100 + 300);
        assertThat(layout.getUidBatchedScanTime(actual2)).isEqualTo(200 + 400);

        assertThat(powerStats.uidStats.get(ISOLATED_UID)).isNull();
        assertThat(powerStats.uidStats.get(APP_UID3)).isNull();
    }

    @Test
    public void dump() throws Throwable {
        PowerStats powerStats = collectPowerStats(true);
        StringWriter sw = new StringWriter();
        IndentingPrintWriter pw = new IndentingPrintWriter(sw);
        powerStats.dump(pw);
        pw.flush();
        String dump = sw.toString();
        assertThat(dump).contains("duration=7500");
        assertThat(dump).contains(
                "rx: 6000 tx: 1000 idle: 300 scan: 200 basic-scan: 634 batched-scan: 945"
                        + " energy: " + ((64321 - 10000) * 1000 / 3500));
        assertThat(dump).contains(
                "UID 24: rx-pkts: 60 rx-B: 6000 tx-pkts: 30 tx-B: 3000"
                        + " scan: 400 batched-scan: 600");
        assertThat(dump).contains(
                "UID 42: rx-pkts: 100 rx-B: 1000 tx-pkts: 200 tx-B: 2000"
                        + " scan: 234 batched-scan: 345");
    }

    @Test
    public void getWifiBatteryStats() throws Throwable {
        when(mWifiManager.isEnhancedPowerReportingSupported()).thenReturn(true);
        mBatteryStats.setPowerStatsCollectorEnabled(BatteryConsumer.POWER_COMPONENT_WIFI,
                true);

        mockWifiActivityInfo(1000, 600, 100, 2000, 3000);
        mockNetworkStats(1000);
        mockNetworkStatsEntry(APP_UID1, 4321, 321, 1234, 23);
        mockNetworkStatsEntry(APP_UID2, 4000, 40, 2000, 20);
        mockWifiScanTimes(APP_UID1, 1000, 2000);
        mockWifiScanTimes(APP_UID2, 3000, 4000);

        // This should trigger a baseline sample collection
        mBatteryStats.onSystemReady(mContext);
        mStatsRule.waitForBackgroundThread();

        mockWifiActivityInfo(1100, 6600, 1100, 2200, 3300);
        mockNetworkStats(1100);
        mockNetworkStatsEntry(APP_UID1, 5321, 421, 3234, 223);
        mockNetworkStatsEntry(APP_UID2, 8000, 80, 4000, 40);
        mockWifiScanTimes(APP_UID1, 1234, 2345);
        mockWifiScanTimes(APP_UID2, 3100, 4200);

        mStatsRule.setTime(30000, 30000);
        mBatteryStats.getPowerStatsCollector(BatteryConsumer.POWER_COMPONENT_WIFI)
                .schedule();
        mStatsRule.waitForBackgroundThread();

        WifiBatteryStats stats = mBatteryStats.getWifiBatteryStats();
        assertThat(stats.getNumPacketsRx()).isEqualTo(501);
        assertThat(stats.getNumBytesRx()).isEqualTo(13321);
        assertThat(stats.getNumPacketsTx()).isEqualTo(263);
        assertThat(stats.getNumBytesTx()).isEqualTo(7234);
        assertThat(stats.getScanTimeMillis()).isEqualTo(200);
        assertThat(stats.getRxTimeMillis()).isEqualTo(6000);
        assertThat(stats.getTxTimeMillis()).isEqualTo(1000);
        assertThat(stats.getIdleTimeMillis()).isEqualTo(300);
        assertThat(stats.getSleepTimeMillis()).isEqualTo(30000 - 6000 - 1000 - 300);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void getWifiBatteryStats_collectorEnabled_doesNotDoubleCountTraffic() throws Throwable {
        when(mWifiManager.isEnhancedPowerReportingSupported()).thenReturn(true);
        mBatteryStats.setPowerStatsCollectorEnabled(BatteryConsumer.POWER_COMPONENT_WIFI,
                true);

        BatteryExternalStatsWorker worker = new BatteryExternalStatsWorker(
                new BatteryExternalStatsWorker.Injector(mContext) {
                    @Override
                    public <T> T getSystemService(Class<T> serviceClass) {
                        if (serviceClass == WifiManager.class) {
                            return (T) mWifiManager;
                        }
                        return null;
                    }

                    @Override
                    public <T> T getLocalService(Class<T> serviceClass) {
                        return null;
                    }
                },
                mBatteryStats, mStatsRule.getHandler());
        worker.systemServicesReady();

        mockWifiActivityInfo(1000, 600, 100, 2000, 3000);
        mockNetworkStats(1000);
        mockNetworkStatsEntry(APP_UID1, 4321, 321, 1234, 23);
        mockNetworkStatsEntry(APP_UID2, 4000, 40, 2000, 20);
        mockWifiScanTimes(APP_UID1, 1000, 2000);
        mockWifiScanTimes(APP_UID2, 3000, 4000);

        // Trigger baseline sample collection
        mBatteryStats.onSystemReady(mContext);
        mStatsRule.waitForBackgroundThread();

        mockWifiActivityInfo(1100, 6600, 1100, 2200, 3300);
        mockNetworkStats(1100);
        mockNetworkStatsEntry(APP_UID1, 5321, 421, 3234, 223);
        mockNetworkStatsEntry(APP_UID2, 8000, 80, 4000, 40);
        mockWifiScanTimes(APP_UID1, 1234, 2345);
        mockWifiScanTimes(APP_UID2, 3100, 4200);

        mStatsRule.setTime(30000, 30000);
        // Schedule external UPDATE_WIFI sync via BatteryExternalStatsWorker; when the collector is
        // enabled, this should schedule the collector rather than running legacy updateWifiState().
        worker.scheduleSync("test-wifi", BatteryStatsImpl.ExternalStatsSync.UPDATE_WIFI);
        // Two handler hops: the first wait drains the worker sync() task, the second drains the
        // collectAndDeliverStats task that sync() posts via collector.schedule().
        mStatsRule.waitForBackgroundThread();
        mStatsRule.waitForBackgroundThread();

        // Also verify that a direct call to updateWifiState() is ignored when the collector is
        // enabled, preventing duplicate accumulation of UID and global Wi-Fi traffic.
        mBatteryStats.updateWifiState(
                new WifiActivityEnergyInfo(1100, 0, 1000, 6000, 200, 300),
                BatteryStats.POWER_DATA_UNAVAILABLE, 30000, 30000,
                mock(NetworkStatsManager.class));

        WifiBatteryStats stats = mBatteryStats.getWifiBatteryStats();
        assertThat(stats.getNumPacketsRx()).isEqualTo(501);
        assertThat(stats.getNumBytesRx()).isEqualTo(13321);
        assertThat(stats.getNumPacketsTx()).isEqualTo(263);
        assertThat(stats.getNumBytesTx()).isEqualTo(7234);
        assertThat(stats.getScanTimeMillis()).isEqualTo(200);
        assertThat(stats.getRxTimeMillis()).isEqualTo(6000);
        assertThat(stats.getTxTimeMillis()).isEqualTo(1000);
        assertThat(stats.getIdleTimeMillis()).isEqualTo(300);
        assertThat(stats.getSleepTimeMillis()).isEqualTo(30000 - 6000 - 1000 - 300);

        BatteryStats.Uid uid1 = mBatteryStats.getUidStatsLocked(APP_UID1);
        assertThat(uid1.getNetworkActivityBytes(
                BatteryStats.NETWORK_WIFI_RX_DATA, BatteryStats.STATS_SINCE_CHARGED))
                .isEqualTo(5321);
        assertThat(uid1.getNetworkActivityBytes(
                BatteryStats.NETWORK_WIFI_TX_DATA, BatteryStats.STATS_SINCE_CHARGED))
                .isEqualTo(3234);
        assertThat(uid1.getNetworkActivityPackets(
                BatteryStats.NETWORK_WIFI_RX_DATA, BatteryStats.STATS_SINCE_CHARGED))
                .isEqualTo(421);
        assertThat(uid1.getNetworkActivityPackets(
                BatteryStats.NETWORK_WIFI_TX_DATA, BatteryStats.STATS_SINCE_CHARGED))
                .isEqualTo(223);

        BatteryStats.Uid uid2 = mBatteryStats.getUidStatsLocked(APP_UID2);
        assertThat(uid2.getNetworkActivityBytes(
                BatteryStats.NETWORK_WIFI_RX_DATA, BatteryStats.STATS_SINCE_CHARGED))
                .isEqualTo(8000);
        assertThat(uid2.getNetworkActivityBytes(
                BatteryStats.NETWORK_WIFI_TX_DATA, BatteryStats.STATS_SINCE_CHARGED))
                .isEqualTo(4000);
        assertThat(uid2.getNetworkActivityPackets(
                BatteryStats.NETWORK_WIFI_RX_DATA, BatteryStats.STATS_SINCE_CHARGED))
                .isEqualTo(80);
        assertThat(uid2.getNetworkActivityPackets(
                BatteryStats.NETWORK_WIFI_TX_DATA, BatteryStats.STATS_SINCE_CHARGED))
                .isEqualTo(40);
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void updateWifiState_collectorConfiguredBeforeSystemReady_doesNotDoubleCountTraffic()
            throws Throwable {
        when(mWifiManager.isEnhancedPowerReportingSupported()).thenReturn(true);
        mBatteryStats.setPowerStatsCollectorEnabled(BatteryConsumer.POWER_COMPONENT_WIFI,
                true);

        mockWifiActivityInfo(1000, 600, 100, 2000, 3000);
        mockNetworkStats(1000);
        mockNetworkStatsEntry(APP_UID1, 4321, 321, 1234, 23);
        mockNetworkStatsEntry(APP_UID2, 4000, 40, 2000, 20);

        // The collector is configured but not yet enabled, because onSystemReady() has not run.
        mBatteryStats.updateWifiState(
                new WifiActivityEnergyInfo(1000, 0, 100, 600, 2000, 3000),
                BatteryStats.POWER_DATA_UNAVAILABLE, 10000, 10000,
                mock(NetworkStatsManager.class));

        WifiBatteryStats stats = mBatteryStats.getWifiBatteryStats();
        assertThat(stats.getNumBytesRx()).isEqualTo(0);
        assertThat(stats.getNumBytesTx()).isEqualTo(0);

        // The first collector sample records the traffic accumulated since boot exactly once.
        mBatteryStats.onSystemReady(mContext);
        mStatsRule.waitForBackgroundThread();

        stats = mBatteryStats.getWifiBatteryStats();
        assertThat(stats.getNumPacketsRx()).isEqualTo(361);
        assertThat(stats.getNumBytesRx()).isEqualTo(8321);
        assertThat(stats.getNumPacketsTx()).isEqualTo(43);
        assertThat(stats.getNumBytesTx()).isEqualTo(3234);

        BatteryStats.Uid uid1 = mBatteryStats.getUidStatsLocked(APP_UID1);
        assertThat(uid1.getNetworkActivityBytes(
                BatteryStats.NETWORK_WIFI_RX_DATA, BatteryStats.STATS_SINCE_CHARGED))
                .isEqualTo(4321);
        assertThat(uid1.getNetworkActivityBytes(
                BatteryStats.NETWORK_WIFI_TX_DATA, BatteryStats.STATS_SINCE_CHARGED))
                .isEqualTo(1234);
    }

    private PowerStats collectPowerStats(boolean hasPowerReporting) {
        when(mWifiManager.isEnhancedPowerReportingSupported()).thenReturn(hasPowerReporting);

        WifiPowerStatsCollector collector = new WifiPowerStatsCollector(mInjector, null);
        collector.setEnabled(true);

        when(mConsumedEnergyRetriever.getVoltageMv()).thenReturn(3500);
        when(mConsumedEnergyRetriever.getEnergyConsumerIds(EnergyConsumerType.WIFI))
                .thenReturn(new int[]{777});

        if (hasPowerReporting) {
            mockWifiActivityInfo(1000, 600, 100, 2000, 3000);
        } else {
            mWifiActiveDuration = 5700;
        }
        mockNetworkStats(1000);
        mockNetworkStatsEntry(APP_UID1, 4321, 321, 1234, 23);
        mockNetworkStatsEntry(APP_UID2, 4000, 40, 2000, 20);
        mockNetworkStatsEntry(ISOLATED_UID, 2000, 20, 1000, 10);
        mockNetworkStatsEntry(APP_UID3, 314, 281, 314, 281);
        mockWifiScanTimes(APP_UID1, 1000, 2000);
        mockWifiScanTimes(APP_UID2, 3000, 4000);
        mockWifiScanTimes(ISOLATED_UID, 5000, 6000);

        when(mConsumedEnergyRetriever.getConsumedEnergy(eq(new int[]{777})))
                .thenReturn(new EnergyConsumerResult[]{mockEnergyConsumer(10_000)});

        collector.collectStats();

        if (hasPowerReporting) {
            mockWifiActivityInfo(1100, 6600, 1100, 2200, 3300);
        } else {
            mWifiActiveDuration = 13200;
        }
        mockNetworkStats(1100);
        mockNetworkStatsEntry(APP_UID1, 5321, 421, 3234, 223);
        mockNetworkStatsEntry(APP_UID2, 8000, 80, 4000, 40);
        mockNetworkStatsEntry(ISOLATED_UID, 4000, 40, 2000, 20);
        mockNetworkStatsEntry(APP_UID3, 314, 281, 314, 281);    // Unchanged
        mockWifiScanTimes(APP_UID1, 1234, 2345);
        mockWifiScanTimes(APP_UID2, 3100, 4200);
        mockWifiScanTimes(ISOLATED_UID, 5300, 6400);

        when(mConsumedEnergyRetriever.getConsumedEnergy(eq(new int[]{777})))
                .thenReturn(new EnergyConsumerResult[]{mockEnergyConsumer(64_321)});

        mStatsRule.setTime(20000, 20000);
        return collector.collectStats();
    }

    private EnergyConsumerResult mockEnergyConsumer(long energyUWs) {
        EnergyConsumerResult ecr = new EnergyConsumerResult();
        ecr.energyUWs = energyUWs;
        return ecr;
    }

    private void mockWifiActivityInfo(long timestamp, long rxTimeMs, long txTimeMs, int scanTimeMs,
            int idleTimeMs) {
        int stackState = 0;
        WifiActivityEnergyInfo info = new WifiActivityEnergyInfo(timestamp, stackState, txTimeMs,
                rxTimeMs, scanTimeMs, idleTimeMs);
        doAnswer(invocation -> {
            WifiManager.OnWifiActivityEnergyInfoListener listener = invocation.getArgument(1);
            listener.onWifiActivityEnergyInfo(info);
            return null;
        }).when(mWifiManager).getWifiActivityEnergyInfoAsync(any(), any());
    }

    private void mockNetworkStats(long elapsedRealtime) {
        if (RavenwoodRule.isOnRavenwood()) {
            mNetworkStats = mock(NetworkStats.class);
            ArrayList<NetworkStats.Entry> networkStatsEntries = new ArrayList<>();
            when(mNetworkStats.iterator()).thenAnswer(inv -> networkStatsEntries.iterator());
            mNetworkStatsEntries = networkStatsEntries;
        } else {
            mNetworkStats = new NetworkStats(elapsedRealtime, 1);
        }
        mBatteryStats.setNetworkStats(mNetworkStats);
        when(mNetworkStatsSupplier.get()).thenReturn(mNetworkStats);
    }

    private void mockNetworkStatsEntry(int uid, long rxBytes, long rxPackets, long txBytes,
            long txPackets) {
        if (RavenwoodRule.isOnRavenwood()) {
            NetworkStats.Entry entry = mock(NetworkStats.Entry.class);
            when(entry.getUid()).thenReturn(uid);
            when(entry.getMetered()).thenReturn(METERED_NO);
            when(entry.getRoaming()).thenReturn(ROAMING_NO);
            when(entry.getDefaultNetwork()).thenReturn(DEFAULT_NETWORK_NO);
            when(entry.getRxBytes()).thenReturn(rxBytes);
            when(entry.getRxPackets()).thenReturn(rxPackets);
            when(entry.getTxBytes()).thenReturn(txBytes);
            when(entry.getTxPackets()).thenReturn(txPackets);
            when(entry.getOperations()).thenReturn(100L);
            mNetworkStatsEntries.add(entry);
        } else {
            mNetworkStats = mNetworkStats
                    .addEntry(new NetworkStats.Entry("wifi", uid, 0, 0,
                            METERED_NO, ROAMING_NO, DEFAULT_NETWORK_NO, rxBytes, rxPackets,
                            txBytes, txPackets, 100));
            mBatteryStats.setNetworkStats(mNetworkStats);
            reset(mNetworkStatsSupplier);
            when(mNetworkStatsSupplier.get()).thenReturn(mNetworkStats);
        }
    }

    private void mockWifiScanTimes(int uid, long scanTimeMs, long batchScanTimeMs) {
        ScanTimes scanTimes = new ScanTimes();
        scanTimes.scanTimeMs = scanTimeMs;
        scanTimes.batchScanTimeMs = batchScanTimeMs;
        mScanTimes.put(uid, scanTimes);
    }
}
