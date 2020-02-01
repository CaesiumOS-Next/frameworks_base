package android.telephony;

import android.annotation.IntDef;
import android.telecom.Connection;
import android.telephony.data.ApnSetting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Telephony Annotations.
 * Telephony sdk is a mainline module and others cannot reference hidden @IntDef. Moving some
 * telephony annotations to a separate class to allow others statically link to it.
 *
 * @hide
 */
public class Annotation {
    @IntDef(prefix = {"DATA_"}, value = {
            TelephonyManager.DATA_ACTIVITY_NONE,
            TelephonyManager.DATA_ACTIVITY_IN,
            TelephonyManager.DATA_ACTIVITY_OUT,
            TelephonyManager.DATA_ACTIVITY_INOUT,
            TelephonyManager.DATA_ACTIVITY_DORMANT,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface DataActivityType {
    }

    @IntDef(prefix = {"DATA_"}, value = {
            TelephonyManager.DATA_UNKNOWN,
            TelephonyManager.DATA_DISCONNECTED,
            TelephonyManager.DATA_CONNECTING,
            TelephonyManager.DATA_CONNECTED,
            TelephonyManager.DATA_SUSPENDED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface DataState {
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"RADIO_POWER_"},
            value = {
                    TelephonyManager.RADIO_POWER_OFF,
                    TelephonyManager.RADIO_POWER_ON,
                    TelephonyManager.RADIO_POWER_UNAVAILABLE,
            })
    public @interface RadioPowerState {
    }

    @IntDef({
            TelephonyManager.SIM_ACTIVATION_STATE_UNKNOWN,
            TelephonyManager.SIM_ACTIVATION_STATE_ACTIVATING,
            TelephonyManager.SIM_ACTIVATION_STATE_ACTIVATED,
            TelephonyManager.SIM_ACTIVATION_STATE_DEACTIVATED,
            TelephonyManager.SIM_ACTIVATION_STATE_RESTRICTED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SimActivationState {
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"SRVCC_STATE_"},
            value = {
                    TelephonyManager.SRVCC_STATE_HANDOVER_NONE,
                    TelephonyManager.SRVCC_STATE_HANDOVER_STARTED,
                    TelephonyManager.SRVCC_STATE_HANDOVER_COMPLETED,
                    TelephonyManager.SRVCC_STATE_HANDOVER_FAILED,
                    TelephonyManager.SRVCC_STATE_HANDOVER_CANCELED})
    public @interface SrvccState {
    }

    @IntDef(prefix = {"CALL_STATE_"}, value = {
            TelephonyManager.CALL_STATE_IDLE,
            TelephonyManager.CALL_STATE_RINGING,
            TelephonyManager.CALL_STATE_OFFHOOK
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface CallState {
    }

    @IntDef({
            TelephonyManager.NETWORK_TYPE_UNKNOWN,
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_IDEN,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_LTE,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_GSM,
            TelephonyManager.NETWORK_TYPE_TD_SCDMA,
            TelephonyManager.NETWORK_TYPE_IWLAN,

            //TODO: In order for @SystemApi methods to use this class, there cannot be any
            // public hidden members.  This network type is marked as hidden because it is not a
            // true network type and we are looking to remove it completely from the available list
            // of network types.
            //TelephonyManager.NETWORK_TYPE_LTE_CA,

            TelephonyManager.NETWORK_TYPE_NR,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface NetworkType {
    }

    @IntDef(flag = true, prefix = {"TYPE_"}, value = {
            ApnSetting.TYPE_DEFAULT,
            ApnSetting.TYPE_MMS,
            ApnSetting.TYPE_SUPL,
            ApnSetting.TYPE_DUN,
            ApnSetting.TYPE_HIPRI,
            ApnSetting.TYPE_FOTA,
            ApnSetting.TYPE_IMS,
            ApnSetting.TYPE_CBS,
            ApnSetting.TYPE_IA,
            ApnSetting.TYPE_EMERGENCY,
            ApnSetting.TYPE_MCX,
            ApnSetting.TYPE_XCAP,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ApnType {
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"PRECISE_CALL_STATE_"},
            value = {
            PreciseCallState.PRECISE_CALL_STATE_NOT_VALID,
            PreciseCallState.PRECISE_CALL_STATE_IDLE,
            PreciseCallState.PRECISE_CALL_STATE_ACTIVE,
            PreciseCallState.PRECISE_CALL_STATE_HOLDING,
            PreciseCallState.PRECISE_CALL_STATE_DIALING,
            PreciseCallState.PRECISE_CALL_STATE_ALERTING,
            PreciseCallState. PRECISE_CALL_STATE_INCOMING,
            PreciseCallState.PRECISE_CALL_STATE_WAITING,
            PreciseCallState.PRECISE_CALL_STATE_DISCONNECTED,
            PreciseCallState.PRECISE_CALL_STATE_DISCONNECTING})
    public @interface PreciseCallStates {}

    @IntDef(value = {
            DisconnectCause.NOT_VALID,
            DisconnectCause.NOT_DISCONNECTED,
            DisconnectCause.INCOMING_MISSED,
            DisconnectCause.NORMAL,
            DisconnectCause.LOCAL,
            DisconnectCause.BUSY,
            DisconnectCause.CONGESTION,
            DisconnectCause.MMI,
            DisconnectCause.INVALID_NUMBER,
            DisconnectCause.NUMBER_UNREACHABLE,
            DisconnectCause.SERVER_UNREACHABLE,
            DisconnectCause.INVALID_CREDENTIALS,
            DisconnectCause.OUT_OF_NETWORK,
            DisconnectCause.SERVER_ERROR,
            DisconnectCause.TIMED_OUT,
            DisconnectCause.LOST_SIGNAL,
            DisconnectCause.LIMIT_EXCEEDED,
            DisconnectCause.INCOMING_REJECTED,
            DisconnectCause.POWER_OFF,
            DisconnectCause.OUT_OF_SERVICE,
            DisconnectCause.ICC_ERROR,
            DisconnectCause.CALL_BARRED,
            DisconnectCause.FDN_BLOCKED,
            DisconnectCause.CS_RESTRICTED,
            DisconnectCause.CS_RESTRICTED_NORMAL,
            DisconnectCause.CS_RESTRICTED_EMERGENCY,
            DisconnectCause.UNOBTAINABLE_NUMBER,
            DisconnectCause.CDMA_LOCKED_UNTIL_POWER_CYCLE,
            DisconnectCause.CDMA_DROP,
            DisconnectCause.CDMA_INTERCEPT,
            DisconnectCause.CDMA_REORDER,
            DisconnectCause.CDMA_SO_REJECT,
            DisconnectCause.CDMA_RETRY_ORDER,
            DisconnectCause.CDMA_ACCESS_FAILURE,
            DisconnectCause.CDMA_PREEMPTED,
            DisconnectCause.CDMA_NOT_EMERGENCY,
            DisconnectCause.CDMA_ACCESS_BLOCKED,
            DisconnectCause.ERROR_UNSPECIFIED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface DisconnectCauses {
    }

    @IntDef(value = {
            PreciseDisconnectCause.NOT_VALID,
            PreciseDisconnectCause.NO_DISCONNECT_CAUSE_AVAILABLE,
            PreciseDisconnectCause.UNOBTAINABLE_NUMBER,
            PreciseDisconnectCause.NORMAL,
            PreciseDisconnectCause.BUSY,
            PreciseDisconnectCause.NUMBER_CHANGED,
            PreciseDisconnectCause.STATUS_ENQUIRY,
            PreciseDisconnectCause.NORMAL_UNSPECIFIED,
            PreciseDisconnectCause.NO_CIRCUIT_AVAIL,
            PreciseDisconnectCause.TEMPORARY_FAILURE,
            PreciseDisconnectCause.SWITCHING_CONGESTION,
            PreciseDisconnectCause.CHANNEL_NOT_AVAIL,
            PreciseDisconnectCause.QOS_NOT_AVAIL,
            PreciseDisconnectCause.BEARER_NOT_AVAIL,
            PreciseDisconnectCause.ACM_LIMIT_EXCEEDED,
            PreciseDisconnectCause.CALL_BARRED,
            PreciseDisconnectCause.FDN_BLOCKED,
            PreciseDisconnectCause.IMSI_UNKNOWN_IN_VLR,
            PreciseDisconnectCause.IMEI_NOT_ACCEPTED,
            PreciseDisconnectCause.CDMA_LOCKED_UNTIL_POWER_CYCLE,
            PreciseDisconnectCause.CDMA_DROP,
            PreciseDisconnectCause.CDMA_INTERCEPT,
            PreciseDisconnectCause.CDMA_REORDER,
            PreciseDisconnectCause.CDMA_SO_REJECT,
            PreciseDisconnectCause.CDMA_RETRY_ORDER,
            PreciseDisconnectCause.CDMA_ACCESS_FAILURE,
            PreciseDisconnectCause.CDMA_PREEMPTED,
            PreciseDisconnectCause.CDMA_NOT_EMERGENCY,
            PreciseDisconnectCause.CDMA_ACCESS_BLOCKED,
            PreciseDisconnectCause.ERROR_UNSPECIFIED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface PreciseDisconnectCauses {
    }

    @IntDef({
            Connection.AUDIO_CODEC_NONE,
            Connection.AUDIO_CODEC_AMR,
            Connection.AUDIO_CODEC_AMR_WB,
            Connection.AUDIO_CODEC_QCELP13K,
            Connection.AUDIO_CODEC_EVRC,
            Connection.AUDIO_CODEC_EVRC_B,
            Connection.AUDIO_CODEC_EVRC_WB,
            Connection.AUDIO_CODEC_EVRC_NW,
            Connection.AUDIO_CODEC_GSM_EFR,
            Connection.AUDIO_CODEC_GSM_FR,
            Connection.AUDIO_CODEC_G711U,
            Connection.AUDIO_CODEC_G723,
            Connection.AUDIO_CODEC_G711A,
            Connection.AUDIO_CODEC_G722,
            Connection.AUDIO_CODEC_G711AB,
            Connection.AUDIO_CODEC_G729,
            Connection.AUDIO_CODEC_EVS_NB,
            Connection.AUDIO_CODEC_EVS_WB,
            Connection.AUDIO_CODEC_EVS_SWB,
            Connection.AUDIO_CODEC_EVS_FB
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ImsAudioCodec {
    }

    /**
     * Call forwarding function status
     */
    @IntDef(prefix = { "STATUS_" }, value = {
        CallForwardingInfo.STATUS_ACTIVE,
        CallForwardingInfo.STATUS_INACTIVE,
        CallForwardingInfo.STATUS_UNKNOWN_ERROR,
        CallForwardingInfo.STATUS_NOT_SUPPORTED,
        CallForwardingInfo.STATUS_FDN_CHECK_FAILURE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface CallForwardingStatus {
    }

    /**
     * Call forwarding reason types
     */
    @IntDef(flag = true, prefix = { "REASON_" }, value = {
        CallForwardingInfo.REASON_UNCONDITIONAL,
        CallForwardingInfo.REASON_BUSY,
        CallForwardingInfo.REASON_NO_REPLY,
        CallForwardingInfo.REASON_NOT_REACHABLE,
        CallForwardingInfo.REASON_ALL,
        CallForwardingInfo.REASON_ALL_CONDITIONAL
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface CallForwardingReason {
    }

    /**
     * Call waiting function status
     */
    @IntDef(prefix = { "CALL_WAITING_STATUS_" }, value = {
        TelephonyManager.CALL_WAITING_STATUS_ACTIVE,
        TelephonyManager.CALL_WAITING_STATUS_INACTIVE,
        TelephonyManager.CALL_WAITING_STATUS_NOT_SUPPORTED,
        TelephonyManager.CALL_WAITING_STATUS_UNKNOWN_ERROR
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface CallWaitingStatus {
    }

    /**
     * UICC SIM Application Types
     */
    @IntDef(prefix = { "APPTYPE_" }, value = {
            TelephonyManager.APPTYPE_SIM,
            TelephonyManager.APPTYPE_USIM,
            TelephonyManager.APPTYPE_RUIM,
            TelephonyManager.APPTYPE_CSIM,
            TelephonyManager.APPTYPE_ISIM
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface UiccAppType{}
}
