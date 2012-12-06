/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.systemui.statusbar.policy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wimax.WimaxManagerConstants;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.app.IBatteryStats;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.cdma.EriInfo;
import com.android.internal.util.AsyncChannel;
import com.android.systemui.DemoMode;
import com.android.systemui.R;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


import com.android.internal.telephony.RILConstants.SimCardID;
import static com.android.internal.telephony.RILConstants.SimCardID.*;
import static com.android.internal.telephony.TelephonyProperties.*;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.SystemProperties;

public class NetworkController_brcm extends BroadcastReceiver implements DemoMode {
    // debug
    static final String TAG = "StatusBar.NetworkControllerDS";
    static final boolean DEBUG = true;
    static final boolean CHATTY = true; // additional diagnostics, but not logspew

    private static final int FLIGHT_MODE_ICON = R.drawable.stat_sys_signal_flightmode;

    // telephony
    boolean mHspaDataDistinguishable;
    boolean mShowPhoneRSSIForData = false;
    boolean mShowAtLeastThreeGees = false;
    boolean mAlwaysShowCdmaRssi = false;
    String mNetworkNameSeparator;

    String mContentDescriptionWifi;
    String mContentDescriptionWimax;
    String mContentDescriptionCombinedSignal;

    // wifi
    final WifiManager mWifiManager;
    AsyncChannel mWifiChannel;
    boolean mWifiEnabled, mWifiConnected;
    int mWifiRssi, mWifiLevel;
    String mWifiSsid;
    int mWifiIconId = 0;
    int mQSWifiIconId = 0;
    int mWifiActivity = WifiManager.DATA_ACTIVITY_NONE;

    // bluetooth
    private boolean mBluetoothTethered = false;
    private int mBluetoothTetherIconId =
        com.android.internal.R.drawable.stat_sys_tether_bluetooth;

    //wimax
    private boolean mWimaxSupported = false;
    private boolean mIsWimaxEnabled = false;
    private boolean mWimaxConnected = false;
    private boolean mWimaxIdle = false;
    private int mWimaxIconId = 0;
    private int mWimaxSignal = 0;
    private int mWimaxState = 0;
    private int mWimaxExtraState = 0;

    // data connectivity (regardless of state, can we access the internet?)
    // state of inet connection - 0 not connected, 100 connected
    private boolean mConnected = false;
    private int mConnectedNetworkType = ConnectivityManager.TYPE_NONE;
    private String mConnectedNetworkTypeName;
    private int mInetCondition = 0;
    private static final int INET_CONDITION_THRESHOLD = 50;

    private boolean mAirplaneMode = false;
    private boolean mLastAirplaneMode = true;

    private Locale mLocale = null;
    private Locale mLastLocale = null;

    // our ui
    Context mContext;
    ArrayList<TextView> mCombinedLabelViews = new ArrayList<TextView>();
    ArrayList<TextView> mMobileLabelViews = new ArrayList<TextView>();
    ArrayList<TextView> mWifiLabelViews = new ArrayList<TextView>();
    ArrayList<TextView> mEmergencyLabelViews = new ArrayList<TextView>();
    ArrayList<SignalCluster> mSignalClusters = new ArrayList<SignalCluster>();
    ArrayList<NetworkSignalChangedCallback> mSignalsChangedCallbacks =
            new ArrayList<NetworkSignalChangedCallback>();

    int mLastWifiIconId = -1;
    int mLastWimaxIconId = -1;
    int mLastCombinedSignalIconId = -1;

    String mLastCombinedLabel = "";

    boolean mDataAndWifiStacked = false;


    private NotificationManager mNotificationMgr;
/* vdang:fixme - to be restored
    public final static int DEFAULT_MODE_BOTH_OR_NONE = 2;
*/

    public interface SignalCluster {
        void setWifiIndicators(boolean visible, int strengthIcon,
                String contentDescription);
        void setMobileDataIndicators1(boolean visible, int strengthIcon,
                int typeIcon, int simAbsentIcon, String contentDescription, String typeContentDescription);
        void setMobileDataIndicators2(boolean visible, int strengthIcon,
                int typeIcon, int simAbsentIcon, String contentDescription, String typeContentDescription);
        void setIsAirplaneMode(boolean is, int airplaneIcon);
        void setIsAirplaneMode2(boolean is, int airplaneIcon);
    }

    public interface NetworkSignalChangedCallback {
        void onWifiSignalChanged(boolean enabled, int wifiSignalIconId,
                boolean activityIn, boolean activityOut,
                String wifitSignalContentDescriptionId, String description);
        void onMobileDataSignalChanged(boolean enabled, int mobileSignalIconId,
                String mobileSignalContentDescriptionId, int dataTypeIconId,
                boolean activityIn, boolean activityOut,
                String dataTypeContentDescriptionId, String description);
        void onMobileDataSignalChanged2(boolean enabled, int mobileSignalIconId,
                String mobileSignalContentDescriptionId, int dataTypeIconId,
                boolean activityIn, boolean activityOut,
                String dataTypeContentDescriptionId, String description, boolean hasService);
        void onAirplaneModeChanged(boolean enabled);
        /*void onAirplaneModeChanged2(boolean enabled);*/
    }

    public class SimNetworkController {
        // telephony
        final TelephonyManager mPhone;
        boolean mDataConnected;
        IccCardConstants.State mSimState = IccCardConstants.State.READY;
        int mPhoneState = TelephonyManager.CALL_STATE_IDLE;
        int mDataNetType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        int mDataState = TelephonyManager.DATA_DISCONNECTED;
        int mDataActivity = TelephonyManager.DATA_ACTIVITY_NONE;
        ServiceState mServiceState;
        SignalStrength mSignalStrength;
        int[] mDataIconList;// = TelephonyIcons.DATA_G[0]; //move after mPhone decided
        String mNetworkNameDefault;
        String mNetworkName;
        int mPhoneSignalIconId;
        int mQSPhoneSignalIconId;
        int mDataDirectionIconId; // data + data direction on phones
        int mDataSignalIconId;
        int mDataTypeIconId;
        int mQSDataTypeIconId;
        int mAirplaneIconId;
        boolean mDataActive;
        int mMobileActivityIconId; // overlay arrows for data direction
        int mSimAbsentIconId;
        int mLastSignalLevel;

        int mLastSimAbsentIconId;

        String mContentDescriptionPhoneSignal;
        String mContentDescriptionDataType;

        int mLastPhoneSignalIconId = -1;
        int mLastDataDirectionIconId = -1;
        int mLastDataTypeIconId = -1;

        private boolean mHasMobileDataFeature;

        private boolean isDataSimAbsentNotified = false;
        private boolean isVoiceSimAbsentNotified = false;

        public SimNetworkController(Context context, SimCardID simCardId) {
            final Resources res = context.getResources();  //make it common?
            ConnectivityManager cm = (ConnectivityManager)mContext.getSystemService(
                    Context.CONNECTIVITY_SERVICE);    //make it common?

            if (SimCardID.ID_ZERO == simCardId) {
                    mHasMobileDataFeature = cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE);
                // telephony
                mPhone = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
                mPhone.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE
                        | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                        | PhoneStateListener.LISTEN_CALL_STATE
                        | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                        | PhoneStateListener.LISTEN_DATA_ACTIVITY);
            } else {  /*if (SimCardID.ID_ONE == simCardId)*/
                mHasMobileDataFeature = cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE);
                // telephony
                mPhone = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE2);
                mPhone.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE
                        | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                        | PhoneStateListener.LISTEN_CALL_STATE
                        | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                        | PhoneStateListener.LISTEN_DATA_ACTIVITY);
            }

            mNetworkNameDefault = context.getString(
                    com.android.internal.R.string.lockscreen_carrier_default);
            mNetworkName = mNetworkNameDefault;

            mDataIconList = TelephonyIcons_brcm.DATA_G[mPhone.getSimCardId().toInt()][0];
        }

        public boolean hasMobileDataFeature() {
            return mHasMobileDataFeature;
        }

        public boolean hasVoiceCallingFeature() {
            return mPhone.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE;
        }

        // ===== Telephony ==============================================================

        PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
            @Override
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                if (DEBUG) {
                    Log.d(TAG, "onSignalStrengthsChanged signalStrength=" + signalStrength +
                        ((signalStrength == null) ? "" : (" level=" + signalStrength.getLevel())));
                }
                mSignalStrength = signalStrength;
                updateTelephonySignalStrength();
                refreshViews();
            }

            @Override
            public void onServiceStateChanged(ServiceState state) {
                if (DEBUG) {
                    Log.d(TAG, "onServiceStateChanged voiceState=" + state.getVoiceRegState()
                            + " dataState=" + state.getDataRegState());
                }
                mServiceState = state;
                updateTelephonySignalStrength();
                updateDataNetType();
                updateDataIcon();
                refreshViews();
            }

            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                if (DEBUG) {
                    Log.d(TAG, "onCallStateChanged state=" + state);
                }
                // In cdma, if a voice call is made, RSSI should switch to 1x.
                if (isCdma()) {
                    updateTelephonySignalStrength();
                    refreshViews();
                }
            }

            @Override
            public void onDataConnectionStateChanged(int state, int networkType) {
                if (DEBUG) {
                    Log.d(TAG, "onDataConnectionStateChanged: state=" + state
                            + " type=" + networkType);
                }
                mDataState = state;

                if (TelephonyManager.DATA_UNKNOWN == mDataState
                        || TelephonyManager.DATA_DISCONNECTED == mDataState) {
                    mDataNetType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
                } else {
                    mDataNetType = networkType;
                }

                updateDataNetType();
                updateDataIcon();
                refreshViews();
            }

            @Override
            public void onDataActivity(int direction) {
                if (DEBUG) {
                    Log.d(TAG, "onDataActivity: direction=" + direction);
                }
                mDataActivity = direction;
                updateDataIcon();
                refreshViews();
            }
        };

        private final void updateSimState(Intent intent) {
            String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
            mSimAbsentIconId = 0;
            if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
                mSimState = IccCardConstants.State.ABSENT;
                mSimAbsentIconId = R.drawable.stat_sys_no_sim;
            }
            else if (IccCardConstants.INTENT_VALUE_ICC_READY.equals(stateExtra)) {
                mSimState = IccCardConstants.State.READY;
            }
            else if (IccCardConstants.INTENT_VALUE_ICC_LOCKED.equals(stateExtra)) {
                final String lockedReason = intent.getStringExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON);
                if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PIN.equals(lockedReason)) {
                    mSimState = IccCardConstants.State.PIN_REQUIRED;
                }
                else if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PUK.equals(lockedReason)) {
                    mSimState = IccCardConstants.State.PUK_REQUIRED;
                }
                else {
                    mSimState = IccCardConstants.State.NETWORK_LOCKED;
                }
            } else {
                mSimState = IccCardConstants.State.UNKNOWN;
            }
        }

        private boolean isCdma() {
            return (mSignalStrength != null) && !mSignalStrength.isGsm();
        }

        private boolean hasService() {
            if (mServiceState != null) {
                // Consider the device to be in service if either voice or data service is available.
                // Some SIM cards are marketed as data-only and do not support voice service, and on
                // these SIM cards, we want to show signal bars for data service as well as the "no
                // service" or "emergency calls only" text that indicates that voice is not available.
                switch(mServiceState.getVoiceRegState()) {
                    case ServiceState.STATE_POWER_OFF:
                        return false;
                    case ServiceState.STATE_OUT_OF_SERVICE:
                    case ServiceState.STATE_EMERGENCY_ONLY:
                        return mServiceState.getDataRegState() == ServiceState.STATE_IN_SERVICE;
                    default:
                        return true;
                }
            } else {
                return false;
            }
        }

        private final void updateTelephonySignalStrength() {
            if (!hasService()) {
                if (CHATTY) Log.d(TAG, "updateTelephonySignalStrength: !hasService()");
                mPhoneSignalIconId = (mPhone.getSimCardId() == SimCardID.ID_ZERO) ?
                        R.drawable.stat_sys_signal_null :
                        R.drawable.stat_sys_signal_null_1;
                mQSPhoneSignalIconId = (mPhone.getSimCardId() == SimCardID.ID_ZERO) ?
                        R.drawable.ic_qs_signal_no_signal :
                        R.drawable.ic_qs_signal_no_signal_1;
                mDataSignalIconId = (mPhone.getSimCardId() == SimCardID.ID_ZERO) ?
                        R.drawable.stat_sys_signal_null :
                        R.drawable.stat_sys_signal_null_1;
            } else {
                if (mSignalStrength == null) {
                    if (CHATTY) Log.d(TAG, "updateTelephonySignalStrength: mSignalStrength == null");
                    mPhoneSignalIconId = (mPhone.getSimCardId() == SimCardID.ID_ZERO) ?
                            R.drawable.stat_sys_signal_null :
                            R.drawable.stat_sys_signal_null_1;
                    mQSPhoneSignalIconId =  (mPhone.getSimCardId() == SimCardID.ID_ZERO) ?
                            R.drawable.ic_qs_signal_no_signal:
                            R.drawable.ic_qs_signal_no_signal_1;
                    mDataSignalIconId = (mPhone.getSimCardId() == SimCardID.ID_ZERO) ?
                            R.drawable.stat_sys_signal_null :
                            R.drawable.stat_sys_signal_null_1;
                    mContentDescriptionPhoneSignal = mContext.getString(
                            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[0]);
                } else {
                    int iconLevel;
                    int[] iconList;
                    if (isCdma() && mAlwaysShowCdmaRssi) {
                        mLastSignalLevel = iconLevel = mSignalStrength.getCdmaLevel();
                        if(DEBUG) Log.d(TAG, "mAlwaysShowCdmaRssi=" + mAlwaysShowCdmaRssi
                                + " set to cdmaLevel=" + mSignalStrength.getCdmaLevel()
                                + " instead of level=" + mSignalStrength.getLevel());
                    } else {
                        mLastSignalLevel = iconLevel = mSignalStrength.getLevel();
                    }

                    if (isCdma()) {
                        if (isCdmaEri()) {
                            iconList = TelephonyIcons_brcm.TELEPHONY_SIGNAL_STRENGTH_ROAMING[mPhone.getSimCardId().toInt()][mInetCondition];
                        } else {
                            iconList = TelephonyIcons_brcm.TELEPHONY_SIGNAL_STRENGTH[mPhone.getSimCardId().toInt()][mInetCondition];
                        }
                    } else {
                        // Though mPhone is a Manager, this call is not an IPC
                        if (mPhone.isNetworkRoaming()) {
                            iconList = TelephonyIcons_brcm.TELEPHONY_SIGNAL_STRENGTH_ROAMING[mPhone.getSimCardId().toInt()][mInetCondition];
                        } else {
                            iconList = TelephonyIcons_brcm.TELEPHONY_SIGNAL_STRENGTH[mPhone.getSimCardId().toInt()][mInetCondition];
                        }
                    }
                    mPhoneSignalIconId = iconList[iconLevel];
                    mQSPhoneSignalIconId =
                        TelephonyIcons_brcm.QS_TELEPHONY_SIGNAL_STRENGTH[mPhone.getSimCardId().toInt()][mInetCondition][iconLevel];
                    mContentDescriptionPhoneSignal = mContext.getString(
                            AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[iconLevel]);
                    mDataSignalIconId = TelephonyIcons_brcm.DATA_SIGNAL_STRENGTH[mPhone.getSimCardId().toInt()][mInetCondition][iconLevel];
                }
            }
        }

        private final void updateDataNetType() {
            if (mIsWimaxEnabled && mWimaxConnected) {
                // wimax is a special 4g network not handled by telephony
                mDataIconList = TelephonyIcons_brcm.DATA_4G[mPhone.getSimCardId().toInt()][mInetCondition];
                mDataTypeIconId = (mPhone.getSimCardId() == SimCardID.ID_ZERO) ?
                        R.drawable.stat_sys_data_fully_connected_4g :
                        R.drawable.stat_sys_data_connected_4g_1;
                mQSDataTypeIconId = TelephonyIcons_brcm.QS_DATA_4G[mPhone.getSimCardId().toInt()][mInetCondition];
                mContentDescriptionDataType = mContext.getString(
                        R.string.accessibility_data_connection_4g);
            } else {
                switch (mDataNetType) {
                    case TelephonyManager.NETWORK_TYPE_UNKNOWN:
                        if (!mShowAtLeastThreeGees) {
                            mDataIconList = TelephonyIcons_brcm.DATA_G[mPhone.getSimCardId().toInt()][mInetCondition];
                            mDataTypeIconId = 0;
                            mQSDataTypeIconId = 0;
                            mContentDescriptionDataType = mContext.getString(
                                    R.string.accessibility_data_connection_gprs);
                            break;
                        } else {
                            // fall through
                        }
                    case TelephonyManager.NETWORK_TYPE_EDGE:
                        if (!mShowAtLeastThreeGees) {
                            mDataIconList = TelephonyIcons_brcm.DATA_E[mPhone.getSimCardId().toInt()][mInetCondition];
                            mDataTypeIconId = (mPhone.getSimCardId() == SimCardID.ID_ZERO) ?
                                    R.drawable.stat_sys_data_fully_connected_e :
                                    R.drawable.stat_sys_data_connected_e_1;
                            mQSDataTypeIconId = TelephonyIcons_brcm.QS_DATA_E[mPhone.getSimCardId().toInt()][mInetCondition];
                            mContentDescriptionDataType = mContext.getString(
                                    R.string.accessibility_data_connection_edge);
                            break;
                        } else {
                            // fall through
                        }
                    case TelephonyManager.NETWORK_TYPE_UMTS:
                        mDataIconList = TelephonyIcons_brcm.DATA_3G[mPhone.getSimCardId().toInt()][mInetCondition];
                        mDataTypeIconId = (mPhone.getSimCardId() == SimCardID.ID_ZERO) ?
                                R.drawable.stat_sys_data_fully_connected_3g :
                                R.drawable.stat_sys_data_connected_3g_1;
                        mQSDataTypeIconId = TelephonyIcons_brcm.QS_DATA_3G[mPhone.getSimCardId().toInt()][mInetCondition];
                        mContentDescriptionDataType = mContext.getString(
                                R.string.accessibility_data_connection_3g);
                        break;
                    case TelephonyManager.NETWORK_TYPE_HSDPA:
                    case TelephonyManager.NETWORK_TYPE_HSUPA:
                    case TelephonyManager.NETWORK_TYPE_HSPA:
                    case TelephonyManager.NETWORK_TYPE_HSPAP:
                        if (mHspaDataDistinguishable) {
                            mDataIconList = TelephonyIcons_brcm.DATA_H[mPhone.getSimCardId().toInt()][mInetCondition];
                            mDataTypeIconId = (mPhone.getSimCardId() == SimCardID.ID_ZERO) ?
                                    R.drawable.stat_sys_data_fully_connected_h :
                                    R.drawable.stat_sys_data_connected_h_1;
                            mQSDataTypeIconId = TelephonyIcons_brcm.QS_DATA_H[mPhone.getSimCardId().toInt()][mInetCondition];
                            mContentDescriptionDataType = mContext.getString(
                                    R.string.accessibility_data_connection_3_5g);
                        } else {
                            mDataIconList = TelephonyIcons_brcm.DATA_3G[mPhone.getSimCardId().toInt()][mInetCondition];
                            mDataTypeIconId = (mPhone.getSimCardId() == SimCardID.ID_ZERO) ?
                                    R.drawable.stat_sys_data_fully_connected_3g :
                                    R.drawable.stat_sys_data_connected_3g_1;
                            mQSDataTypeIconId = TelephonyIcons_brcm.QS_DATA_3G[mPhone.getSimCardId().toInt()][mInetCondition];
                            mContentDescriptionDataType = mContext.getString(
                                    R.string.accessibility_data_connection_3g);
                        }
                        break;
                    case TelephonyManager.NETWORK_TYPE_CDMA:
                        if (!mShowAtLeastThreeGees) {
                            // display 1xRTT for IS95A/B
                            mDataIconList = TelephonyIcons_brcm.DATA_1X[mPhone.getSimCardId().toInt()][mInetCondition];
                            mDataTypeIconId = (mPhone.getSimCardId() == SimCardID.ID_ZERO) ?
                                    R.drawable.stat_sys_data_fully_connected_1x :
                                    R.drawable.stat_sys_data_connected_1x_1;
                            mQSDataTypeIconId = TelephonyIcons_brcm.QS_DATA_1X[mPhone.getSimCardId().toInt()][mInetCondition];
                            mContentDescriptionDataType = mContext.getString(
                                    R.string.accessibility_data_connection_cdma);
                            break;
                        } else {
                            // fall through
                        }
                    case TelephonyManager.NETWORK_TYPE_1xRTT:
                        if (!mShowAtLeastThreeGees) {
                            mDataIconList = TelephonyIcons_brcm.DATA_1X[mPhone.getSimCardId().toInt()][mInetCondition];
                            mDataTypeIconId = (mPhone.getSimCardId() == SimCardID.ID_ZERO) ?
                                    R.drawable.stat_sys_data_fully_connected_1x :
                                    R.drawable.stat_sys_data_connected_1x_1;
                            mQSDataTypeIconId = TelephonyIcons_brcm.QS_DATA_1X[mPhone.getSimCardId().toInt()][mInetCondition];
                            mContentDescriptionDataType = mContext.getString(
                                    R.string.accessibility_data_connection_cdma);
                            break;
                        } else {
                            // fall through
                        }
                    case TelephonyManager.NETWORK_TYPE_EVDO_0: //fall through
                    case TelephonyManager.NETWORK_TYPE_EVDO_A:
                    case TelephonyManager.NETWORK_TYPE_EVDO_B:
                    case TelephonyManager.NETWORK_TYPE_EHRPD:
                        mDataIconList = TelephonyIcons_brcm.DATA_3G[mPhone.getSimCardId().toInt()][mInetCondition];
                        mDataTypeIconId = (mPhone.getSimCardId() == SimCardID.ID_ZERO) ?
                                R.drawable.stat_sys_data_fully_connected_3g :
                                R.drawable.stat_sys_data_connected_3g_1;
                        mQSDataTypeIconId = TelephonyIcons_brcm.QS_DATA_3G[mPhone.getSimCardId().toInt()][mInetCondition];
                        mContentDescriptionDataType = mContext.getString(
                                R.string.accessibility_data_connection_3g);
                        break;
                    case TelephonyManager.NETWORK_TYPE_LTE:
                        boolean show4GforLTE = mContext.getResources().getBoolean(R.bool.config_show4GForLTE);
                        if (show4GforLTE) {
                            mDataIconList = TelephonyIcons_brcm.DATA_4G[mPhone.getSimCardId().toInt()][mInetCondition];
                            mDataTypeIconId = (mPhone.getSimCardId() == SimCardID.ID_ZERO) ?
                                    R.drawable.stat_sys_data_fully_connected_4g :
                                    R.drawable.stat_sys_data_connected_4g_1;
                            mQSDataTypeIconId = TelephonyIcons_brcm.QS_DATA_4G[mPhone.getSimCardId().toInt()][mInetCondition];
                            mContentDescriptionDataType = mContext.getString(
                                    R.string.accessibility_data_connection_4g);
                        } else {
                            mDataIconList = TelephonyIcons_brcm.DATA_LTE[mPhone.getSimCardId().toInt()][mInetCondition];
                            mDataTypeIconId = (mPhone.getSimCardId() == SimCardID.ID_ZERO) ?
                                    R.drawable.stat_sys_data_fully_connected_lte :
                                    R.drawable.stat_sys_data_fully_connected_lte;
                            mQSDataTypeIconId = TelephonyIcons_brcm.QS_DATA_LTE[mPhone.getSimCardId().toInt()][mInetCondition];
                            mContentDescriptionDataType = mContext.getString(
                                    R.string.accessibility_data_connection_lte);
                        }
                        break;
                    default:
                        if (!mShowAtLeastThreeGees) {
                            mDataIconList = TelephonyIcons_brcm.DATA_G[mPhone.getSimCardId().toInt()][mInetCondition];
                            mDataTypeIconId = (mPhone.getSimCardId() == SimCardID.ID_ZERO) ?
                                    R.drawable.stat_sys_data_fully_connected_g :
                                    R.drawable.stat_sys_data_fully_connected_g_1;
                            mQSDataTypeIconId = TelephonyIcons_brcm.QS_DATA_G[mPhone.getSimCardId().toInt()][mInetCondition];
                            mContentDescriptionDataType = mContext.getString(
                                    R.string.accessibility_data_connection_gprs);
                        } else {
                            mDataIconList = TelephonyIcons_brcm.DATA_3G[mPhone.getSimCardId().toInt()][mInetCondition];
                            mDataTypeIconId = (mPhone.getSimCardId() == SimCardID.ID_ZERO) ?
                                    R.drawable.stat_sys_data_fully_connected_3g :
                                    R.drawable.stat_sys_data_fully_connected_3g_1;
                            mQSDataTypeIconId = TelephonyIcons_brcm.QS_DATA_3G[mPhone.getSimCardId().toInt()][mInetCondition];
                            mContentDescriptionDataType = mContext.getString(
                                    R.string.accessibility_data_connection_3g);
                        }
                        break;
                }
            }

            if (isCdma()) {
                if (isCdmaEri()) {
                    mDataTypeIconId = (mPhone.getSimCardId() == SimCardID.ID_ZERO) ?
                            R.drawable.stat_sys_data_fully_connected_roam :
                            R.drawable.stat_sys_data_fully_connected_roam;
                   mQSDataTypeIconId = TelephonyIcons_brcm.QS_DATA_R[mPhone.getSimCardId().toInt()][mInetCondition];
                }
            } else if (mPhone.isNetworkRoaming()) {
                    mDataTypeIconId = (mPhone.getSimCardId() == SimCardID.ID_ZERO) ?
                            R.drawable.stat_sys_data_fully_connected_roam :
                            R.drawable.stat_sys_data_fully_connected_roam;
                   mQSDataTypeIconId = TelephonyIcons_brcm.QS_DATA_R[mPhone.getSimCardId().toInt()][mInetCondition];
            }
        }

        boolean isCdmaEri() {
            if (mServiceState != null) {
                final int iconIndex = mServiceState.getCdmaEriIconIndex();
                if (iconIndex != EriInfo.ROAMING_INDICATOR_OFF) {
                    final int iconMode = mServiceState.getCdmaEriIconMode();
                    if (iconMode == EriInfo.ROAMING_ICON_MODE_NORMAL
                            || iconMode == EriInfo.ROAMING_ICON_MODE_FLASH) {
                        return true;
                    }
                }
            }
            return false;
        }

        private final void updateDataIcon() {
            int iconId;
            boolean visible = true;

            if (!isCdma()) {
                // GSM case, we have to check also the sim state
                if (mSimState == IccCardConstants.State.READY || mSimState == IccCardConstants.State.UNKNOWN) {
                    if (hasService() && mDataState == TelephonyManager.DATA_CONNECTED) {
                        switch (mDataActivity) {
                            case TelephonyManager.DATA_ACTIVITY_IN:
                                iconId = mDataIconList[1];
                                break;
                            case TelephonyManager.DATA_ACTIVITY_OUT:
                                iconId = mDataIconList[2];
                                break;
                            case TelephonyManager.DATA_ACTIVITY_INOUT:
                                iconId = mDataIconList[3];
                                break;
                            default:
                                iconId = mDataIconList[0];
                                break;
                        }
                        mDataDirectionIconId = iconId;
                    } else {
                        iconId = 0;
                        visible = false;
                    }
                } else { //No SIM Card case, mSimState = IccCardConstants.State.ABSENT
                    iconId = R.drawable.stat_sys_no_sim;
                    visible = false; // no SIM? no data

                    /* If the preferred SIM for data connection did not exist,
                     * we should send the notification on the status bar to inform user */
                    if((mSimState == IccCardConstants.State.ABSENT)
                        &&(Integer.valueOf(SystemProperties.get(PROPERTY_DATA_PREFER_SIM_ID,String.valueOf(ID_ZERO.toInt()))) == mPhone.getSimCardId().toInt())
                        && !isDataSimAbsentNotified){
                        Notification myNotification = new Notification();
                        Intent notifyIntent = new Intent(Intent.ACTION_MAIN);
                        notifyIntent.setClassName("com.android.phone",
                            "com.android.phone.Settings");
                        notifyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        PendingIntent appIntent = PendingIntent.getActivity(mContext, 0, notifyIntent, 0);
                        myNotification.icon = android.R.drawable.stat_notify_error;
                        myNotification.tickerText = mContext.getString(R.string.noPreferredDataSIM_title);
                        myNotification.defaults = Notification.DEFAULT_SOUND;
                        myNotification.flags |= Notification.FLAG_AUTO_CANCEL;
                        myNotification.setLatestEventInfo(mContext,
                            mContext.getString(R.string.noPreferredDataSIM_title),
                            mContext.getString(R.string.noPreferredDataSIM_content),appIntent);
                        Log.d(TAG,"updateSimState()->updateDataIcon(), send the no preferred data SIM notification");
                        isDataSimAbsentNotified = true;//Only notify user one time
                        mNotificationMgr.notify(0,myNotification);
                    }

                    /* If the preferred SIM for Voice call did not exist,
                     * we should send the notification on the status bar to inform user */
/* vdang:fixme - to be restored
                    int defaultVoiceCallSim = Settings.Global.getInt(mContext.getContentResolver(),
                            Settings.Global.DEFAULT_VOICE_CALL_MODE, DEFAULT_MODE_BOTH_OR_NONE);
*/
                    if((mSimState == IccCardConstants.State.ABSENT)
/* vdang:fixme - to be removed */
                        &&(SystemProperties.getInt(PROPERTY_CALL_DEFAULT_SIM_ID,SimCardID.ID_PROMPT.toInt()) == mPhone.getSimCardId().toInt())
/* vdang:fixme - to be restored
                        &&(defaultVoiceCallSim == mPhone.getSimCardId().toInt())
*/
                        && !isVoiceSimAbsentNotified){
                        Notification myNotification2 = new Notification();
                        Intent notifyIntent = new Intent(Intent.ACTION_MAIN);
                        notifyIntent.setClassName("com.android.settings",
                            "com.android.settings.CallSettings");
                        notifyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        PendingIntent appIntent = PendingIntent.getActivity(mContext, 0, notifyIntent, 0);
                        myNotification2.icon = android.R.drawable.stat_notify_error;
                        myNotification2.tickerText = mContext.getString(R.string.noPreferredVoiceCallSIM_title);
                        myNotification2.defaults = Notification.DEFAULT_SOUND;
                        myNotification2.flags |= Notification.FLAG_AUTO_CANCEL;
                        myNotification2.setLatestEventInfo(mContext,
                            mContext.getString(R.string.noPreferredVoiceCallSIM_title),
                            mContext.getString(R.string.noPreferredVoiceCallSIM_content),appIntent);
                        isVoiceSimAbsentNotified = true;//Only notify user one time
                        mNotificationMgr.notify(1,myNotification2);
                    }
                }
            } else {
                // CDMA case, mDataActivity can be also DATA_ACTIVITY_DORMANT
                if (hasService() && mDataState == TelephonyManager.DATA_CONNECTED) {
                    switch (mDataActivity) {
                        case TelephonyManager.DATA_ACTIVITY_IN:
                            iconId = mDataIconList[1];
                            break;
                        case TelephonyManager.DATA_ACTIVITY_OUT:
                            iconId = mDataIconList[2];
                            break;
                        case TelephonyManager.DATA_ACTIVITY_INOUT:
                            iconId = mDataIconList[3];
                            break;
                        case TelephonyManager.DATA_ACTIVITY_DORMANT:
                        default:
                            iconId = mDataIconList[0];
                            break;
                    }
                } else {
                    iconId = 0;
                    visible = false;
                }
            }

            mDataDirectionIconId = iconId;
            mDataConnected = visible;
        }

        void updateNetworkName(boolean showSpn, String spn, boolean showPlmn, String plmn) {
            if (false) {
                Log.d("CarrierLabel", "updateNetworkName showSpn=" + showSpn + " spn=" + spn
                        + " showPlmn=" + showPlmn + " plmn=" + plmn);
            }
            StringBuilder str = new StringBuilder();
            boolean something = false;
            if (showPlmn && plmn != null) {
                str.append(plmn);
                something = true;
            }
            if (showSpn && spn != null) {
                if (something) {
                    str.append(mNetworkNameSeparator);
                }
                str.append(spn);
                something = true;
            }
            if (something) {
                mNetworkName = str.toString();
            } else {
                mNetworkName = mNetworkNameDefault;
            }
        }
    } //End: SimNetworkController

    private SimNetworkController mSim[] = {null, null};

    /**
     * Construct this controller object and register for updates.
     */
    public NetworkController_brcm(Context context) {
        mContext = context;

        mSim[SimCardID.ID_ZERO.toInt()] = new SimNetworkController(context, SimCardID.ID_ZERO);
        mSim[SimCardID.ID_ONE.toInt()] = new SimNetworkController(context, SimCardID.ID_ONE);

        // set up the default wifi icon, used when no radios have ever appeared
        updateWifiIcons();
        updateWimaxIcons();

        mHspaDataDistinguishable = mContext.getResources().getBoolean(
                R.bool.config_hspa_data_distinguishable);
        mShowPhoneRSSIForData = mContext.getResources().getBoolean(R.bool.config_showPhoneRSSIForData);
        mShowAtLeastThreeGees = mContext.getResources().getBoolean(R.bool.config_showMin3G);
        mAlwaysShowCdmaRssi = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_alwaysUseCdmaRssi);
        mNetworkNameSeparator = mContext.getString(R.string.status_bar_network_name_separator);

        mNotificationMgr = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        // wifi
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        Handler handler = new WifiHandler();
        mWifiChannel = new AsyncChannel();
        Messenger wifiMessenger = mWifiManager.getWifiServiceMessenger();
        if (wifiMessenger != null) {
            mWifiChannel.connect(mContext, handler, wifiMessenger);
        }

        // broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        filter.addAction(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(ConnectivityManager.INET_CONDITION_ACTION);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        mWimaxSupported = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_wimaxEnabled);
        if(mWimaxSupported) {
            filter.addAction(WimaxManagerConstants.WIMAX_NETWORK_STATE_CHANGED_ACTION);
            filter.addAction(WimaxManagerConstants.SIGNAL_LEVEL_CHANGED_ACTION);
            filter.addAction(WimaxManagerConstants.NET_4G_STATE_CHANGED_ACTION);
        }
        context.registerReceiver(this, filter);

        // AIRPLANE_MODE_CHANGED is sent at boot; we've probably already missed it
        updateAirplaneMode();

        mLastLocale = mContext.getResources().getConfiguration().locale;
    }

    public boolean isEmergencyOnly() {
        return ( mSim[ID_ZERO.toInt()].mServiceState != null &&
                 mSim[ID_ZERO.toInt()].mServiceState.isEmergencyOnly() &&
                 mSim[ID_ONE.toInt()].mServiceState != null &&
                 mSim[ID_ONE.toInt()].mServiceState.isEmergencyOnly() );
    }

    public boolean isEmergencyOnly(SimCardID simCardId) {
        return ( mSim[simCardId.toInt()].mServiceState != null &&
                 mSim[simCardId.toInt()].mServiceState.isEmergencyOnly() );
    }


    public void addCombinedLabelView(TextView v) {
        mCombinedLabelViews.add(v);
    }

    public void addMobileLabelView(TextView v) {
        mMobileLabelViews.add(v);
    }

    public void addWifiLabelView(TextView v) {
        mWifiLabelViews.add(v);
    }

    public void addEmergencyLabelView(TextView v) {
        mEmergencyLabelViews.add(v);
    }

    public void addSignalCluster(SignalCluster cluster) {
        mSignalClusters.add(cluster);
        refreshSignalCluster(cluster);
    }

    public void addNetworkSignalChangedCallback(NetworkSignalChangedCallback cb) {
        mSignalsChangedCallbacks.add(cb);
        notifySignalsChangedCallbacks(cb);
    }

    public void refreshSignalCluster(SignalCluster cluster) {
        cluster.setWifiIndicators(
                // only show wifi in the cluster if connected or if wifi-only
                mWifiEnabled && (mWifiConnected || (!mSim[SimCardID.ID_ZERO.toInt()].hasMobileDataFeature() && !mSim[SimCardID.ID_ONE.toInt()].hasMobileDataFeature())),
                mWifiIconId,
                mContentDescriptionWifi);

        if (mIsWimaxEnabled && mWimaxConnected) {
            // wimax is special
            cluster.setMobileDataIndicators1(
                    true,
                    mAlwaysShowCdmaRssi ? mSim[ID_ZERO.toInt()].mPhoneSignalIconId : mWimaxIconId,
                    mSim[ID_ZERO.toInt()].mDataTypeIconId,
                    mSim[ID_ZERO.toInt()].mSimAbsentIconId,
                    mContentDescriptionWimax,
                    mSim[ID_ZERO.toInt()].mContentDescriptionDataType);
        } else {
            // normal mobile data
            cluster.setMobileDataIndicators1(
                    mSim[ID_ZERO.toInt()].hasMobileDataFeature(),
                    mShowPhoneRSSIForData ? mSim[ID_ZERO.toInt()].mPhoneSignalIconId : mSim[ID_ZERO.toInt()].mDataSignalIconId,
                    mSim[ID_ZERO.toInt()].mDataTypeIconId,
                    mSim[ID_ZERO.toInt()].mSimAbsentIconId,
                    mSim[ID_ZERO.toInt()].mContentDescriptionPhoneSignal,
                    mSim[ID_ZERO.toInt()].mContentDescriptionDataType);
            cluster.setMobileDataIndicators2(
                    mSim[ID_ONE.toInt()].hasMobileDataFeature(),
                    mShowPhoneRSSIForData ? mSim[ID_ONE.toInt()].mPhoneSignalIconId : mSim[ID_ONE.toInt()].mDataSignalIconId,
                    mSim[ID_ONE.toInt()].mDataTypeIconId,
                    mSim[ID_ONE.toInt()].mSimAbsentIconId,
                    mSim[ID_ONE.toInt()].mContentDescriptionPhoneSignal,
                    mSim[ID_ONE.toInt()].mContentDescriptionDataType);
        }
        cluster.setIsAirplaneMode(mAirplaneMode, mSim[ID_ZERO.toInt()].mAirplaneIconId);
        if (mSim[ID_ONE.toInt()].mServiceState != null) {
            cluster.setIsAirplaneMode2(mAirplaneMode, mSim[ID_ONE.toInt()].mAirplaneIconId);
        }
    }

    void notifySignalsChangedCallbacks(NetworkSignalChangedCallback cb) {
        // only show wifi in the cluster if connected or if wifi-only
        boolean wifiEnabled = mWifiEnabled && (mWifiConnected || !hasMobileDataFeature());
        String wifiDesc = wifiEnabled ?
                mWifiSsid : null;

        boolean wifiIn = wifiEnabled && mWifiSsid != null
                && (mWifiActivity == WifiManager.DATA_ACTIVITY_INOUT
                || mWifiActivity == WifiManager.DATA_ACTIVITY_IN);
        boolean wifiOut = wifiEnabled && mWifiSsid != null
                && (mWifiActivity == WifiManager.DATA_ACTIVITY_INOUT
                || mWifiActivity == WifiManager.DATA_ACTIVITY_OUT);

        cb.onWifiSignalChanged(wifiEnabled, mQSWifiIconId, wifiIn, wifiOut, mContentDescriptionWifi, wifiDesc);

        boolean mobileIn = (mSim[ID_ZERO.toInt()].mDataConnected || mSim[ID_ONE.toInt()].mDataConnected ) &&
               ( (mSim[ID_ZERO.toInt()].mDataActivity == TelephonyManager.DATA_ACTIVITY_INOUT ||
                  mSim[ID_ONE.toInt()].mDataActivity == TelephonyManager.DATA_ACTIVITY_IN ) ||
                 (mSim[ID_ZERO.toInt()].mDataActivity == TelephonyManager.DATA_ACTIVITY_INOUT ||
                  mSim[ID_ONE.toInt()].mDataActivity == TelephonyManager.DATA_ACTIVITY_IN )
               );

        boolean mobileOut = (mSim[ID_ZERO.toInt()].mDataConnected || mSim[ID_ONE.toInt()].mDataConnected ) &&
                 ( (mSim[ID_ZERO.toInt()].mDataActivity == TelephonyManager.DATA_ACTIVITY_INOUT ||
                    mSim[ID_ONE.toInt()].mDataActivity == TelephonyManager.DATA_ACTIVITY_OUT) ||
                (mSim[ID_ZERO.toInt()].mDataActivity == TelephonyManager.DATA_ACTIVITY_INOUT ||
                mSim[ID_ONE.toInt()].mDataActivity == TelephonyManager.DATA_ACTIVITY_OUT)
                 );

        if (isEmergencyOnly()) {
            cb.onMobileDataSignalChanged(false, mSim[ID_ZERO.toInt()].mQSPhoneSignalIconId,
                    mSim[ID_ZERO.toInt()].mContentDescriptionPhoneSignal, mSim[ID_ZERO.toInt()].mQSDataTypeIconId, mobileIn, mobileOut,
                    mSim[ID_ZERO.toInt()].mContentDescriptionDataType,null);
            cb.onMobileDataSignalChanged2(false, mSim[ID_ONE.toInt()].mQSPhoneSignalIconId,
                    mSim[ID_ONE.toInt()].mContentDescriptionPhoneSignal, mSim[ID_ONE.toInt()].mQSDataTypeIconId,  mobileIn, mobileOut,
                    mSim[ID_ONE.toInt()].mContentDescriptionDataType,null, (mSim[ID_ONE.toInt()].mServiceState != null));
        } else {
            if (mIsWimaxEnabled && mWimaxConnected) {
                // Wimax is special
                cb.onMobileDataSignalChanged(true, mSim[ID_ZERO.toInt()].mQSPhoneSignalIconId,
                        mSim[ID_ZERO.toInt()].mContentDescriptionPhoneSignal, mSim[ID_ZERO.toInt()].mQSDataTypeIconId, mobileIn, mobileOut,
                        mSim[ID_ZERO.toInt()].mContentDescriptionDataType, mSim[ID_ZERO.toInt()].mNetworkName);
            } else {
                // Normal mobile data
                cb.onMobileDataSignalChanged(mSim[ID_ZERO.toInt()].mHasMobileDataFeature, mSim[ID_ZERO.toInt()].mQSPhoneSignalIconId,
                        mSim[ID_ZERO.toInt()].mContentDescriptionPhoneSignal, mSim[ID_ZERO.toInt()].mQSDataTypeIconId, mobileIn, mobileOut,
                        mSim[ID_ZERO.toInt()].mContentDescriptionDataType, mSim[ID_ZERO.toInt()].mNetworkName);
                cb.onMobileDataSignalChanged2(mSim[ID_ONE.toInt()].mHasMobileDataFeature, mSim[ID_ONE.toInt()].mQSPhoneSignalIconId,
                        mSim[ID_ONE.toInt()].mContentDescriptionPhoneSignal, mSim[ID_ONE.toInt()].mQSDataTypeIconId, mobileIn, mobileOut,
                        mSim[ID_ONE.toInt()].mContentDescriptionDataType, mSim[ID_ONE.toInt()].mNetworkName, (mSim[ID_ONE.toInt()].mServiceState != null));
            }
        }
        cb.onAirplaneModeChanged(mAirplaneMode);
        /*if (mSim[ID_ONE.toInt()].mServiceState != null) {
            cb.onAirplaneModeChanged2(mAirplaneMode);
        }*/
    }

    public void setStackedMode(boolean stacked) {
        mDataAndWifiStacked = true;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action.equals(WifiManager.RSSI_CHANGED_ACTION)
                || action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)
                || action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
            updateWifiState(intent);
            refreshViews();
        } else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
            SimCardID simCardId = (SimCardID)(intent.getExtra("simId", ID_ZERO));
            mSim[simCardId.toInt()].updateSimState(intent);
            mSim[simCardId.toInt()].updateDataIcon();
            refreshViews();
        } else if (action.equals(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION)) {
            SimCardID simCardId = (SimCardID)(intent.getExtra("simId", ID_ZERO));
            mSim[simCardId.toInt()].updateNetworkName(intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, false),
            intent.getStringExtra(TelephonyIntents.EXTRA_SPN),
            intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_PLMN, false),
            intent.getStringExtra(TelephonyIntents.EXTRA_PLMN));
            refreshViews();
        } else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION) ||
                 action.equals(ConnectivityManager.INET_CONDITION_ACTION)) {
            updateConnectivity(intent);
            refreshViews();
        } else if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
            refreshLocale();
            refreshViews();
        } else if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
            refreshLocale();
            updateAirplaneMode();
            refreshViews();
        } else if (action.equals(WimaxManagerConstants.NET_4G_STATE_CHANGED_ACTION) ||
                action.equals(WimaxManagerConstants.SIGNAL_LEVEL_CHANGED_ACTION) ||
                action.equals(WimaxManagerConstants.WIMAX_NETWORK_STATE_CHANGED_ACTION)) {
            updateWimaxState(intent);
            refreshViews();
        }
    }

    // ===== Telephony ==============================================================
    public boolean hasMobileDataFeature() {
        return (mSim[ID_ZERO.toInt()].hasMobileDataFeature()
                || mSim[ID_ONE.toInt()].hasMobileDataFeature());
    }

    public boolean hasVoiceCallingFeature() {
        return (mSim[ID_ZERO.toInt()].hasVoiceCallingFeature()
                || mSim[ID_ONE.toInt()].hasVoiceCallingFeature());
    }

    private void updateAirplaneMode() {
        mAirplaneMode = (Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) == 1);
    }

    private void refreshLocale() {
        mLocale = mContext.getResources().getConfiguration().locale;
    }

    // ===== Wifi ===================================================================

    class WifiHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AsyncChannel.CMD_CHANNEL_HALF_CONNECTED:
                    if (msg.arg1 == AsyncChannel.STATUS_SUCCESSFUL) {
                        mWifiChannel.sendMessage(Message.obtain(this,
                                AsyncChannel.CMD_CHANNEL_FULL_CONNECTION));
                    } else {
                        Log.e(TAG, "Failed to connect to wifi");
                    }
                    break;
                case WifiManager.DATA_ACTIVITY_NOTIFICATION:
                    if (msg.arg1 != mWifiActivity) {
                        mWifiActivity = msg.arg1;
                        refreshViews();
                    }
                    break;
                default:
                    //Ignore
                    break;
            }
        }
    }

    private void updateWifiState(Intent intent) {
        final String action = intent.getAction();
        if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
            mWifiEnabled = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                    WifiManager.WIFI_STATE_UNKNOWN) == WifiManager.WIFI_STATE_ENABLED;

        } else if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
            final NetworkInfo networkInfo = (NetworkInfo)
                    intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            boolean wasConnected = mWifiConnected;
            mWifiConnected = networkInfo != null && networkInfo.isConnected();
            // If we just connected, grab the inintial signal strength and ssid
            if (mWifiConnected && !wasConnected) {
                // try getting it out of the intent first
                WifiInfo info = (WifiInfo) intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
                if (info == null) {
                    info = mWifiManager.getConnectionInfo();
                }
                if (info != null) {
                    mWifiSsid = huntForSsid(info);
                } else {
                    mWifiSsid = null;
                }
            } else if (!mWifiConnected) {
                mWifiSsid = null;
            }
        } else if (action.equals(WifiManager.RSSI_CHANGED_ACTION)) {
            mWifiRssi = intent.getIntExtra(WifiManager.EXTRA_NEW_RSSI, -200);
            mWifiLevel = WifiManager.calculateSignalLevel(
                    mWifiRssi, WifiIcons.WIFI_LEVEL_COUNT);
        }

        updateWifiIcons();
    }

    private void updateWifiIcons() {
        if (mWifiConnected) {
            mWifiIconId = WifiIcons.WIFI_SIGNAL_STRENGTH[mInetCondition][mWifiLevel];
            mQSWifiIconId = WifiIcons.QS_WIFI_SIGNAL_STRENGTH[mInetCondition][mWifiLevel];
            mContentDescriptionWifi = mContext.getString(
                    AccessibilityContentDescriptions.WIFI_CONNECTION_STRENGTH[mWifiLevel]);
        } else {
            if (mDataAndWifiStacked) {
                mWifiIconId = 0;
                mQSWifiIconId = 0;
            } else {
                mWifiIconId = mWifiEnabled ? R.drawable.stat_sys_wifi_signal_null : 0;
                mQSWifiIconId = mWifiEnabled ? R.drawable.ic_qs_wifi_no_network : 0;
            }
            mContentDescriptionWifi = mContext.getString(R.string.accessibility_no_wifi);
        }
    }

    private String huntForSsid(WifiInfo info) {
        String ssid = info.getSSID();
        if (ssid != null) {
            return ssid;
        }
        // OK, it's not in the connectionInfo; we have to go hunting for it
        List<WifiConfiguration> networks = mWifiManager.getConfiguredNetworks();
        for (WifiConfiguration net : networks) {
            if (net.networkId == info.getNetworkId()) {
                return net.SSID;
            }
        }
        return null;
    }


    // ===== Wimax ===================================================================
    private final void updateWimaxState(Intent intent) {
        final String action = intent.getAction();
        boolean wasConnected = mWimaxConnected;
        if (action.equals(WimaxManagerConstants.NET_4G_STATE_CHANGED_ACTION)) {
            int wimaxStatus = intent.getIntExtra(WimaxManagerConstants.EXTRA_4G_STATE,
                    WimaxManagerConstants.NET_4G_STATE_UNKNOWN);
            mIsWimaxEnabled = (wimaxStatus ==
                    WimaxManagerConstants.NET_4G_STATE_ENABLED);
        } else if (action.equals(WimaxManagerConstants.SIGNAL_LEVEL_CHANGED_ACTION)) {
            mWimaxSignal = intent.getIntExtra(WimaxManagerConstants.EXTRA_NEW_SIGNAL_LEVEL, 0);
        } else if (action.equals(WimaxManagerConstants.WIMAX_NETWORK_STATE_CHANGED_ACTION)) {
            mWimaxState = intent.getIntExtra(WimaxManagerConstants.EXTRA_WIMAX_STATE,
                    WimaxManagerConstants.NET_4G_STATE_UNKNOWN);
            mWimaxExtraState = intent.getIntExtra(
                    WimaxManagerConstants.EXTRA_WIMAX_STATE_DETAIL,
                    WimaxManagerConstants.NET_4G_STATE_UNKNOWN);
            mWimaxConnected = (mWimaxState ==
                    WimaxManagerConstants.WIMAX_STATE_CONNECTED);
            mWimaxIdle = (mWimaxExtraState == WimaxManagerConstants.WIMAX_IDLE);
        }

        mSim[ID_ZERO.toInt()].updateDataNetType();
        updateWimaxIcons();
    }

    private void updateWimaxIcons() {
        if (mIsWimaxEnabled) {
            if (mWimaxConnected) {
                if (mWimaxIdle)
                    mWimaxIconId = WimaxIcons.WIMAX_IDLE;
                else
                    mWimaxIconId = WimaxIcons.WIMAX_SIGNAL_STRENGTH[mInetCondition][mWimaxSignal];
                mContentDescriptionWimax = mContext.getString(
                        AccessibilityContentDescriptions.WIMAX_CONNECTION_STRENGTH[mWimaxSignal]);
            } else {
                mWimaxIconId = WimaxIcons.WIMAX_DISCONNECTED;
                mContentDescriptionWimax = mContext.getString(R.string.accessibility_no_wimax);
            }
        } else {
            mWimaxIconId = 0;
        }
    }

    // ===== Full or limited Internet connectivity ==================================

    private void updateConnectivity(Intent intent) {
        if (CHATTY) {
            Log.d(TAG, "updateConnectivity: intent=" + intent);
        }

        final ConnectivityManager connManager = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo info = connManager.getActiveNetworkInfo();

        // Are we connected at all, by any interface?
        mConnected = info != null && info.isConnected();
        if (mConnected) {
            mConnectedNetworkType = info.getType();
            mConnectedNetworkTypeName = info.getTypeName();
        } else {
            mConnectedNetworkType = ConnectivityManager.TYPE_NONE;
            mConnectedNetworkTypeName = null;
        }

        int connectionStatus = intent.getIntExtra(ConnectivityManager.EXTRA_INET_CONDITION, 0);

        if (CHATTY) {
            Log.d(TAG, "updateConnectivity: networkInfo=" + info);
            Log.d(TAG, "updateConnectivity: connectionStatus=" + connectionStatus);
        }

        mInetCondition = (connectionStatus > INET_CONDITION_THRESHOLD ? 1 : 0);

        if (info != null && info.getType() == ConnectivityManager.TYPE_BLUETOOTH) {
            mBluetoothTethered = info.isConnected();
        } else {
            mBluetoothTethered = false;
        }

        // We want to update all the icons, all at once, for any condition change
        if (TelephonyManager.DATA_CONNECTED == mSim[ID_ZERO.toInt()].mDataState) {
            mSim[ID_ZERO.toInt()].updateDataNetType();
            mSim[ID_ZERO.toInt()].updateDataIcon();
        } else if (TelephonyManager.DATA_CONNECTED == mSim[ID_ONE.toInt()].mDataState) {
            mSim[ID_ONE.toInt()].updateDataNetType();
            mSim[ID_ONE.toInt()].updateDataIcon();
        }

        if (mSim[ID_ZERO.toInt()].mServiceState != null) {
            mSim[ID_ZERO.toInt()].updateTelephonySignalStrength();
        }
        if (mSim[ID_ONE.toInt()].mServiceState != null) {
            mSim[ID_ONE.toInt()].updateTelephonySignalStrength();
        }

        updateWimaxIcons();
        updateWifiIcons();
    }

    // ===== Update the views =======================================================

    void refreshViews() {
        Context context = mContext;

        int combinedSignalIconId = 0;
        String combinedLabel = "";
        String wifiLabel = "";
        String mobileLabel[] = {"",""};
        String mobileLabel_show = "";
        int N;
        final boolean emergencyOnly = isEmergencyOnly();

        if (!mSim[ID_ZERO.toInt()].hasMobileDataFeature()) {
            mSim[ID_ZERO.toInt()].mDataSignalIconId = mSim[ID_ZERO.toInt()].mPhoneSignalIconId = 0;
            mSim[ID_ZERO.toInt()].mQSPhoneSignalIconId = 0;
            mobileLabel[ID_ZERO.toInt()] = "";
        } else {
            // We want to show the carrier name if in service and either:
            //   - We are connected to mobile data, or
            //   - We are not connected to mobile data, as long as the *reason* packets are not
            //     being routed over that link is that we have better connectivity via wifi.
            // If data is disconnected for some other reason but wifi (or ethernet/bluetooth)
            // is connected, we show nothing.
            // Otherwise (nothing connected) we show "No internet connection".

            if (mSim[ID_ZERO.toInt()].mDataConnected) {
                mobileLabel[ID_ZERO.toInt()] = mSim[ID_ZERO.toInt()].mNetworkName;
            } else if (mConnected || isEmergencyOnly(SimCardID.ID_ZERO)) {
                if (mSim[ID_ZERO.toInt()].hasService() || isEmergencyOnly(SimCardID.ID_ZERO)) {
                    // The isEmergencyOnly test covers the case of a phone with no SIM
                    mobileLabel[ID_ZERO.toInt()] = mSim[ID_ZERO.toInt()].mNetworkName;
                } else {
                    // Tablets, basically
                    mobileLabel[ID_ZERO.toInt()] = "";
                }
            } else {
                mobileLabel[ID_ZERO.toInt()]
                    = context.getString(R.string.status_bar_settings_signal_meter_disconnected);
            }

            // Now for things that should only be shown when actually using mobile data.
            if (mSim[ID_ZERO.toInt()].mDataConnected) {
                combinedSignalIconId = mSim[ID_ZERO.toInt()].mDataSignalIconId;
                combinedLabel = mobileLabel[ID_ZERO.toInt()];
                combinedSignalIconId = mSim[ID_ZERO.toInt()].mDataSignalIconId; // set by updateDataIcon()
                mContentDescriptionCombinedSignal = mSim[ID_ZERO.toInt()].mContentDescriptionDataType;
            }
        }

        if (!mSim[ID_ONE.toInt()].hasMobileDataFeature()) {
            mSim[ID_ONE.toInt()].mDataSignalIconId = mSim[ID_ONE.toInt()].mPhoneSignalIconId = 0;
            mobileLabel[ID_ONE.toInt()] = "";
        } else {
            // We want to show the carrier name if in service and either:
            //   - We are connected to mobile data, or
            //   - We are not connected to mobile data, as long as the *reason* packets are not
            //     being routed over that link is that we have better connectivity via wifi.
            // If data is disconnected for some other reason but wifi (or ethernet/bluetooth)
            // is connected, we show nothing.
            // Otherwise (nothing connected) we show "No internet connection".

            if (mSim[ID_ONE.toInt()].mDataConnected) {
                mobileLabel[ID_ONE.toInt()] = mSim[ID_ONE.toInt()].mNetworkName;
            } else if (mConnected || isEmergencyOnly(SimCardID.ID_ONE)) {
                if (mSim[ID_ONE.toInt()].hasService() || isEmergencyOnly(SimCardID.ID_ONE)) {
                    // The isEmergencyOnly test covers the case of a phone with no SIM
                    mobileLabel[ID_ONE.toInt()] = mSim[ID_ONE.toInt()].mNetworkName;
                } else {
                    // Tablets, basically
                    mobileLabel[ID_ONE.toInt()] = "";
                }
            } else {
                mobileLabel[ID_ONE.toInt()]
                    = context.getString(R.string.status_bar_settings_signal_meter_disconnected);
            }

            // Now for things that should only be shown when actually using mobile data.
            if (mSim[ID_ONE.toInt()].mDataConnected) {
                combinedSignalIconId = mSim[ID_ONE.toInt()].mDataSignalIconId;
                combinedLabel = mobileLabel[ID_ONE.toInt()];
                combinedSignalIconId = mSim[ID_ONE.toInt()].mDataSignalIconId; // set by updateDataIcon()
                mContentDescriptionCombinedSignal = mSim[ID_ONE.toInt()].mContentDescriptionDataType;
            }
        }

        if (mWifiConnected) {
            if (mWifiSsid == null) {
                wifiLabel = context.getString(R.string.status_bar_settings_signal_meter_wifi_nossid);
            } else {
                wifiLabel = mWifiSsid;
                if (DEBUG) {
                    wifiLabel += "xxxxXXXXxxxxXXXX";
                }
            }

            combinedLabel = wifiLabel;
            combinedSignalIconId = mWifiIconId; // set by updateWifiIcons()
            mContentDescriptionCombinedSignal = mContentDescriptionWifi;

        } else {
            if (mSim[ID_ZERO.toInt()].hasMobileDataFeature() || mSim[ID_ONE.toInt()].hasMobileDataFeature()) {
                wifiLabel = "";
            } else {
                wifiLabel = context.getString(R.string.status_bar_settings_signal_meter_disconnected);
            }
        }

        if (mBluetoothTethered) {
            combinedLabel = mContext.getString(R.string.bluetooth_tethered);
            combinedSignalIconId = mBluetoothTetherIconId;
            mContentDescriptionCombinedSignal = mContext.getString(
                    R.string.accessibility_bluetooth_tether);
        }

        final boolean ethernetConnected = (mConnectedNetworkType == ConnectivityManager.TYPE_ETHERNET);
        if (ethernetConnected) {
            combinedLabel = context.getString(R.string.ethernet_label);
        }

        if (mAirplaneMode &&
                (mSim[ID_ZERO.toInt()].mServiceState == null
                        || (!mSim[ID_ZERO.toInt()].hasService() && !mSim[ID_ZERO.toInt()].mServiceState.isEmergencyOnly()))
                && (mSim[ID_ONE.toInt()].mServiceState == null
                        || (!mSim[ID_ONE.toInt()].hasService() && !mSim[ID_ONE.toInt()].mServiceState.isEmergencyOnly()))) {
            // Only display the flight-mode icon if not in "emergency calls only" mode.

            // look again; your radios are now airplanes
            mSim[ID_ZERO.toInt()].mContentDescriptionPhoneSignal = mContext.getString(
                    R.string.accessibility_airplane_mode);
            mSim[ID_ZERO.toInt()].mAirplaneIconId = FLIGHT_MODE_ICON;
            mSim[ID_ZERO.toInt()].mPhoneSignalIconId = mSim[ID_ZERO.toInt()].mDataSignalIconId = mSim[ID_ZERO.toInt()].mDataTypeIconId = mSim[ID_ZERO.toInt()].mQSDataTypeIconId = 0;
            mSim[ID_ZERO.toInt()].mQSPhoneSignalIconId = 0;

            mSim[ID_ONE.toInt()].mContentDescriptionPhoneSignal = mContext.getString(
                    R.string.accessibility_airplane_mode);
            mSim[ID_ONE.toInt()].mAirplaneIconId = R.drawable.stat_sys_signal_flightmode_1;
            mSim[ID_ONE.toInt()].mPhoneSignalIconId = mSim[ID_ONE.toInt()].mDataSignalIconId = mSim[ID_ONE.toInt()].mDataTypeIconId = mSim[ID_ONE.toInt()].mQSDataTypeIconId = 0;
            mSim[ID_ONE.toInt()].mQSPhoneSignalIconId = 0;

            // combined values from connected wifi take precedence over airplane mode
            if (mWifiConnected) {
                // Suppress "No internet connection." from mobile if wifi connected.
                mobileLabel[ID_ZERO.toInt()] = "";
                mobileLabel[ID_ONE.toInt()] = "";
            } else {
                if (mSim[ID_ZERO.toInt()].mHasMobileDataFeature || mSim[ID_ONE.toInt()].mHasMobileDataFeature) {
                    // let the mobile icon show "No internet connection."
                    wifiLabel = "";
                } else {
                    wifiLabel = context.getString(R.string.status_bar_settings_signal_meter_disconnected);
                    combinedLabel = wifiLabel;
                }
                mContentDescriptionCombinedSignal = mSim[ID_ZERO.toInt()].mDataConnected ?
                        mSim[ID_ZERO.toInt()].mContentDescriptionPhoneSignal
                        : mSim[ID_ONE.toInt()].mContentDescriptionPhoneSignal; //TODO: DUAL PHYSICAL MODEM
                combinedSignalIconId = mSim[ID_ZERO.toInt()].mDataConnected ?
                        mSim[ID_ZERO.toInt()].mDataSignalIconId
                        :mSim[ID_ONE.toInt()].mDataSignalIconId;  //TODO: DUAL PHYSICAL MODEM
            }
        }
        else if (!mSim[ID_ZERO.toInt()].mDataConnected && !mSim[ID_ONE.toInt()].mDataConnected && !mWifiConnected && !mBluetoothTethered && !mWimaxConnected && !ethernetConnected) {
            // pretty much totally disconnected

            combinedLabel = context.getString(R.string.status_bar_settings_signal_meter_disconnected);
            // On devices without mobile radios, we want to show the wifi icon
            combinedSignalIconId =
                    (mSim[ID_ZERO.toInt()].hasMobileDataFeature() || mSim[ID_ONE.toInt()].hasMobileDataFeature()) ? mSim[ID_ZERO.toInt()].mDataSignalIconId : mWifiIconId;  //TODO: ID_ONE?
            mContentDescriptionCombinedSignal = (mSim[ID_ZERO.toInt()].hasMobileDataFeature() || mSim[ID_ONE.toInt()].hasMobileDataFeature())
                ? mSim[ID_ZERO.toInt()].mContentDescriptionDataType : mContentDescriptionWifi;  //TODO: ID_ONE?

            mSim[ID_ZERO.toInt()].mDataTypeIconId = 0;
            mSim[ID_ONE.toInt()].mDataTypeIconId = 0;
            mSim[ID_ZERO.toInt()].mQSDataTypeIconId = 0;
            mSim[ID_ONE.toInt()].mQSDataTypeIconId = 0;

            if (mSim[ID_ZERO.toInt()].isCdma()) {
                if (mSim[ID_ZERO.toInt()].isCdmaEri()) {
                    mSim[ID_ZERO.toInt()].mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_roam;
            mSim[ID_ZERO.toInt()].mQSDataTypeIconId = TelephonyIcons_brcm.QS_DATA_R[ID_ZERO.toInt()][mInetCondition];
                }
            } else if (mSim[ID_ZERO.toInt()].mPhone.isNetworkRoaming()) {
                mSim[ID_ZERO.toInt()].mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_roam;
        mSim[ID_ZERO.toInt()].mQSDataTypeIconId = TelephonyIcons_brcm.QS_DATA_R[ID_ZERO.toInt()][mInetCondition];
            }

            if (mSim[ID_ONE.toInt()].isCdma()) {
                if (mSim[ID_ONE.toInt()].isCdmaEri()) {
                    mSim[ID_ONE.toInt()].mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_roam;
            mSim[ID_ONE.toInt()].mQSDataTypeIconId = TelephonyIcons_brcm.QS_DATA_R[ID_ONE.toInt()][mInetCondition];
                }
            } else if (mSim[ID_ONE.toInt()].mPhone.isNetworkRoaming()) {
                mSim[ID_ONE.toInt()].mDataTypeIconId = R.drawable.stat_sys_data_fully_connected_roam;
        mSim[ID_ONE.toInt()].mQSDataTypeIconId = TelephonyIcons_brcm.QS_DATA_R[ID_ONE.toInt()][mInetCondition];
            }
        }

        if (DEBUG) {
            Log.d(TAG, "refreshViews connected={"
                    + (mWifiConnected?" wifi":"")
                    + (mSim[ID_ZERO.toInt()].mDataConnected?" data":"")
                    + (mSim[ID_ONE.toInt()].mDataConnected?" data2":"")
                    + " } level="
                    + ((mSim[ID_ZERO.toInt()].mSignalStrength == null)?"??":Integer.toString(mSim[ID_ZERO.toInt()].mSignalStrength.getLevel()))
                    + " } level2="
                    + ((mSim[ID_ONE.toInt()].mSignalStrength == null)?"??":Integer.toString(mSim[ID_ONE.toInt()].mSignalStrength.getLevel()))
                    + " combinedSignalIconId=0x"
                    + Integer.toHexString(combinedSignalIconId)
                    + "/" + getResourceName(combinedSignalIconId)
                    + " mobileLabel[0]=" + mobileLabel[ID_ZERO.toInt()]
                    + " mobileLabel[1]=" + mobileLabel[ID_ONE.toInt()]
                    + " wifiLabel=" + wifiLabel
                    + " SIM1 emergencyOnly=" + isEmergencyOnly(SimCardID.ID_ZERO)
                    + " SIM2 emergencyOnly=" + isEmergencyOnly(SimCardID.ID_ONE)
                    + " combinedLabel=" + combinedLabel
                    + " mAirplaneMode=" + mAirplaneMode
                    + " mDataActivity=" + mSim[ID_ZERO.toInt()].mDataActivity
                    + " mDataActivity2=" + mSim[ID_ONE.toInt()].mDataActivity
                    + " mPhoneSignalIconId=0x" + Integer.toHexString(mSim[ID_ZERO.toInt()].mPhoneSignalIconId)
                    + " mPhoneSignalIconId2=0x" + Integer.toHexString(mSim[ID_ONE.toInt()].mPhoneSignalIconId)
                    + " mQSPhoneSignalIconId=0x" + Integer.toHexString(mSim[ID_ZERO.toInt()].mQSPhoneSignalIconId)
                    + " mQSPhoneSignalIconId2=0x" + Integer.toHexString(mSim[ID_ONE.toInt()].mQSPhoneSignalIconId)
                    + " mDataDirectionIconId=0x" + Integer.toHexString(mSim[ID_ZERO.toInt()].mDataDirectionIconId)
                    + " mDataDirectionIconId2=0x" + Integer.toHexString(mSim[ID_ONE.toInt()].mDataDirectionIconId)
                    + " mDataSignalIconId=0x" + Integer.toHexString(mSim[ID_ZERO.toInt()].mDataSignalIconId)
                    + " mDataSignalIconId2=0x" + Integer.toHexString(mSim[ID_ONE.toInt()].mDataSignalIconId)
                    + " mDataTypeIconId=0x" + Integer.toHexString(mSim[ID_ZERO.toInt()].mDataTypeIconId)
                    + " mDataTypeIconId2=0x" + Integer.toHexString(mSim[ID_ONE.toInt()].mDataTypeIconId)
                    + " mQSDataTypeIconId=0x" + Integer.toHexString(mSim[ID_ZERO.toInt()].mQSDataTypeIconId)
                    + " mQSDataTypeIconId2=0x" + Integer.toHexString(mSim[ID_ONE.toInt()].mQSDataTypeIconId)
                    + " mSimAbsentIconId1=0x" + Integer.toHexString(mSim[ID_ZERO.toInt()].mSimAbsentIconId)
                    + " mSimAbsentIconId2=0x" + Integer.toHexString(mSim[ID_ONE.toInt()].mSimAbsentIconId)
                    + " mWifiIconId=0x" + Integer.toHexString(mWifiIconId)
                    + " mQSWifiIconId=0x" + Integer.toHexString(mQSWifiIconId)
                    + " mBluetoothTetherIconId=0x" + Integer.toHexString(mBluetoothTetherIconId));
        }

        // update QS
        for (NetworkSignalChangedCallback cb : mSignalsChangedCallbacks) {
            notifySignalsChangedCallbacks(cb);
        }

        if (mSim[ID_ZERO.toInt()].mLastPhoneSignalIconId          != mSim[ID_ZERO.toInt()].mPhoneSignalIconId
         || mSim[ID_ONE.toInt()].mLastPhoneSignalIconId           != mSim[ID_ONE.toInt()].mPhoneSignalIconId
         || mLastWifiIconId                                       != mWifiIconId
         || mLastWimaxIconId                                      != mWimaxIconId
         || mSim[ID_ZERO.toInt()].mLastDataTypeIconId             != mSim[ID_ZERO.toInt()].mDataTypeIconId
         || mSim[ID_ONE.toInt()].mLastDataTypeIconId              != mSim[ID_ONE.toInt()].mDataTypeIconId
         || mSim[ID_ZERO.toInt()].mLastSimAbsentIconId            != mSim[ID_ZERO.toInt()].mSimAbsentIconId
         || mSim[ID_ONE.toInt()].mLastSimAbsentIconId             != mSim[ID_ONE.toInt()].mSimAbsentIconId
         || mLastAirplaneMode                                     != mAirplaneMode
         || mLastLocale                                              != mLocale)
        {
            // NB: the mLast*s will be updated later
            for (SignalCluster cluster : mSignalClusters) {
                refreshSignalCluster(cluster);
            }
            for (NetworkSignalChangedCallback cb : mSignalsChangedCallbacks) {
                notifySignalsChangedCallbacks(cb);
            }
        }

        if (mLastAirplaneMode != mAirplaneMode) {
            mLastAirplaneMode = mAirplaneMode;
        }

        if (mLastLocale != mLocale) {
            mLastLocale = mLocale;
        }

        // the phone icon on phones
        if (mSim[ID_ZERO.toInt()].mLastPhoneSignalIconId != mSim[ID_ZERO.toInt()].mPhoneSignalIconId) {
            mSim[ID_ZERO.toInt()].mLastPhoneSignalIconId = mSim[ID_ZERO.toInt()].mPhoneSignalIconId;
        }

        // the phone icon on phones2
        if (mSim[ID_ONE.toInt()].mLastPhoneSignalIconId != mSim[ID_ONE.toInt()].mPhoneSignalIconId) {
            mSim[ID_ONE.toInt()].mLastPhoneSignalIconId = mSim[ID_ONE.toInt()].mPhoneSignalIconId;
        }

        // the sim absent icon on phones
        if (mSim[ID_ZERO.toInt()].mLastSimAbsentIconId != mSim[ID_ZERO.toInt()].mSimAbsentIconId) {
            mSim[ID_ZERO.toInt()].mLastSimAbsentIconId = mSim[ID_ZERO.toInt()].mSimAbsentIconId;
        }

        // the sim absent icon on phones 2
        if (mSim[ID_ONE.toInt()].mLastSimAbsentIconId != mSim[ID_ONE.toInt()].mSimAbsentIconId) {
            mSim[ID_ONE.toInt()].mLastSimAbsentIconId = mSim[ID_ONE.toInt()].mSimAbsentIconId;
        }

        // the data icon on phones
        if (mSim[ID_ZERO.toInt()].mLastDataDirectionIconId != mSim[ID_ZERO.toInt()].mDataDirectionIconId) {
            mSim[ID_ZERO.toInt()].mLastDataDirectionIconId = mSim[ID_ZERO.toInt()].mDataDirectionIconId;
        }

        // the data icon on phones2
        if (mSim[ID_ONE.toInt()].mLastDataDirectionIconId != mSim[ID_ONE.toInt()].mDataDirectionIconId) {
            mSim[ID_ONE.toInt()].mLastDataDirectionIconId = mSim[ID_ONE.toInt()].mDataDirectionIconId;
        }

        // the wifi icon on phones
        if (mLastWifiIconId != mWifiIconId) {
            mLastWifiIconId = mWifiIconId;
        }

        // the wimax icon on phones
        if (mLastWimaxIconId != mWimaxIconId) {
            mLastWimaxIconId = mWimaxIconId;
        }
        // the combined data signal icon
        if (mLastCombinedSignalIconId != combinedSignalIconId) {
            mLastCombinedSignalIconId = combinedSignalIconId;
        }

        // the data network type overlay
        if (mSim[ID_ZERO.toInt()].mLastDataTypeIconId != mSim[ID_ZERO.toInt()].mDataTypeIconId) {
            mSim[ID_ZERO.toInt()].mLastDataTypeIconId = mSim[ID_ZERO.toInt()].mDataTypeIconId;
        }

        // the data network type overlay2
        if (mSim[ID_ONE.toInt()].mLastDataTypeIconId != mSim[ID_ONE.toInt()].mDataTypeIconId) {
            mSim[ID_ONE.toInt()].mLastDataTypeIconId = mSim[ID_ONE.toInt()].mDataTypeIconId;
        }


        // the combinedLabel in the notification panel
        if (!mLastCombinedLabel.equals(combinedLabel)) {
            mLastCombinedLabel = combinedLabel;
            N = mCombinedLabelViews.size();
            for (int i=0; i<N; i++) {
                TextView v = mCombinedLabelViews.get(i);
                v.setText(combinedLabel);
            }
        }

        // wifi label
        N = mWifiLabelViews.size();
        for (int i=0; i<N; i++) {
            TextView v = mWifiLabelViews.get(i);
            v.setText(wifiLabel);
            if ("".equals(wifiLabel)) {
                v.setVisibility(View.GONE);
            } else {
                v.setVisibility(View.VISIBLE);
            }
        }

        //Make the mobileLabel_show label
        if(context.getString(R.string.status_bar_settings_signal_meter_disconnected).equals(mobileLabel[ID_ZERO.toInt()]) &&
            context.getString(R.string.status_bar_settings_signal_meter_disconnected).equals(mobileLabel[ID_ONE.toInt()])) {
            mobileLabel_show = context.getString(R.string.status_bar_settings_signal_meter_disconnected);
        } else {
            if(!"".equals(mobileLabel[ID_ZERO.toInt()]) &&
                !context.getString(R.string.status_bar_settings_signal_meter_disconnected).equals(mobileLabel[ID_ZERO.toInt()])) {
                mobileLabel_show = mobileLabel[ID_ZERO.toInt()];
            }

            if(!"".equals(mobileLabel[ID_ONE.toInt()]) &&
                !context.getString(R.string.status_bar_settings_signal_meter_disconnected).equals(mobileLabel[ID_ZERO.toInt()])){
                if("".equals(mobileLabel_show)) {
                    mobileLabel_show = mobileLabel[ID_ONE.toInt()];
                } else {
                    mobileLabel_show += " " + mNetworkNameSeparator + " " + mobileLabel[ID_ONE.toInt()];
                }
            }
        }
        Log.d(TAG, "mobileLabel_show: " + mobileLabel_show);

        // mobile label
        N = mMobileLabelViews.size();
        for (int i=0; i<N; i++) {
            TextView v = mMobileLabelViews.get(i);
            v.setText(mobileLabel_show);
            if ("".equals(mobileLabel_show)) {
                v.setVisibility(View.GONE);
            } else {
                v.setVisibility(View.VISIBLE);
            }
        }

        // e-call label
        N = mEmergencyLabelViews.size();
        for (int i=0; i<N; i++) {
            TextView v = mEmergencyLabelViews.get(i);
            if (!emergencyOnly) {
                v.setVisibility(View.GONE);
            } else {
                v.setText(mobileLabel_show); // comes from the telephony stack
                v.setVisibility(View.VISIBLE);
            }
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("NetworkController state:");
        pw.println(String.format("  %s network type %d (%s)",
                mConnected?"CONNECTED":"DISCONNECTED",
                mConnectedNetworkType, mConnectedNetworkTypeName));
        pw.println("  - telephony ------");
        pw.print("   mSim[ID_ZERO.toInt()].hasVoiceCallingFeature()=");
        pw.println( mSim[ID_ZERO.toInt()].hasVoiceCallingFeature());
        pw.print("  mSim[ID_ZERO.toInt()].hasService()=");
        pw.println(mSim[ID_ZERO.toInt()].hasService());
        pw.print("  mHspaDataDistinguishable=");
        pw.println(mHspaDataDistinguishable);
        pw.print("  mDataConnected=");
        pw.println(mSim[ID_ZERO.toInt()].mDataConnected);
        pw.print("  mSimState=");
        pw.println(mSim[ID_ZERO.toInt()].mSimState);
        pw.print("  mPhoneState=");
        pw.println(mSim[ID_ZERO.toInt()].mPhoneState);
        pw.print("  mDataState=");
        pw.println(mSim[ID_ZERO.toInt()].mDataState);
        pw.print("  mDataActivity=");
        pw.println(mSim[ID_ZERO.toInt()].mDataActivity);
        pw.print("  mDataNetType=");
        pw.print(mSim[ID_ZERO.toInt()].mDataNetType);
        pw.print("/");
        pw.println(TelephonyManager.getNetworkTypeName(mSim[ID_ZERO.toInt()].mDataNetType));
        pw.print("  mServiceState=");
        pw.println(mSim[ID_ZERO.toInt()].mServiceState);
        pw.print("  mSignalStrength=");
        pw.println(mSim[ID_ZERO.toInt()].mSignalStrength);
        pw.print("  mLastSignalLevel=");
        pw.println(mSim[ID_ZERO.toInt()].mLastSignalLevel);
        pw.print("  mNetworkName=");
        pw.println(mSim[ID_ZERO.toInt()].mNetworkName);
        pw.print("  mNetworkNameDefault=");
        pw.println(mSim[ID_ZERO.toInt()].mNetworkNameDefault);
        pw.print("  mNetworkNameSeparator=");
        pw.println(mNetworkNameSeparator.replace("\n","\\n"));
        pw.print("  mPhoneSignalIconId=0x");
        pw.print(Integer.toHexString(mSim[ID_ZERO.toInt()].mPhoneSignalIconId));
        pw.print("/");
        pw.print("  mQSPhoneSignalIconId=0x");
        pw.print(Integer.toHexString(mSim[ID_ZERO.toInt()].mQSPhoneSignalIconId));
        pw.print("/");
        pw.println(getResourceName(mSim[ID_ZERO.toInt()].mPhoneSignalIconId));
        pw.print("  mDataDirectionIconId=");
        pw.print(Integer.toHexString(mSim[ID_ZERO.toInt()].mDataDirectionIconId));
        pw.print("/");
        pw.println(getResourceName(mSim[ID_ZERO.toInt()].mDataDirectionIconId));
        pw.print("  mDataSignalIconId=");
        pw.print(Integer.toHexString(mSim[ID_ZERO.toInt()].mDataSignalIconId));
        pw.print("/");
        pw.println(getResourceName(mSim[ID_ZERO.toInt()].mDataSignalIconId));
        pw.print("  mDataTypeIconId=");
        pw.print(Integer.toHexString(mSim[ID_ZERO.toInt()].mDataTypeIconId));
        pw.print("/");
        pw.println(getResourceName(mSim[ID_ZERO.toInt()].mDataTypeIconId));
        pw.print("  mQSDataTypeIconId=");
        pw.print(Integer.toHexString(mSim[ID_ZERO.toInt()].mQSDataTypeIconId));
        pw.print("/");
        pw.println(getResourceName(mSim[ID_ZERO.toInt()].mQSDataTypeIconId));

        pw.println("  - telephony SIM2------");
        pw.print("   mSim[ID_ONE.toInt()].hasVoiceCallingFeature()=");
        pw.println( mSim[ID_ONE.toInt()].hasVoiceCallingFeature());
        pw.print("  mSim[ID_ONE.toInt()].hasService()=");
        pw.println(mSim[ID_ONE.toInt()].hasService());
        pw.print("  mHspaDataDistinguishable=");
        pw.println(mHspaDataDistinguishable);
        pw.print("  mDataConnected=");
        pw.println(mSim[ID_ONE.toInt()].mDataConnected);
        pw.print("  mSimState=");
        pw.println(mSim[ID_ONE.toInt()].mSimState);
        pw.print("  mPhoneState=");
        pw.println(mSim[ID_ONE.toInt()].mPhoneState);
        pw.print("  mDataState=");
        pw.println(mSim[ID_ONE.toInt()].mDataState);
        pw.print("  mDataActivity=");
        pw.println(mSim[ID_ONE.toInt()].mDataActivity);
        pw.print("  mDataNetType=");
        pw.print(mSim[ID_ONE.toInt()].mDataNetType);
        pw.print("/");
        pw.println(TelephonyManager.getNetworkTypeName(mSim[ID_ONE.toInt()].mDataNetType));
        pw.print("  mServiceState=");
        pw.println(mSim[ID_ONE.toInt()].mServiceState);
        pw.print("  mSignalStrength=");
        pw.println(mSim[ID_ONE.toInt()].mSignalStrength);
        pw.print("  mLastSignalLevel=");
        pw.println(mSim[ID_ONE.toInt()].mLastSignalLevel);
        pw.print("  mNetworkName=");
        pw.println(mSim[ID_ONE.toInt()].mNetworkName);
        pw.print("  mNetworkNameDefault=");
        pw.println(mSim[ID_ONE.toInt()].mNetworkNameDefault);
        pw.print("  mNetworkNameSeparator=");
        pw.println(mNetworkNameSeparator.replace("\n","\\n"));
        pw.print("  mPhoneSignalIconId=0x");
        pw.print(Integer.toHexString(mSim[ID_ONE.toInt()].mPhoneSignalIconId));
        pw.print("/");
        pw.print("  mQSPhoneSignalIconId=0x");
        pw.print(Integer.toHexString(mSim[ID_ONE.toInt()].mQSPhoneSignalIconId));
        pw.print("/");
        pw.println(getResourceName(mSim[ID_ONE.toInt()].mPhoneSignalIconId));
        pw.print("  mDataDirectionIconId=");
        pw.print(Integer.toHexString(mSim[ID_ONE.toInt()].mDataDirectionIconId));
        pw.print("/");
        pw.println(getResourceName(mSim[ID_ONE.toInt()].mDataDirectionIconId));
        pw.print("  mDataSignalIconId=");
        pw.print(Integer.toHexString(mSim[ID_ONE.toInt()].mDataSignalIconId));
        pw.print("/");
        pw.println(getResourceName(mSim[ID_ONE.toInt()].mDataSignalIconId));
        pw.print("  mDataTypeIconId=");
        pw.print(Integer.toHexString(mSim[ID_ONE.toInt()].mDataTypeIconId));
        pw.print("/");
        pw.println(getResourceName(mSim[ID_ONE.toInt()].mDataTypeIconId));
        pw.print("  mQSDataTypeIconId=");
        pw.print(Integer.toHexString(mSim[ID_ONE.toInt()].mQSDataTypeIconId));
        pw.print("/");
        pw.println(getResourceName(mSim[ID_ONE.toInt()].mQSDataTypeIconId));

        pw.println("  - wifi ------");
        pw.print("  mWifiEnabled=");
        pw.println(mWifiEnabled);
        pw.print("  mWifiConnected=");
        pw.println(mWifiConnected);
        pw.print("  mWifiRssi=");
        pw.println(mWifiRssi);
        pw.print("  mWifiLevel=");
        pw.println(mWifiLevel);
        pw.print("  mWifiSsid=");
        pw.println(mWifiSsid);
        pw.println(String.format("  mWifiIconId=0x%08x/%s",
                    mWifiIconId, getResourceName(mWifiIconId)));
        pw.println(String.format("  mQSWifiIconId=0x%08x/%s",
                    mQSWifiIconId, getResourceName(mQSWifiIconId)));
        pw.print("  mWifiActivity=");
        pw.println(mWifiActivity);

        if (mWimaxSupported) {
            pw.println("  - wimax ------");
            pw.print("  mIsWimaxEnabled="); pw.println(mIsWimaxEnabled);
            pw.print("  mWimaxConnected="); pw.println(mWimaxConnected);
            pw.print("  mWimaxIdle="); pw.println(mWimaxIdle);
            pw.println(String.format("  mWimaxIconId=0x%08x/%s",
                        mWimaxIconId, getResourceName(mWimaxIconId)));
            pw.println(String.format("  mWimaxSignal=%d", mWimaxSignal));
            pw.println(String.format("  mWimaxState=%d", mWimaxState));
            pw.println(String.format("  mWimaxExtraState=%d", mWimaxExtraState));
        }

        pw.println("  - Bluetooth ----");
        pw.print("  mBtReverseTethered=");
        pw.println(mBluetoothTethered);

        pw.println("  - connectivity ------");
        pw.print("  mInetCondition=");
        pw.println(mInetCondition);

        pw.println("  - icons ------");
        pw.print("  mLastPhoneSignalIconId=0x");
        pw.print(Integer.toHexString(mSim[ID_ZERO.toInt()].mLastPhoneSignalIconId));
        pw.print("/");
        pw.println(getResourceName(mSim[ID_ZERO.toInt()].mLastPhoneSignalIconId));
        pw.print("  mLastDataDirectionIconId=0x");
        pw.print(Integer.toHexString(mSim[ID_ZERO.toInt()].mLastDataDirectionIconId));
        pw.print("/");
        pw.println(getResourceName(mSim[ID_ZERO.toInt()].mLastDataDirectionIconId));
        pw.print("/");
        pw.print("  mLastWifiIconId=0x");
        pw.print(Integer.toHexString(mLastWifiIconId));
        pw.print("/");
        pw.println(getResourceName(mLastWifiIconId));
        pw.print("  mLastCombinedSignalIconId=0x");
        pw.print(Integer.toHexString(mLastCombinedSignalIconId));
        pw.print("/");
        pw.println(getResourceName(mLastCombinedSignalIconId));
        pw.print("  mLastDataTypeIconId=0x");
        pw.print(Integer.toHexString(mSim[ID_ZERO.toInt()].mLastDataTypeIconId));
        pw.print("/");
        pw.println(getResourceName(mSim[ID_ZERO.toInt()].mLastDataTypeIconId));
        pw.print("  mLastCombinedLabel=");
        pw.print(mLastCombinedLabel);
        pw.println("");
    }

    private String getResourceName(int resId) {
        if (resId != 0) {
            final Resources res = mContext.getResources();
            try {
                return res.getResourceName(resId);
            } catch (android.content.res.Resources.NotFoundException ex) {
                return "(unknown)";
            }
        } else {
            return "(null)";
        }
    }

    private boolean mDemoMode;
    private int mDemoInetCondition;
    private int mDemoWifiLevel;
    private int mDemoDataTypeIconId;
    private int mDemoMobileLevel;

    @Override
    public void dispatchDemoCommand(String command, Bundle args) {
        if (!mDemoMode && command.equals(COMMAND_ENTER)) {
            mDemoMode = true;
            mDemoWifiLevel = mWifiLevel;
            mDemoInetCondition = mInetCondition;



            mDemoDataTypeIconId = mSim[ID_ZERO.toInt()].mDataTypeIconId;
            mDemoMobileLevel = mSim[ID_ZERO.toInt()].mLastSignalLevel;
        } else if (mDemoMode && command.equals(COMMAND_EXIT)) {
            mDemoMode = false;
            for (SignalCluster cluster : mSignalClusters) {
                refreshSignalCluster(cluster);
            }
        } else if (mDemoMode && command.equals(COMMAND_NETWORK)) {
            String airplane = args.getString("airplane");
            if (airplane != null) {
                boolean show = airplane.equals("show");
                for (SignalCluster cluster : mSignalClusters) {
                    cluster.setIsAirplaneMode(show, FLIGHT_MODE_ICON);
                }
            }
            String fully = args.getString("fully");
            if (fully != null) {
                mDemoInetCondition = Boolean.parseBoolean(fully) ? 1 : 0;
            }
            String wifi = args.getString("wifi");
            if (wifi != null) {
                boolean show = wifi.equals("show");
                String level = args.getString("level");
                if (level != null) {
                    mDemoWifiLevel = level.equals("null") ? -1
                            : Math.min(Integer.parseInt(level), WifiIcons.WIFI_LEVEL_COUNT - 1);
                }
                int iconId = mDemoWifiLevel < 0 ? R.drawable.stat_sys_wifi_signal_null
                        : WifiIcons.WIFI_SIGNAL_STRENGTH[mDemoInetCondition][mDemoWifiLevel];
                for (SignalCluster cluster : mSignalClusters) {
                    cluster.setWifiIndicators(
                            show,
                            iconId,
                            "Demo");
                }
            }
            String mobile = args.getString("mobile");
            if (mobile != null) {
                boolean show = mobile.equals("show");
                String datatype = args.getString("datatype");
                if (datatype != null) {
                    mDemoDataTypeIconId =
                            datatype.equals("1x") ? R.drawable.stat_sys_data_fully_connected_1x :
                            datatype.equals("3g") ? R.drawable.stat_sys_data_fully_connected_3g :
                            datatype.equals("4g") ? R.drawable.stat_sys_data_fully_connected_4g :
                            datatype.equals("e") ? R.drawable.stat_sys_data_fully_connected_e :
                            datatype.equals("g") ? R.drawable.stat_sys_data_fully_connected_g :
                            datatype.equals("h") ? R.drawable.stat_sys_data_fully_connected_h :
                            datatype.equals("lte") ? R.drawable.stat_sys_data_fully_connected_lte :
                            datatype.equals("roam")
                                    ? R.drawable.stat_sys_data_fully_connected_roam :
                            0;
                }
                int[][] icons = TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH;
                String level = args.getString("level");
                if (level != null) {
                    mDemoMobileLevel = level.equals("null") ? -1
                            : Math.min(Integer.parseInt(level), icons[0].length - 1);
                }
                int iconId = mDemoMobileLevel < 0 ? R.drawable.stat_sys_signal_null :
                        icons[mDemoInetCondition][mDemoMobileLevel];
                for (SignalCluster cluster : mSignalClusters) {
                    cluster.setMobileDataIndicators1(
                            show,
                            iconId,
                            mDemoDataTypeIconId,
                            mDemoDataTypeIconId,
                            "Demo",
                            "Demo");
                }
            }
        }
    }
}
