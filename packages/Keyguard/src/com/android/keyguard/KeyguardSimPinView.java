/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.keyguard;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;

import android.content.Context;
import android.content.DialogInterface;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.util.AttributeSet;
import android.view.View;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView.OnEditorActionListener;

/* dual sim */
import android.os.SystemProperties;
import android.util.Log;
import com.android.keyguard.KeyguardSecurityModel.SecurityMode;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.RILConstants.SimCardID;
import com.android.internal.telephony.TelephonyProperties;

/**
 * Displays a PIN pad for unlocking.
 */
public class KeyguardSimPinView extends KeyguardAbsKeyInputView
        implements KeyguardSecurityView, OnEditorActionListener, TextWatcher {
    private static final String LOG_TAG = "KeyguardSimPinView";
    private static final boolean DEBUG = KeyguardViewMediator.DEBUG;
    public static final String TAG = "KeyguardSimPinView";

    private ProgressDialog mSimUnlockProgressDialog = null;
    private CheckSimPin mCheckSimPinThread;

    private AlertDialog mRemainingAttemptsDialog;

    // Use this to debug all of keyguard
    private static boolean DEBUG = KeyguardViewMediator.DEBUG;
    private static final String TAG = "KeyguardSimPinView";
    /* dual sim */
    static final boolean isDualMode =
            SystemProperties.getBoolean(TelephonyProperties.PROPERTY_DUAL_MODE_PHONE, false);
    private SimCardID mSimCardId = SimCardID.ID_ZERO;
    private KeyguardSecurityModel mKeyguardSecurityModel;
    private SecurityMode mSecurityMode;
	private int mPinRemainingCount = -1;

    public KeyguardSimPinView(Context context) {
        this(context, null);
    }

    public KeyguardSimPinView(Context context, AttributeSet attrs) {
        super(context, attrs);
        /* dual sim */
        reGetSecurityMode(context);
        checkPinRemainingCount();
        if(DEBUG && mSimUnlockProgressDialog != null) {
            Log.d(TAG, "KeyguardSimPinView(), mSimUnlockProgressDialog is not NULL, mSimCardId:" + mSimCardId.toInt());
        }
    }

    /* dual sim */
    private void reGetSecurityMode(Context context){
        KeyguardUpdateMonitor updateMonitor = KeyguardUpdateMonitor.getInstance(mContext);
        final IccCardConstants.State simState = updateMonitor.getSimState();
        final IccCardConstants.State sim2State = updateMonitor.getSimState(SimCardID.ID_ONE);
        mKeyguardSecurityModel = new KeyguardSecurityModel(context);
        mSecurityMode = mKeyguardSecurityModel.getSecurityMode();
        switch (mSecurityMode) {
            case SimPin:
                mSimCardId = SimCardID.ID_ZERO;
                break;
            case Sim2Pin:
                mSimCardId = SimCardID.ID_ONE;
                break;
            case SimsBothPin:
            {
                /* In the SimsBothPin case, we can choose which SIM PIN that we prefer to unlock first.
                           * The priority is based on SIM state check order */
                if(sim2State == IccCardConstants.State.PIN_REQUIRED) {
                    mSimCardId = SimCardID.ID_ONE;
                } else if(simState == IccCardConstants.State.PIN_REQUIRED) {
                    mSimCardId = SimCardID.ID_ZERO;
                }
                break;
            }
            case SimsPinPuk: //dual sim
            {
                if(simState == IccCardConstants.State.PIN_REQUIRED) {
                    mSimCardId = SimCardID.ID_ZERO;
                } else if (sim2State == IccCardConstants.State.PIN_REQUIRED) {
                    mSimCardId = SimCardID.ID_ONE;
                }
                break;
            }
        }
        if(DEBUG) Log.d(TAG,"reGetSecurityMode(), simState:" + simState
            + ", sim2State:" + sim2State
            + ", mSecurityMode:" + mSecurityMode
            + ", mSimCardId:" + mSimCardId.toInt());
    }

    private void checkPinRemainingCount(){
        try {
            ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService(SimCardID.ID_ONE == mSimCardId?Context.TELEPHONY_SERVICE2:Context.TELEPHONY_SERVICE1));
            if(phone != null) mPinRemainingCount = phone.getPinRemainingStatus(0);
            else Log.d(TAG,"checkPinRemainingCount(), phone is null");
        } catch (RemoteException e) {
        }
        if(DEBUG)Log.d(TAG,"checkPinRemainingCount(), mSimCardId:" + mSimCardId + ", mPinRemainingCount:" + mPinRemainingCount);
    }

    public void resetState() {
        if(DEBUG) {
            Log.d(TAG, "resetState()");
            if(mSimUnlockProgressDialog != null) {
                Log.d(TAG, "resetState(), mSimUnlockProgressDialog is not NULL, mSimCardId:" + mSimCardId.toInt());
            }
        }

        /* dual sim */
        String title = "";
        if(isDualMode) {
            if (SimCardID.ID_ZERO == mSimCardId) {
                title += mContext.getString(R.string.keyguard_password_enter_sim1_pin_code);
            } else {
                title += mContext.getString(R.string.keyguard_password_enter_sim2_pin_code);
            }
        } else {
            title += mContext.getString(R.string.kg_sim_pin_instructions);
        }
		title += "("+Integer.toString(mPinRemainingCount)+")";

        mSecurityMessageDisplay.setMessage(title, true);
        mPasswordEntry.setEnabled(true);
    }

    private String getPinPasswordErrorMessage(int attemptsRemaining) {
        String displayMessage;

        if (attemptsRemaining == 0) {
            displayMessage = getContext().getString(R.string.kg_password_wrong_pin_code_pukked);
        } else if (attemptsRemaining > 0) {
            displayMessage = getContext().getResources()
                    .getQuantityString(R.plurals.kg_password_wrong_pin_code, attemptsRemaining,
                            attemptsRemaining);
        } else {
            displayMessage = getContext().getString(R.string.kg_password_pin_failed);
        }
        if (DEBUG) Log.d(LOG_TAG, "getPinPasswordErrorMessage:"
                + " attemptsRemaining=" + attemptsRemaining + " displayMessage=" + displayMessage);
        return displayMessage;
    }

    @Override
    protected boolean shouldLockout(long deadline) {
        // SIM PIN doesn't have a timed lockout
        return false;
    }

    @Override
    protected int getPasswordTextViewId() {
        return R.id.pinEntry;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        final View ok = findViewById(R.id.key_enter);
        if (ok != null) {
            ok.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    doHapticKeyClick();
                    verifyPasswordAndUnlock();
                }
            });
        }

        // The delete button is of the PIN keyboard itself in some (e.g. tablet) layouts,
        // not a separate view
        View pinDelete = findViewById(R.id.delete_button);
        if (pinDelete != null) {
            pinDelete.setVisibility(View.VISIBLE);
            pinDelete.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    CharSequence str = mPasswordEntry.getText();
                    if (str.length() > 0) {
                        mPasswordEntry.setText(str.subSequence(0, str.length()-1));
                    }
                    doHapticKeyClick();
                }
            });
            pinDelete.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    mPasswordEntry.setText("");
                    doHapticKeyClick();
                    return true;
                }
            });
        }

        mPasswordEntry.setKeyListener(DigitsKeyListener.getInstance());
        mPasswordEntry.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_VARIATION_PASSWORD);

        mPasswordEntry.requestFocus();

        mSecurityMessageDisplay.setTimeout(0); // don't show ownerinfo/charging status by default
    }

    @Override
    public void showUsabilityHint() {
    }

    @Override
    public void onPause() {
        // dismiss the dialog.
        if (mSimUnlockProgressDialog != null) {
            mSimUnlockProgressDialog.dismiss();
            mSimUnlockProgressDialog = null;
            if(DEBUG) Log.d(TAG, "onPause(), mSimUnlockProgressDialog is NULL now, mSimCardId:" + mSimCardId.toInt());
        }
    }

    /**
     * Since the IPC can block, we want to run the request in a separate thread
     * with a callback.
     */
    private abstract class CheckSimPin extends Thread {
        private final String mPin;

        protected CheckSimPin(String pin) {
            mPin = pin;
        }

        abstract void onSimCheckResponse(final int result, final int attemptsRemaining);

        @Override
        public void run() {
            try {
                ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService(SimCardID.ID_ONE == mSimCardId?Context.TELEPHONY_SERVICE2:Context.TELEPHONY_SERVICE1));
                final boolean result = phone.supplyPin(mPin);
                post(new Runnable() {
                    public void run() {
                        onSimCheckResponse(result[0], result[1]);
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException for supplyPinReportResult:", e);
                post(new Runnable() {
                    public void run() {
                        onSimCheckResponse(PhoneConstants.PIN_GENERAL_FAILURE, -1);
                    }
                });
            }
        }
    }

    private Dialog getSimUnlockProgressDialog() {
        if (mSimUnlockProgressDialog == null) {
            mSimUnlockProgressDialog = new ProgressDialog(mContext);
            mSimUnlockProgressDialog.setMessage(
                    mContext.getString(R.string.kg_sim_unlock_progress_dialog_message));
            mSimUnlockProgressDialog.setIndeterminate(true);
            mSimUnlockProgressDialog.setCancelable(false);
            mSimUnlockProgressDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        }
        return mSimUnlockProgressDialog;
    }

    private Dialog getSimRemainingAttemptsDialog(int remaining) {
        String msg = getPinPasswordErrorMessage(remaining);
        if (mRemainingAttemptsDialog == null) {
            Builder builder = new AlertDialog.Builder(mContext);
            builder.setMessage(msg);
            builder.setCancelable(false);
            builder.setNeutralButton(R.string.ok, null);
            mRemainingAttemptsDialog = builder.create();
            mRemainingAttemptsDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        } else {
            mRemainingAttemptsDialog.setMessage(msg);
        }
        return mRemainingAttemptsDialog;
    }

    @Override
    protected void verifyPasswordAndUnlock() {
        String entry = mPasswordEntry.getText().toString();

        if (entry.length() < 4) {
            // otherwise, display a message to the user, and don't submit.
            mSecurityMessageDisplay.setMessage(R.string.kg_invalid_sim_pin_hint, true);
            mPasswordEntry.setText("");
            mCallback.userActivity(0);
            return;
        }

        getSimUnlockProgressDialog().show();
        if(DEBUG) Log.d(TAG, "verifyPasswordAndUnlock(), start unlock SIM and show progress dialog, mSimCheckInProgress:" + mSimCheckInProgress 
            + ", mSimCardId:" + mSimCardId.toInt());

        if (mCheckSimPinThread == null) {
            mCheckSimPinThread = new CheckSimPin(mPasswordEntry.getText().toString()) {
                void onSimCheckResponse(final int result, final int attemptsRemaining) {
                    post(new Runnable() {
                        public void run() {
                            if(DEBUG) Log.d(TAG, "onSimCheckResponse(), mSimCardId:" + mSimCardId.toInt() + ", success:" + success);
                            // dual sim, re-check and dismiss the needless dialog.
                            if (mSimUnlockProgressDialog != null) {
                                mSimUnlockProgressDialog.hide();
                                mSimUnlockProgressDialog.dismiss();
                                mSimUnlockProgressDialog = null;
                                if(DEBUG) Log.d(TAG, "onSimCheckResponse(), unlock process finished, dismiss the needless progress dialog");
                            }
                            if (success) {
                                // before closing the keyguard, report back that the sim is unlocked
                                // so it knows right away.
                                KeyguardUpdateMonitor.getInstance(getContext()).reportSimUnlocked(mSimCardId);//dual sim
                                mCallback.dismiss(true);
                            } else {
                                checkPinRemainingCount();
                                String title = "";
                                if(isDualMode) {
                                    if (SimCardID.ID_ZERO == mSimCardId) {
                                        title += mContext.getString(R.string.keyguard_password_wrong_sim1_pin_code);
                                    } else {
                                        title += mContext.getString(R.string.keyguard_password_wrong_sim2_pin_code);
                                    }
                                } else {
                                    title += mContext.getString(R.string.kg_password_wrong_pin_code);
                                }
                                title += "("+Integer.toString(mPinRemainingCount)+")";

                                mSecurityMessageDisplay.setMessage
                                    (title, true);
                                mPasswordEntry.setText("");
                            }
                            mCallback.userActivity(0);
                            mCheckSimPinThread = null;
                        }
                    });
                }
            };
            mCheckSimPinThread.start();
        }
    }
}

