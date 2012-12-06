/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo.DetailedState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Slog;

import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.util.AsyncChannel;

import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;

import com.android.internal.telephony.RILConstants.SimCardID;
import android.os.SystemProperties;

/**
 * Track the state of mobile data connectivity. This is done by
 * receiving broadcast intents from the Phone process whenever
 * the state of data connectivity changes.
 *
 * {@hide}
 */
public class MobileDataStateTracker extends BaseNetworkStateTracker {

    private static final String TAG = "MobileDataStateTracker";
    private static final boolean DBG = true;
    private static final boolean VDBG = true;

    private PhoneConstants.DataState mMobileDataState[] = {null, null};

    private ITelephony mPhoneService;

    private String mApnType;
    private NetworkInfo mNetworkInfo[] = {null, null};
    private boolean mTeardownRequested = false;
    private Handler mTarget;
    private Context mContext;
    private LinkProperties mLinkProperties;
    private LinkCapabilities mLinkCapabilities;
    private boolean mPrivateDnsRouteSet = false;
    private boolean mDefaultRouteSet = false;

    // NOTE: these are only kept for debugging output; actual values are
    // maintained in DataConnectionTracker.
    protected boolean mUserDataEnabled = true;
    protected boolean mPolicyDataEnabled = true;

    private AtomicBoolean mIsCaptivePortal = new AtomicBoolean(false);

    private SignalStrength mSignalStrength;

    private SamplingDataTracker mSamplingDataTracker = new SamplingDataTracker();

    private static final int UNKNOWN = LinkQualityInfo.UNKNOWN_INT;
    private Handler mHandler[] = {null, null};
    private AsyncChannel mDataConnectionTrackerAc[] = {null, null};
    private Messenger mMessenger[] = {null, null};
    private int mNetType;
    private int mEnableApnSimId = -1;

    /**
     * Create a new MobileDataStateTracker
     * @param netType the ConnectivityManager network type
     * @param tag the name of this network
     */
    public MobileDataStateTracker(int netType, String tag) {
        mNetType = netType;
        mNetworkInfo[0] = new NetworkInfo(netType,
                TelephonyManager.getDefault(SimCardID.ID_ZERO).getNetworkType(), tag,
                TelephonyManager.getDefault(SimCardID.ID_ZERO).getNetworkTypeName());
        mNetworkInfo[1] = new NetworkInfo(netType,
                TelephonyManager.getDefault(SimCardID.ID_ONE).getNetworkType(), tag,
                TelephonyManager.getDefault(SimCardID.ID_ONE).getNetworkTypeName());
        mApnType = networkTypeToApnType(netType);
    }

    /**
     * Begin monitoring data connectivity.
     *
     * @param context is the current Android context
     * @param target is the Hander to which to return the events.
     */
    public void startMonitoring(Context context, Handler target) {
        mTarget = target;
        mContext = context;

        mHandler[SimCardID.ID_ZERO.toInt()] = new MdstHandler(target.getLooper(), this, SimCardID.ID_ZERO.toInt());
        mHandler[SimCardID.ID_ONE.toInt()] = new MdstHandler(target.getLooper(), this, SimCardID.ID_ONE.toInt());

        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_DATA_CONNECTION_CONNECTED_TO_PROVISIONING_APN);
        filter.addAction(TelephonyIntents.ACTION_DATA_CONNECTION_FAILED);

        mContext.registerReceiver(new MobileDataStateReceiver(), filter);
        mMobileDataState[SimCardID.ID_ZERO.toInt()] = PhoneConstants.DataState.DISCONNECTED;
        mMobileDataState[SimCardID.ID_ONE.toInt()] = PhoneConstants.DataState.DISCONNECTED;

        TelephonyManager tm = (TelephonyManager)mContext.getSystemService(
                Context.TELEPHONY_SERVICE);
        tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
    }

    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            mSignalStrength = signalStrength;
        }
    };

    static class MdstHandler extends Handler {
        private MobileDataStateTracker mMdst;
        private int mSimId;

        MdstHandler(Looper looper, MobileDataStateTracker mdst, int simId) {
            super(looper);
            mMdst = mdst;
            mSimId = simId;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AsyncChannel.CMD_CHANNEL_HALF_CONNECTED:
                    if (msg.arg1 == AsyncChannel.STATUS_SUCCESSFUL) {
                        if (VDBG) {
                            mMdst.log("MdstHandler connected");
                        }
                        mMdst.mDataConnectionTrackerAc[mSimId] = (AsyncChannel) msg.obj;
                    } else {
                        if (VDBG) {
                            mMdst.log("MdstHandler %s NOT connected error=" + msg.arg1);
                        }
                    }
                    break;
                case AsyncChannel.CMD_CHANNEL_DISCONNECTED:
                    if (VDBG) mMdst.log("Disconnected from DataStateTracker");
                    mMdst.mDataConnectionTrackerAc[mSimId] = null;
                    break;
                default: {
                    if (VDBG) mMdst.log("Ignorning unknown message=" + msg);
                    break;
                }
            }
        }
    }

    public boolean isPrivateDnsRouteSet() {
        return mPrivateDnsRouteSet;
    }

    public void privateDnsRouteSet(boolean enabled) {
        mPrivateDnsRouteSet = enabled;
    }

    public NetworkInfo getNetworkInfo() {
        int simId = SystemProperties.getInt(TelephonyProperties.PROPERTY_DATA_PREFER_SIM_ID, SimCardID.ID_ZERO.toInt());
        if (ConnectivityManager.TYPE_MOBILE_MMS == mNetType) {
            int mmsSimId = SystemProperties.getInt(TelephonyProperties.PROPERTY_WORKING_MMS_SIM_ID, SimCardID.ID_PROMPT.toInt());
            if (-1 != mmsSimId) {
                simId = mmsSimId;
            }
        }
        log("getNetworkInfo(): simId = " + simId);
        return mNetworkInfo[simId];
    }

    public NetworkInfo getNetworkInfo(int simId) {
        return mNetworkInfo[simId];
    }

    public boolean isDefaultRouteSet() {
        return mDefaultRouteSet;
    }

    public void defaultRouteSet(boolean enabled) {
        mDefaultRouteSet = enabled;
    }

    /**
     * This is not implemented.
     */
    public void releaseWakeLock() {
    }

    private void updateLinkProperitesAndCapatilities(Intent intent) {
        mLinkProperties = intent.getParcelableExtra(
                PhoneConstants.DATA_LINK_PROPERTIES_KEY);
        if (mLinkProperties == null) {
            loge("CONNECTED event did not supply link properties.");
            mLinkProperties = new LinkProperties();
        }
        mLinkProperties.setMtu(mContext.getResources().getInteger(
                com.android.internal.R.integer.config_mobile_mtu));
        mLinkCapabilities = intent.getParcelableExtra(
                PhoneConstants.DATA_LINK_CAPABILITIES_KEY);
        if (mLinkCapabilities == null) {
            loge("CONNECTED event did not supply link capabilities.");
            mLinkCapabilities = new LinkCapabilities();
        }
    }

    private class MobileDataStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            SimCardID simCardId = SimCardID.ID_ZERO;
            simCardId = (SimCardID)(intent.getExtra("simId", SimCardID.ID_ZERO));

            if (intent.getAction().equals(TelephonyIntents.
                    ACTION_DATA_CONNECTION_CONNECTED_TO_PROVISIONING_APN)) {
                String apnName = intent.getStringExtra(PhoneConstants.DATA_APN_KEY);
                String apnType = intent.getStringExtra(PhoneConstants.DATA_APN_TYPE_KEY);
                if (!TextUtils.equals(mApnType, apnType)) {
                    return;
                }
                if (DBG) {
                    log("Broadcast received: " + intent.getAction() + " apnType=" + apnType
                            + " apnName=" + apnName);
                }

                // Make us in the connecting state until we make a new TYPE_MOBILE_PROVISIONING
                mMobileDataState[simCardId.toInt()] = PhoneConstants.DataState.CONNECTING;
                updateLinkProperitesAndCapatilities(intent);
                mNetworkInfo[simCardId.toInt()].setIsConnectedToProvisioningNetwork(true);

                // Change state to SUSPENDED so setDetailedState
                // sends EVENT_STATE_CHANGED to connectivityService
                setDetailedState(DetailedState.SUSPENDED, "", apnName, simCardId);
            } else if (intent.getAction().equals(TelephonyIntents.
                    ACTION_ANY_DATA_CONNECTION_STATE_CHANGED)) {
                String apnType = intent.getStringExtra(PhoneConstants.DATA_APN_TYPE_KEY);
                if (VDBG) {
                    log(String.format("Broadcast received: ACTION_ANY_DATA_CONNECTION_STATE_CHANGED"
                        + ", mApnType=%s %s received apnType=%s simId = %s", mApnType,
                        TextUtils.equals(apnType, mApnType) ? "==" : "!=", apnType, simCardId.toInt()));
                }
                if (!TextUtils.equals(apnType, mApnType)) {
                    return;
                }
                // Assume this isn't a provisioning network.
                mNetworkInfo[simCardId.toInt()].setIsConnectedToProvisioningNetwork(false);
                if (DBG) {
                    log("Broadcast received: " + intent.getAction() + " apnType=" + apnType);
                }

                int oldSubtype = mNetworkInfo[simCardId.toInt()].getSubtype();
                int newSubType = TelephonyManager.getDefault(simCardId).getNetworkType();
                String subTypeName = TelephonyManager.getDefault(simCardId).getNetworkTypeName();
                mNetworkInfo[simCardId.toInt()].setSubtype(newSubType, subTypeName);
                if (newSubType != oldSubtype && mNetworkInfo[simCardId.toInt()].isConnected()) {
                    Message msg = mTarget.obtainMessage(EVENT_NETWORK_SUBTYPE_CHANGED,
                                                        oldSubtype, 0, mNetworkInfo[simCardId.toInt()]);
                    msg.sendToTarget();
                }

                PhoneConstants.DataState state = Enum.valueOf(PhoneConstants.DataState.class,
                        intent.getStringExtra(PhoneConstants.STATE_KEY));
                String reason = intent.getStringExtra(PhoneConstants.STATE_CHANGE_REASON_KEY);
                String apnName = intent.getStringExtra(PhoneConstants.DATA_APN_KEY);
                mNetworkInfo[simCardId.toInt()].setRoaming(intent.getBooleanExtra(
                        PhoneConstants.DATA_NETWORK_ROAMING_KEY, false));
                if (VDBG) {
                    log(mApnType + " setting isAvailable to " +
                            intent.getBooleanExtra(PhoneConstants.NETWORK_UNAVAILABLE_KEY,false));
                }
                mNetworkInfo[simCardId.toInt()].setIsAvailable(!intent.getBooleanExtra(
                        PhoneConstants.NETWORK_UNAVAILABLE_KEY, false));

                if (DBG) {
                    log("Received state=" + state + ", old=" + mMobileDataState[simCardId.toInt()] +
                        ", reason=" + (reason == null ? "(unspecified)" : reason));
                }
                if (mMobileDataState[simCardId.toInt()] != state) {
                    mMobileDataState[simCardId.toInt()] = state;
                    switch (state) {
                        case DISCONNECTED:
                            if(isTeardownRequested()) {
                                setTeardownRequested(false);
                            }

                            setDetailedState(DetailedState.DISCONNECTED, reason, apnName, simCardId);
                            // can't do this here - ConnectivityService needs it to clear stuff
                            // it's ok though - just leave it to be refreshed next time
                            // we connect.
                            //if (DBG) log("clearing mInterfaceName for "+ mApnType +
                            //        " as it DISCONNECTED");
                            //mInterfaceName = null;
                            break;
                        case CONNECTING:
                            setDetailedState(DetailedState.CONNECTING, reason, apnName, simCardId);
                            break;
                        case SUSPENDED:
                            setDetailedState(DetailedState.SUSPENDED, reason, apnName, simCardId);
                            break;
                        case CONNECTED:
                            updateLinkProperitesAndCapatilities(intent);
                            setDetailedState(DetailedState.CONNECTED, reason, apnName, simCardId);
                            break;
                    }

                    if (VDBG) {
                        Slog.d(TAG, "TelephonyMgr.DataConnectionStateChanged");
                        if (mNetworkInfo[simCardId.toInt()] != null) {
                            Slog.d(TAG, "NetworkInfo = " + mNetworkInfo[simCardId.toInt()].toString());
                            Slog.d(TAG, "subType = " + String.valueOf(mNetworkInfo[simCardId.toInt()].getSubtype()));
                            Slog.d(TAG, "subType = " + mNetworkInfo[simCardId.toInt()].getSubtypeName());
                        }
                        if (mLinkProperties != null) {
                            Slog.d(TAG, "LinkProperties = " + mLinkProperties.toString());
                        } else {
                            Slog.d(TAG, "LinkProperties = " );
                        }

                        if (mLinkCapabilities != null) {
                            Slog.d(TAG, "LinkCapabilities = " + mLinkCapabilities.toString());
                        } else {
                            Slog.d(TAG, "LinkCapabilities = " );
                        }
                    }


                    /* lets not sample traffic data across state changes */
                    mSamplingDataTracker.resetSamplingData();
                } else {
                    // There was no state change. Check if LinkProperties has been updated.
                    if (TextUtils.equals(reason, PhoneConstants.REASON_LINK_PROPERTIES_CHANGED)) {
                        mLinkProperties = intent.getParcelableExtra(
                                PhoneConstants.DATA_LINK_PROPERTIES_KEY);
                        if (mLinkProperties == null) {
                            loge("No link property in LINK_PROPERTIES change event.");
                            mLinkProperties = new LinkProperties();
                        }
                        // Just update reason field in this NetworkInfo
                        mNetworkInfo[simCardId.toInt()].setDetailedState(mNetworkInfo[simCardId.toInt()].getDetailedState(), reason,
                                                      mNetworkInfo[simCardId.toInt()].getExtraInfo());
                        Message msg = mTarget.obtainMessage(EVENT_CONFIGURATION_CHANGED,
                                                            mNetworkInfo[simCardId.toInt()]);
                        msg.sendToTarget();
                    }
                }
            } else if (intent.getAction().
                    equals(TelephonyIntents.ACTION_DATA_CONNECTION_FAILED)) {
                String apnType = intent.getStringExtra(PhoneConstants.DATA_APN_TYPE_KEY);
                if (!TextUtils.equals(apnType, mApnType)) {
                    if (DBG) {
                        log(String.format(
                                "Broadcast received: ACTION_ANY_DATA_CONNECTION_FAILED ignore, " +
                                "mApnType=%s != received apnType=%s simId=%s", mApnType, apnType, simCardId.toInt()));
                    }
                    return;
                }
                // Assume this isn't a provisioning network.
                mNetworkInfo[simCardId.toInt()].setIsConnectedToProvisioningNetwork(false);
                String reason = intent.getStringExtra(PhoneConstants.FAILURE_REASON_KEY);
                String apnName = intent.getStringExtra(PhoneConstants.DATA_APN_KEY);
                if (DBG) {
                    log("Broadcast received: " + intent.getAction() +
                                " reason=" + reason == null ? "null" : reason);
                }
                setDetailedState(DetailedState.FAILED, reason, apnName, simCardId);
            } else {
                if (DBG) log("Broadcast received: ignore " + intent.getAction());
            }
        }
    }

    private void getPhoneService(boolean forceRefresh) {
        if ((mPhoneService == null) || forceRefresh) {
            if (SimCardID.ID_ONE.toInt() == SystemProperties.getInt(TelephonyProperties.PROPERTY_DATA_PREFER_SIM_ID, SimCardID.ID_ZERO.toInt())) {
                mPhoneService = ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE2));
            } else {
                mPhoneService = ITelephony.Stub.asInterface(
                                    ServiceManager.getService(Context.TELEPHONY_SERVICE1));
            }
        }
    }

    /**
     * Report whether data connectivity is possible.
     */
    public boolean isAvailable() {
        int simId = SystemProperties.getInt(TelephonyProperties.PROPERTY_DATA_PREFER_SIM_ID, SimCardID.ID_ZERO.toInt());
        if (ConnectivityManager.TYPE_MOBILE_MMS == mNetType) {
            int mmsSimId = SystemProperties.getInt(TelephonyProperties.PROPERTY_WORKING_MMS_SIM_ID, SimCardID.ID_PROMPT.toInt());
            if (-1 != mmsSimId) {
                simId = mmsSimId;
            }
        }
        log("isAvailable(): simId = " + simId);
        return mNetworkInfo[simId].isAvailable();
    }

    /**
     * Return the system properties name associated with the tcp buffer sizes
     * for this network.
     */
    public String getTcpBufferSizesPropName() {
        String networkTypeStr = "unknown";
        TelephonyManager tm = new TelephonyManager(mContext);
        //TODO We have to edit the parameter for getNetworkType regarding CDMA
        switch(tm.getNetworkType()) {
        case TelephonyManager.NETWORK_TYPE_GPRS:
            networkTypeStr = "gprs";
            break;
        case TelephonyManager.NETWORK_TYPE_EDGE:
            networkTypeStr = "edge";
            break;
        case TelephonyManager.NETWORK_TYPE_UMTS:
            networkTypeStr = "umts";
            break;
        case TelephonyManager.NETWORK_TYPE_HSDPA:
            networkTypeStr = "hsdpa";
            break;
        case TelephonyManager.NETWORK_TYPE_HSUPA:
            networkTypeStr = "hsupa";
            break;
        case TelephonyManager.NETWORK_TYPE_HSPA:
            networkTypeStr = "hspa";
            break;
        case TelephonyManager.NETWORK_TYPE_HSPAP:
            networkTypeStr = "hspap";
            break;
        case TelephonyManager.NETWORK_TYPE_CDMA:
            networkTypeStr = "cdma";
            break;
        case TelephonyManager.NETWORK_TYPE_1xRTT:
            networkTypeStr = "1xrtt";
            break;
        case TelephonyManager.NETWORK_TYPE_EVDO_0:
            networkTypeStr = "evdo";
            break;
        case TelephonyManager.NETWORK_TYPE_EVDO_A:
            networkTypeStr = "evdo";
            break;
        case TelephonyManager.NETWORK_TYPE_EVDO_B:
            networkTypeStr = "evdo";
            break;
        case TelephonyManager.NETWORK_TYPE_IDEN:
            networkTypeStr = "iden";
            break;
        case TelephonyManager.NETWORK_TYPE_LTE:
            networkTypeStr = "lte";
            break;
        case TelephonyManager.NETWORK_TYPE_EHRPD:
            networkTypeStr = "ehrpd";
            break;
        default:
            loge("unknown network type: " + tm.getNetworkType());
        }
        log("getTcpBufferSizesPropName() - type="+ tm.getNetworkType()+" str="+ networkTypeStr);
        return "net.tcp.buffersize." + networkTypeStr;
    }

    /**
     * Tear down mobile data connectivity, i.e., disable the ability to create
     * mobile data connections.
     * TODO - make async and return nothing?
     */
    public boolean teardown() {
        setTeardownRequested(true);
        return (setEnableApn(mApnType, false) != PhoneConstants.APN_REQUEST_FAILED);
    }

    /**
     * @return true if this is ready to operate
     */
    public boolean isReady() {
        int simId = SystemProperties.getInt(TelephonyProperties.PROPERTY_DATA_PREFER_SIM_ID, SimCardID.ID_ZERO.toInt());
        if (ConnectivityManager.TYPE_MOBILE_MMS == mNetType) {
            int mmsSimId = SystemProperties.getInt(TelephonyProperties.PROPERTY_WORKING_MMS_SIM_ID, SimCardID.ID_PROMPT.toInt());
            if (-1 != mmsSimId) {
                simId = mmsSimId;
            }
        }
        return mDataConnectionTrackerAc[simId] != null;
    }

    @Override
    public void captivePortalCheckComplete() {
        // not implemented
    }

    @Override
    public void captivePortalCheckCompleted(boolean isCaptivePortal) {
        if (mIsCaptivePortal.getAndSet(isCaptivePortal) != isCaptivePortal) {
            // Captive portal change enable/disable failing fast
            setEnableFailFastMobileData(
                    isCaptivePortal ? DctConstants.ENABLED : DctConstants.DISABLED);
        }
    }

    /**
     * Record the detailed state of a network, and if it is a
     * change from the previous state, send a notification to
     * any listeners.
     * @param state the new {@code DetailedState}
     * @param reason a {@code String} indicating a reason for the state change,
     * if one was supplied. May be {@code null}.
     * @param extraInfo optional {@code String} providing extra information about the state change
     */
    private void setDetailedState(NetworkInfo.DetailedState state, String reason,
            String extraInfo, SimCardID simCardId) {
        if (DBG) log("setDetailed state, old ="
                + mNetworkInfo[simCardId.toInt()].getDetailedState() + " and new state=" + state);
        if (state != mNetworkInfo[simCardId.toInt()].getDetailedState()) {
            boolean wasConnecting = (mNetworkInfo[simCardId.toInt()].getState() == NetworkInfo.State.CONNECTING);
            String lastReason = mNetworkInfo[simCardId.toInt()].getReason();
            /*
             * If a reason was supplied when the CONNECTING state was entered, and no
             * reason was supplied for entering the CONNECTED state, then retain the
             * reason that was supplied when going to CONNECTING.
             */
            if (wasConnecting && state == NetworkInfo.DetailedState.CONNECTED && reason == null
                    && lastReason != null)
                reason = lastReason;
            mNetworkInfo[simCardId.toInt()].setDetailedState(state, reason, extraInfo);
            Message msg = mTarget.obtainMessage(EVENT_STATE_CHANGED, new NetworkInfo(mNetworkInfo[simCardId.toInt()]));
            msg.sendToTarget();
        }
    }

    public void setTeardownRequested(boolean isRequested) {
        mTeardownRequested = isRequested;
    }

    public boolean isTeardownRequested() {
        return mTeardownRequested;
    }

    /**
     * Re-enable mobile data connectivity after a {@link #teardown()}.
     * TODO - make async and always get a notification?
     */
    public boolean reconnect() {
        boolean retValue = false; //connected or expect to be?
        setTeardownRequested(false);
        switch (setEnableApn(mApnType, true)) {
            case PhoneConstants.APN_ALREADY_ACTIVE:
                // need to set self to CONNECTING so the below message is handled.
                retValue = true;
                break;
            case PhoneConstants.APN_REQUEST_STARTED:
                // set IDLE here , avoid the following second FAILED not sent out
                if (-1 != mEnableApnSimId) mNetworkInfo[mEnableApnSimId].setDetailedState(DetailedState.IDLE, null, null);
                retValue = true;
                break;
            case PhoneConstants.APN_REQUEST_FAILED:
            case PhoneConstants.APN_TYPE_NOT_AVAILABLE:
                break;
            default:
                loge("Error in reconnect - unexpected response.");
                break;
        }
        return retValue;
    }

    /**
     * Turn on or off the mobile radio. No connectivity will be possible while the
     * radio is off. The operation is a no-op if the radio is already in the desired state.
     * @param turnOn {@code true} if the radio should be turned on, {@code false} if
     */
    public boolean setRadio(boolean turnOn) {
        getPhoneService(false);
        /*
         * If the phone process has crashed in the past, we'll get a
         * RemoteException and need to re-reference the service.
         */
        for (int retry = 0; retry < 2; retry++) {
            if (mPhoneService == null) {
                loge("Ignoring mobile radio request because could not acquire PhoneService");
                break;
            }

            try {
                return mPhoneService.setRadio(turnOn);
            } catch (RemoteException e) {
                if (retry == 0) getPhoneService(true);
            }
        }

        loge("Could not set radio power to " + (turnOn ? "on" : "off"));
        return false;
    }

    @Override
    public void setUserDataEnable(boolean enabled) {
        if (DBG) log("setUserDataEnable: E enabled=" + enabled);
        final AsyncChannel channel0 = mDataConnectionTrackerAc[0];
        final AsyncChannel channel1 = mDataConnectionTrackerAc[1];
        if (channel0 != null) {
            channel0.sendMessage(DctConstants.CMD_SET_USER_DATA_ENABLE,
                    enabled ? DctConstants.ENABLED : DctConstants.DISABLED);
        }
        if (channel1 != null) {
            channel1.sendMessage(DctConstants.CMD_SET_USER_DATA_ENABLE,
                    enabled ? DctConstants.ENABLED : DctConstants.DISABLED);
        }
        if (channel0 != null || channel1 != null) {
            mUserDataEnabled = enabled;
        }
        if (VDBG) log("setUserDataEnable: X enabled=" + enabled);
    }

    @Override
    public void setPolicyDataEnable(boolean enabled) {
        if (DBG) log("setPolicyDataEnable(enabled=" + enabled + ")");
        final AsyncChannel channel0 = mDataConnectionTrackerAc[0];
        final AsyncChannel channel1 = mDataConnectionTrackerAc[1];
        if (channel0 != null) {
            channel0.sendMessage(DctConstants.CMD_SET_POLICY_DATA_ENABLE,
                    enabled ? DctConstants.ENABLED : DctConstants.DISABLED);
        }
        if (channel1 != null) {
            channel1.sendMessage(DctConstants.CMD_SET_POLICY_DATA_ENABLE,
                    enabled ? DctConstants.ENABLED : DctConstants.DISABLED);
        }
        if (channel0 != null || channel1 != null) {
            mPolicyDataEnabled = enabled;
        }
    }

    /**
     * Eanble/disable FailFast
     *
     * @param enabled is DctConstants.ENABLED/DISABLED
     */
    public void setEnableFailFastMobileData(int enabled) {
        int simId = SystemProperties.getInt(TelephonyProperties.PROPERTY_DATA_PREFER_SIM_ID, SimCardID.ID_ZERO.toInt());
        if (ConnectivityManager.TYPE_MOBILE_MMS == mNetType) {
            int mmsSimId = SystemProperties.getInt(TelephonyProperties.PROPERTY_WORKING_MMS_SIM_ID, SimCardID.ID_PROMPT.toInt());
            if (-1 != mmsSimId) {
                simId = mmsSimId;
            }
        }
        if (DBG) log("setEnableFailFastMobileData(enabled=" + enabled + ")");
        final AsyncChannel channel = mDataConnectionTrackerAc[simId];
        if (channel != null) {
            channel.sendMessage(DctConstants.CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA, enabled);
        }
    }

    /**
     * carrier dependency is met/unmet
     * @param met
     */
    public void setDependencyMet(boolean met) {
        Bundle bundle = Bundle.forPair(DctConstants.APN_TYPE_KEY, mApnType);
        try {
            if (DBG) log("setDependencyMet: E met=" + met);
            Message msg = Message.obtain();
            msg.what = DctConstants.CMD_SET_DEPENDENCY_MET;
            msg.arg1 = (met ? DctConstants.ENABLED : DctConstants.DISABLED);
            msg.setData(bundle);
            if (null != mDataConnectionTrackerAc[0]) {
                mDataConnectionTrackerAc[0].sendMessage(msg);
            }
            mDataConnectionTrackerAc[1].sendMessage(msg);
            if (VDBG) log("setDependencyMet: X met=" + met);
        } catch (NullPointerException e) {
            loge("setDependencyMet: X mAc was null" + e);
        }
    }

    /**
     *  Inform DCT mobile provisioning has started, it ends when provisioning completes.
     */
    public void enableMobileProvisioning(String url) {
        int simId = SystemProperties.getInt(TelephonyProperties.PROPERTY_DATA_PREFER_SIM_ID, SimCardID.ID_ZERO.toInt());
        if (ConnectivityManager.TYPE_MOBILE_MMS == mNetType) {
            int mmsSimId = SystemProperties.getInt(TelephonyProperties.PROPERTY_WORKING_MMS_SIM_ID, SimCardID.ID_PROMPT.toInt());
            if (-1 != mmsSimId) {
                simId = mmsSimId;
            }
        }
        if (DBG) log("enableMobileProvisioning(url=" + url + ")");
        final AsyncChannel channel = mDataConnectionTrackerAc[simId];
        if (channel != null) {
            Message msg = Message.obtain();
            msg.what = DctConstants.CMD_ENABLE_MOBILE_PROVISIONING;
            msg.setData(Bundle.forPair(DctConstants.PROVISIONING_URL_KEY, url));
            channel.sendMessage(msg);
        }
    }

    /**
     * Return if this network is the provisioning network. Valid only if connected.
     * @param met
     */
    public boolean isProvisioningNetwork() {
        boolean retVal;
        int simId = SystemProperties.getInt(TelephonyProperties.PROPERTY_DATA_PREFER_SIM_ID, SimCardID.ID_ZERO.toInt());
        if (ConnectivityManager.TYPE_MOBILE_MMS == mNetType) {
            int mmsSimId = SystemProperties.getInt(TelephonyProperties.PROPERTY_WORKING_MMS_SIM_ID, SimCardID.ID_PROMPT.toInt());
            if (-1 != mmsSimId) {
                simId = mmsSimId;
            }
        }
        try {
            Message msg = Message.obtain();
            msg.what = DctConstants.CMD_IS_PROVISIONING_APN;
            msg.setData(Bundle.forPair(DctConstants.APN_TYPE_KEY, mApnType));
            Message result = mDataConnectionTrackerAc[simId].sendMessageSynchronously(msg);
            retVal = result.arg1 == DctConstants.ENABLED;
        } catch (NullPointerException e) {
            loge("isProvisioningNetwork: X " + e);
            retVal = false;
        }
        if (DBG) log("isProvisioningNetwork: retVal=" + retVal);
        return retVal;
    }

    @Override
    public void addStackedLink(LinkProperties link) {
        mLinkProperties.addStackedLink(link);
    }

    @Override
    public void removeStackedLink(LinkProperties link) {
        mLinkProperties.removeStackedLink(link);
    }

    @Override
    public String toString() {
        final CharArrayWriter writer = new CharArrayWriter();
        final PrintWriter pw = new PrintWriter(writer);
        pw.print("Mobile data state 0: "); pw.println(mMobileDataState[0]);
        pw.print("Mobile data state 1: "); pw.println(mMobileDataState[1]);
        pw.print("Data enabled: user="); pw.print(mUserDataEnabled);
        pw.print(", policy="); pw.println(mPolicyDataEnabled);
        return writer.toString();
    }

    /**
     * Internal method supporting the ENABLE_MMS feature.
     * @param apnType the type of APN to be enabled or disabled (e.g., mms)
     * @param enable {@code true} to enable the specified APN type,
     * {@code false} to disable it.
     * @return an integer value representing the outcome of the request.
     */
    private int setEnableApn(String apnType, boolean enable) {
        log("setEnableApn(): apnType = " + apnType + ", enable = " + enable + ", mEnableApnSimId = " + mEnableApnSimId);
        //getPhoneService(false);
        if (enable || (-1 == mEnableApnSimId)) {
            int simId = SimCardID.ID_ZERO.toInt();
            if (SimCardID.ID_ONE.toInt() == SystemProperties.getInt(TelephonyProperties.PROPERTY_DATA_PREFER_SIM_ID, SimCardID.ID_ZERO.toInt())) {
                log("setEnableApn(): data prefer = 1");
                mPhoneService = ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE2));
                simId = SimCardID.ID_ONE.toInt();
            } else {
                log("setEnableApn(): data prefer = 0");
                mPhoneService = ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE1));
            }
            if (ConnectivityManager.TYPE_MOBILE_MMS == mNetType) {
                int mmsSimId = SystemProperties.getInt(TelephonyProperties.PROPERTY_WORKING_MMS_SIM_ID, SimCardID.ID_PROMPT.toInt());
                log("setEnableApn(): working mms = " + mmsSimId);
                if (-1 != mmsSimId) {
                    if (SimCardID.ID_ONE.toInt() == mmsSimId) {
                        mPhoneService = ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE2));
                        simId = SimCardID.ID_ONE.toInt();
                    } else {
                        mPhoneService = ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE1));
                    }
                }
            }
            mEnableApnSimId = simId;
            log("setEnableApn(): mEnableApnSimId = " + mEnableApnSimId);
        } else {
            if (SimCardID.ID_ONE.toInt() == mEnableApnSimId) {
                mPhoneService = ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE2));
            } else {
                mPhoneService = ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE1));
            }
        }
        /*
         * If the phone process has crashed in the past, we'll get a
         * RemoteException and need to re-reference the service.
         */
        for (int retry = 0; retry < 2; retry++) {
            if (mPhoneService == null) {
                loge("Ignoring feature request because could not acquire PhoneService");
                break;
            }

            try {
                if (enable) {
                    return mPhoneService.enableApnType(apnType);
                } else {
                    mEnableApnSimId = -1;
                    return mPhoneService.disableApnType(apnType);
                }
            } catch (RemoteException e) {
                //if (retry == 0) getPhoneService(true);
            }
        }

        loge("Could not " + (enable ? "enable" : "disable") + " APN type \"" + apnType + "\"");
        return PhoneConstants.APN_REQUEST_FAILED;
    }

    public static String networkTypeToApnType(int netType) {
        switch(netType) {
            case ConnectivityManager.TYPE_MOBILE:
                return PhoneConstants.APN_TYPE_DEFAULT;  // TODO - use just one of these
            case ConnectivityManager.TYPE_MOBILE_MMS:
                return PhoneConstants.APN_TYPE_MMS;
            case ConnectivityManager.TYPE_MOBILE_SUPL:
                return PhoneConstants.APN_TYPE_SUPL;
            case ConnectivityManager.TYPE_MOBILE_DUN:
                return PhoneConstants.APN_TYPE_DUN;
            case ConnectivityManager.TYPE_MOBILE_HIPRI:
                return PhoneConstants.APN_TYPE_HIPRI;
            case ConnectivityManager.TYPE_MOBILE_FOTA:
                return PhoneConstants.APN_TYPE_FOTA;
            case ConnectivityManager.TYPE_MOBILE_IMS:
                return PhoneConstants.APN_TYPE_IMS;
            case ConnectivityManager.TYPE_MOBILE_CBS:
                return PhoneConstants.APN_TYPE_CBS;
            case ConnectivityManager.TYPE_MOBILE_IA:
                return PhoneConstants.APN_TYPE_IA;
            default:
                sloge("Error mapping networkType " + netType + " to apnType.");
                return null;
        }
    }


    /**
     * @see android.net.NetworkStateTracker#getLinkProperties()
     */
    @Override
    public LinkProperties getLinkProperties() {
        return new LinkProperties(mLinkProperties);
    }

    /**
     * @see android.net.NetworkStateTracker#getLinkCapabilities()
     */
    @Override
    public LinkCapabilities getLinkCapabilities() {
        return new LinkCapabilities(mLinkCapabilities);
    }

    public void supplyMessenger(Messenger messenger) {
        int simId = SystemProperties.getInt(TelephonyProperties.PROPERTY_DATA_PREFER_SIM_ID, SimCardID.ID_ZERO.toInt());
        if (ConnectivityManager.TYPE_MOBILE_MMS == mNetType) {
            int mmsSimId = SystemProperties.getInt(TelephonyProperties.PROPERTY_WORKING_MMS_SIM_ID, SimCardID.ID_PROMPT.toInt());
            if (-1 != mmsSimId) {
                simId = mmsSimId;
            }
        }
        if (VDBG) log(mApnType + " got supplyMessenger");
        AsyncChannel ac = new AsyncChannel();
        ac.connect(mContext, MobileDataStateTracker.this.mHandler[simId], messenger);
    }

    private void log(String s) {
        Slog.d(TAG, mApnType + ": " + s);
    }

    private void loge(String s) {
        Slog.e(TAG, mApnType + ": " + s);
    }

    static private void sloge(String s) {
        Slog.e(TAG, s);
    }

    @Override
    public LinkQualityInfo getLinkQualityInfo() {
        int simId = SystemProperties.getInt(TelephonyProperties.PROPERTY_DATA_PREFER_SIM_ID, SimCardID.ID_ZERO.toInt());
        if (ConnectivityManager.TYPE_MOBILE_MMS == mNetType) {
            int mmsSimId = SystemProperties.getInt(TelephonyProperties.PROPERTY_WORKING_MMS_SIM_ID, SimCardID.ID_PROMPT.toInt());
            if (-1 != mmsSimId) {
                simId = mmsSimId;
            }
        }
        if (mNetworkInfo[simId] == null || mNetworkInfo[simId].getType() == ConnectivityManager.TYPE_NONE) {
            // no data available yet; just return
            return null;
        }

        MobileLinkQualityInfo li = new MobileLinkQualityInfo();

        li.setNetworkType(mNetworkInfo[simId].getType());

        mSamplingDataTracker.setCommonLinkQualityInfoFields(li);

        if (mNetworkInfo[simId].getSubtype() != TelephonyManager.NETWORK_TYPE_UNKNOWN) {
            li.setMobileNetworkType(mNetworkInfo[simId].getSubtype());

            NetworkDataEntry entry = getNetworkDataEntry(mNetworkInfo[simId].getSubtype());
            if (entry != null) {
                li.setTheoreticalRxBandwidth(entry.downloadBandwidth);
                li.setTheoreticalRxBandwidth(entry.uploadBandwidth);
                li.setTheoreticalLatency(entry.latency);
            }

            if (mSignalStrength != null) {
                li.setNormalizedSignalStrength(getNormalizedSignalStrength(
                        li.getMobileNetworkType(), mSignalStrength));
            }
        }

        SignalStrength ss = mSignalStrength;
        if (ss != null) {

            li.setRssi(ss.getGsmSignalStrength());
            li.setGsmErrorRate(ss.getGsmBitErrorRate());
            li.setCdmaDbm(ss.getCdmaDbm());
            li.setCdmaEcio(ss.getCdmaEcio());
            li.setEvdoDbm(ss.getEvdoDbm());
            li.setEvdoEcio(ss.getEvdoEcio());
            li.setEvdoSnr(ss.getEvdoSnr());
            li.setLteSignalStrength(ss.getLteSignalStrength());
            li.setLteRsrp(ss.getLteRsrp());
            li.setLteRsrq(ss.getLteRsrq());
            li.setLteRssnr(ss.getLteRssnr());
            li.setLteCqi(ss.getLteCqi());
        }

        if (VDBG) {
            Slog.d(TAG, "Returning LinkQualityInfo with"
                    + " MobileNetworkType = " + String.valueOf(li.getMobileNetworkType())
                    + " Theoretical Rx BW = " + String.valueOf(li.getTheoreticalRxBandwidth())
                    + " gsm Signal Strength = " + String.valueOf(li.getRssi())
                    + " cdma Signal Strength = " + String.valueOf(li.getCdmaDbm())
                    + " evdo Signal Strength = " + String.valueOf(li.getEvdoDbm())
                    + " Lte Signal Strength = " + String.valueOf(li.getLteSignalStrength()));
        }

        return li;
    }

    static class NetworkDataEntry {
        public int networkType;
        public int downloadBandwidth;               // in kbps
        public int uploadBandwidth;                 // in kbps
        public int latency;                         // in millisecond

        NetworkDataEntry(int i1, int i2, int i3, int i4) {
            networkType = i1;
            downloadBandwidth = i2;
            uploadBandwidth = i3;
            latency = i4;
        }
    }

    private static NetworkDataEntry [] mTheoreticalBWTable = new NetworkDataEntry[] {
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_EDGE,      237,     118, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_GPRS,       48,      40, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_UMTS,      384,      64, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_HSDPA,   14400, UNKNOWN, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_HSUPA,   14400,    5760, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_HSPA,    14400,    5760, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_HSPAP,   21000,    5760, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_CDMA,  UNKNOWN, UNKNOWN, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_1xRTT, UNKNOWN, UNKNOWN, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_EVDO_0,   2468,     153, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_EVDO_A,   3072,    1800, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_EVDO_B,  14700,    1800, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_IDEN,  UNKNOWN, UNKNOWN, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_LTE,    100000,   50000, UNKNOWN),
            new NetworkDataEntry(TelephonyManager.NETWORK_TYPE_EHRPD, UNKNOWN, UNKNOWN, UNKNOWN),
    };

    private static NetworkDataEntry getNetworkDataEntry(int networkType) {
        for (NetworkDataEntry entry : mTheoreticalBWTable) {
            if (entry.networkType == networkType) {
                return entry;
            }
        }

        Slog.e(TAG, "Could not find Theoretical BW entry for " + String.valueOf(networkType));
        return null;
    }

    private static int getNormalizedSignalStrength(int networkType, SignalStrength ss) {

        int level;

        switch(networkType) {
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                level = ss.getGsmLevel();
                break;
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
                level = ss.getCdmaLevel();
                break;
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
                level = ss.getEvdoLevel();
                break;
            case TelephonyManager.NETWORK_TYPE_LTE:
                level = ss.getLteLevel();
                break;
            case TelephonyManager.NETWORK_TYPE_IDEN:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            default:
                return UNKNOWN;
        }

        return (level * LinkQualityInfo.NORMALIZED_SIGNAL_STRENGTH_RANGE) /
                SignalStrength.NUM_SIGNAL_STRENGTH_BINS;
    }

    @Override
    public void startSampling(SamplingDataTracker.SamplingSnapshot s) {
        mSamplingDataTracker.startSampling(s);
    }

    @Override
    public void stopSampling(SamplingDataTracker.SamplingSnapshot s) {
        mSamplingDataTracker.stopSampling(s);
    }

    public int getEnableApnSimId() {
        return mEnableApnSimId;
    }
}
