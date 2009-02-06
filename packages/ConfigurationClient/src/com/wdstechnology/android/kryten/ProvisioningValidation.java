/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.wdstechnology.android.kryten;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;

import com.wdstechnology.android.kryten.security.OmaSigner;
import com.wdstechnology.android.kryten.security.UnknownSecurityMechanismException;
import com.wdstechnology.android.kryten.utils.HexConvertor;

public class ProvisioningValidation extends Activity {

    private Button mCancel;
    private Button mOk;
    private EditText mUserSuppliedPin;
    private OmaSigner mSigner;
    private byte[] mMac;
    private byte[] mDocument;
    private Dialog mBadUserPinDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.enter_pin);

        mBadUserPinDialog = new AlertDialog.Builder(ProvisioningValidation.this).setIcon(
                R.drawable.icon).setTitle(R.string.bad_pin_title).setPositiveButton(R.string.ok,
                null).setMessage(R.string.bad_pin_message).create();

        mUserSuppliedPin = (EditText)findViewById(R.id.user_supplied_pin);
        mUserSuppliedPin.setOnKeyListener(new View.OnKeyListener() {

            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_ENTER && KeyEvent.ACTION_DOWN == event.getAction()) {
                    pinEntered();
                    return true;
                } else {
                    return false;
                }
            }
        });

        mCancel = (Button)findViewById(R.id.pin_cancel);
        mCancel.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                ProvisioningNotification.clearNotification(ProvisioningValidation.this);
                finish();
            }
        });

        mOk = (Button)findViewById(R.id.pin_ok);
        mOk.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                pinEntered();
            }

        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        Intent myIntent = getIntent();
        String secString = myIntent.getStringExtra("com.wdstechnology.android.kryten.SEC");
        mDocument = myIntent.getByteArrayExtra("com.wdstechnology.android.kryten.provisioning-data");
        String macString = myIntent.getStringExtra("com.wdstechnology.android.kryten.MAC");
        if (secString == null) {
            ProvisioningFailed.fail(this, R.string.no_security_specified);
            finish();
            return;
        }
        int sec = Integer.valueOf(secString);

        if (macString != null) {
            mMac = HexConvertor.convert(macString);
        } else {
            mMac = null;
        }

        TelephonyManager tm = (TelephonyManager)ProvisioningValidation.this
                .getSystemService(Context.TELEPHONY_SERVICE);

        try {
            mSigner = OmaSigner.signerFor(sec, mDocument, tm.getSubscriberId());
        } catch (UnknownSecurityMechanismException e) {
            throw new RuntimeException(e);
        }

        if (!mSigner.isUserPinRequired()) {
            boolean valid = mSigner.isDocumentValid(mUserSuppliedPin.getText().toString(), mMac);
            if (valid) {
                ProvisioningNotification.clearNotification(ProvisioningValidation.this);
                StoreProvisioning.provision(ProvisioningValidation.this, mDocument);
                finish();
                return;
            } else {
                ProvisioningFailed.fail(this, R.string.bad_network_pin_message);
                finish();
                return;
            }
        }

    }

    private void pinEntered() {
        if (mSigner.isDocumentValid(mUserSuppliedPin.getText().toString(), mMac)) {
            ProvisioningNotification.clearNotification(ProvisioningValidation.this);
            StoreProvisioning.provision(ProvisioningValidation.this, mDocument);
            finish();
            return;
        } else {
            mBadUserPinDialog.show();
        }
    }

    public static PendingIntent createPendingValidationActivity(Context context, String sec,
            String mac, byte[] document) {

        Intent validationIntent = new Intent(context, ProvisioningValidation.class);
        validationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (sec != null) {
            validationIntent.putExtra("com.wdstechnology.android.kryten.SEC", sec);
        }
        if (mac != null) {
            validationIntent.putExtra("com.wdstechnology.android.kryten.MAC", mac);
        }
        validationIntent.putExtra("com.wdstechnology.android.kryten.provisioning-data", document);

        return PendingIntent.getActivity(context, 0, validationIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);

    }

}
