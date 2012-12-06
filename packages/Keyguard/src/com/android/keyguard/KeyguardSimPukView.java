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

import android.content.Context;
import android.animation.AnimatorSet.Builder;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView.OnEditorActionListener;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;

import android.os.SystemProperties;
import android.util.Log;
import com.android.keyguard.KeyguardSecurityModel.SecurityMode;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.RILConstants.SimCardID;
import com.android.internal.telephony.TelephonyProperties;

/**
 * Displays a PIN pad for entering a PUK (Pin Unlock Kode) provided by a carrier.
 */
public class KeyguardSimPukView extends KeyguardAbsKeyInputView
        implements KeyguardSecurityView, OnEditorActionListener, TextWatcher {
    private static final String LOG_TAG = "KeyguardSimPukView";
    private static final boolean DEBUG = KeyguardViewMediator.DEBUG;
    public static final String TAG = "KeyguardSimPukView";

    // Use this to debug all of keyguard
    private static boolean DEBUG = KeyguardViewMediator.DEBUG;
    private static final String TAG = "KeyguardSimPukView";
    /* dual sim */
    static final boolean isDualMode =
            SystemProperties.getBoolean(TelephonyProperties.PROPERTY_DUAL_MODE_PHONE, false);
    private SimCardID mSimCardId = SimCardID.ID_ZERO;
    private KeyguardSecurityModel mKeyguardSecurityModel;
    private SecurityMode mSecurityMode;
    private int mPinRemainingCount = -1;

    private ProgressDialog mSimUnlockProgressDialog = null;
    private CheckSimPuk mCheckSimPukThread;
    private String mPukText;
    private String mPinText;
    private StateMachine mStateMachine = new StateMachine();
    private AlertDialog mRemainingAttemptsDialog;

    private class StateMachine {
        final int ENTER_PUK = 0;
        final int ENTER_PIN = 1;
        final int CONFIRM_PIN = 2;
        final int DONE = 3;
        private int state = ENTER_PUK;

        public void next() {
            int msg = 0;
            if (state == ENTER_PUK) {
                if (checkPuk()) {
                    state = ENTER_PIN;
                    msg = R.string.kg_puk_enter_pin_hint;
                } else {
                    msg = R.string.invalidPuk_8numbers;
                }
            } else if (state == ENTER_PIN) {
                if (checkPin()) {
                    state = CONFIRM_PIN;
                    msg = R.string.kg_enter_confirm_pin_hint;
                } else {
                    msg = R.string.kg_invalid_sim_pin_hint;
                }
            } else if (state == CONFIRM_PIN) {
                if (confirmPin()) {
                    state = DONE;
                    msg = R.string.keyguard_sim_unlock_progress_dialog_message;
                    updateSim();
                } else {
                    state = ENTER_PIN; // try again?
                    msg = R.string.kg_invalid_confirm_pin_hint;
                }
            }
            mPasswordEntry.setText(null);
            if (msg != 0) {
                mSecurityMessageDisplay.setMessage(msg, true);
            }
        }

        void reset() {
            mPinText="";
            mPukText="";
            state = ENTER_PUK;
            /* dual sim */
            String title = "";
            if(isDualMode) {
                if (SimCardID.ID_ZERO == mSimCardId) {
                    title += mContext.getString(R.string.keyguard_password_enter_sim1_puk_code);
                } else {
                    title += mContext.getString(R.string.keyguard_password_enter_sim2_puk_code);
                }
            } else {
                title += mContext.getString(R.string.kg_puk_enter_puk_hint);
            }
            title += "("+Integer.toString(mPinRemainingCount)+")";
            mSecurityMessageDisplay.setMessage(title, true);//SIM is now disabled. Enter PUK code to continue. Contact carrier for details.
            mPasswordEntry.requestFocus();
        }
    }

    private String getPukPasswordErrorMessage(int attemptsRemaining) {
        String displayMessage;

        if (attemptsRemaining == 0) {
            displayMessage = getContext().getString(R.string.kg_password_wrong_puk_code_dead);
        } else if (attemptsRemaining > 0) {
            displayMessage = getContext().getResources()
                    .getQuantityString(R.plurals.kg_password_wrong_puk_code, attemptsRemaining,
                            attemptsRemaining);
        } else {
            displayMessage = getContext().getString(R.string.kg_password_puk_failed);
        }
        if (DEBUG) Log.d(LOG_TAG, "getPukPasswordErrorMessage:"
                + " attemptsRemaining=" + attemptsRemaining + " displayMessage=" + displayMessage);
        return displayMessage;
    }

    public KeyguardSimPukView(Context context) {
        this(context, null);
    }

    public KeyguardSimPukView(Context context, AttributeSet attrs) {
        super(context, attrs);
        /* dual sim */
        reGetSecurityMode(context);
        checkPukRemainingCount();
    }

    /* dual sim */
    private void reGetSecurityMode(Context context){
        KeyguardUpdateMonitor updateMonitor = KeyguardUpdateMonitor.getInstance(mContext);
        final IccCardConstants.State simState = updateMonitor.getSimState();
        final IccCardConstants.State sim2State = updateMonitor.getSimState(SimCardID.ID_ONE);
        mKeyguardSecurityModel = new KeyguardSecurityModel(context);
        mSecurityMode = mKeyguardSecurityModel.getSecurityMode();
        switch (mSecurityMode) {
            case SimPuk:
                mSimCardId = SimCardID.ID_ZERO;
                break;
            case Sim2Puk:
                mSimCardId = SimCardID.ID_ONE;
                break;
            case SimsBothPuk:
            {
                /* In the SimsBothPuk case, we can choose which SIM PUK that we prefer to unlock first.
                           * The priority is based on SIM state check order */
                if(sim2State == IccCardConstants.State.PUK_REQUIRED) {
                    mSimCardId = SimCardID.ID_ONE;
                } else if(simState == IccCardConstants.State.PUK_REQUIRED) {
                    mSimCardId = SimCardID.ID_ZERO;
                }
                break;
            }
            case SimsPinPuk: //dual sim
            {
                if(simState == IccCardConstants.State.PUK_REQUIRED) {
                    mSimCardId = SimCardID.ID_ZERO;
                } else if (sim2State == IccCardConstants.State.PUK_REQUIRED) {
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

    private void checkPukRemainingCount(){
        try {
            ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService(SimCardID.ID_ONE == mSimCardId?Context.TELEPHONY_SERVICE2:Context.TELEPHONY_SERVICE1));
            if(phone != null) mPinRemainingCount = phone.getPinRemainingStatus(1);
            else Log.d(TAG,"checkPukRemainingCount(), phone is null");
        } catch (RemoteException e) {
        }
        if(DEBUG)Log.d(TAG,"checkPinRemainingCount(), mSimCardId:" + mSimCardId + ", mPinRemainingCount:" + mPinRemainingCount);
    }

    public void resetState() {
        if(DEBUG) Log.d(TAG, "resetState()");
        mStateMachine.reset();
        mPasswordEntry.setEnabled(true);
    }

    @Override
    protected boolean shouldLockout(long deadline) {
        // SIM PUK doesn't have a timed lockout
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
        }
    }

    /**
     * Since the IPC can block, we want to run the request in a separate thread
     * with a callback.
     */
    private abstract class CheckSimPuk extends Thread {

        private final String mPin, mPuk;

        protected CheckSimPuk(String puk, String pin) {
            mPuk = puk;
            mPin = pin;
        }

        abstract void onSimLockChangedResponse(final int result, final int attemptsRemaining);

        @Override
        public void run() {
            try {
                ITelephony phone;
                phone = ITelephony.Stub.asInterface(ServiceManager.checkService(SimCardID.ID_ONE == mSimCardId?Context.TELEPHONY_SERVICE2:Context.TELEPHONY_SERVICE1));
                final boolean result = phone.supplyPuk(mPuk, mPin);

                post(new Runnable() {
                    public void run() {
                        onSimLockChangedResponse(result[0], result[1]);
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException for supplyPukReportResult:", e);
                post(new Runnable() {
                    public void run() {
                        onSimLockChangedResponse(PhoneConstants.PIN_GENERAL_FAILURE, -1);
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
            if (!(mContext instanceof Activity)) {
                mSimUnlockProgressDialog.getWindow().setType(
                        WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
            }
        }
        return mSimUnlockProgressDialog;
    }

    private Dialog getPukRemainingAttemptsDialog(int remaining) {
        String msg = getPukPasswordErrorMessage(remaining);
        if (mRemainingAttemptsDialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
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

    private boolean checkPuk() {
        // make sure the puk is 8 digits long.
        if (mPasswordEntry.getText().length() == 8) {//android default: >=8
            mPukText = mPasswordEntry.getText().toString();
            return true;
        }
        return false;
    }

    private boolean checkPin() {
        // make sure the PIN is between 4 and 8 digits
        int length = mPasswordEntry.getText().length();
        if (length >= 4 && length <= 8) {
            mPinText = mPasswordEntry.getText().toString();
            return true;
        }
        return false;
    }

    public boolean confirmPin() {
        return mPinText.equals(mPasswordEntry.getText().toString());
    }

    private void updateSim() {
        getSimUnlockProgressDialog().show();

        if (mCheckSimPukThread == null) {
            mCheckSimPukThread = new CheckSimPuk(mPukText, mPinText) {
                void onSimLockChangedResponse(final int result, final int attemptsRemaining) {
                    post(new Runnable() {
                        public void run() {
                            if(DEBUG) Log.d(TAG, "onSimLockChangedResponse(), mSimCardId:" + mSimCardId.toInt() + ", success:" + success);
                            // dual sim, re-check and dismiss the needless dialog.
                            if (mSimUnlockProgressDialog != null) {
                                mSimUnlockProgressDialog.hide();
                                mSimUnlockProgressDialog.dismiss();
                                mSimUnlockProgressDialog = null;
                                if(DEBUG) Log.d(TAG, "onSimLockChangedResponse(), unlock process finished, dismiss the needless progress dialog");
                            }
                            if (result == PhoneConstants.PIN_RESULT_SUCCESS) {
                                KeyguardUpdateMonitor.getInstance(getContext()).reportSimUnlocked();
                                mCallback.dismiss(true);
                            } else {
                                checkPukRemainingCount();

                                mStateMachine.reset();
                                String title = "";
                                if(isDualMode) {
                                    if (SimCardID.ID_ZERO == mSimCardId) {
                                        title += mContext.getString(R.string.kg_invalid_puk_sim1);
                                    } else {
                                        title += mContext.getString(R.string.kg_invalid_puk_sim2);
                                    }
                                } else {
                                    title += mContext.getString(R.string.kg_invalid_puk);
                                }
                                title += "("+Integer.toString(mPinRemainingCount)+")";
                                mSecurityMessageDisplay.setMessage(title, true);
                            }
                            mCheckSimPukThread = null;
                        }
                    });
                }
            };
            mCheckSimPukThread.start();
        }
    }

    @Override
    protected void verifyPasswordAndUnlock() {
        mStateMachine.next();
    }
}


