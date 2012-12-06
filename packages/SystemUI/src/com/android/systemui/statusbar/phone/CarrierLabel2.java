/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.View;
import android.widget.TextView;

import com.android.internal.telephony.TelephonyIntents;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import com.android.internal.R;

import com.android.internal.telephony.RILConstants.SimCardID;

/**
 * This widget display an analogic clock with two hands for hours and
 * minutes.
 */

/**
 *  Update the carrier name label of SIM 2
 */
public class CarrierLabel2 extends TextView {
    private boolean mAttached;

    public CarrierLabel2(Context context) {
        this(context, null);
    }

    public CarrierLabel2(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CarrierLabel2(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        updateNetworkName(false, null, false, null);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION);
            getContext().registerReceiver(mIntentReceiver, filter, null, getHandler());
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            getContext().unregisterReceiver(mIntentReceiver);
            mAttached = false;
        }
    }

    private String getTranslatedPlmn(String originalPlmn) {
        String translatedPlmn = originalPlmn;
        String[] origPlmnArray;
        Resources res = mContext.getResources();

        origPlmnArray = res.getStringArray(R.array.lockscreen_carrier_plmn_original);
        if (null != origPlmnArray) {
            for (int i=0;i<origPlmnArray.length;i++) {
                if (origPlmnArray[i].equals(originalPlmn)) {
                    String[] translatedPlmnArray;
                    translatedPlmnArray = res.getStringArray(R.array.lockscreen_carrier_plmn_translated);
                    if (null != translatedPlmnArray) {
                        translatedPlmn = translatedPlmnArray[i];
                    }
                    break;
                }
            }
        }

        return translatedPlmn;
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TelephonyIntents.SPN_STRINGS_UPDATED_ACTION.equals(action)) {
                /* dual sim */
                if((SimCardID)(intent.getExtra("simId", SimCardID.ID_ZERO)) == SimCardID.ID_ONE) {
                    updateNetworkName(intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, false),
                            intent.getStringExtra(TelephonyIntents.EXTRA_SPN),
                            intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_PLMN, false),
                            getTranslatedPlmn(intent.getStringExtra(TelephonyIntents.EXTRA_PLMN)));
                }
            }
        }
    };

    void updateNetworkName(boolean showSpn, String spn, boolean showPlmn, String plmn) {
        if (false) {
            Slog.d("CarrierLabel2", "updateNetworkName showSpn=" + showSpn + " spn=" + spn
                    + " showPlmn=" + showPlmn + " plmn=" + plmn);
        }
        final String str;
        // match logic in KeyguardStatusViewManager
        final boolean plmnValid = showPlmn && !TextUtils.isEmpty(plmn);
        final boolean spnValid = showSpn && !TextUtils.isEmpty(spn);
        if (plmnValid && spnValid) {
            str = plmn + "|" + spn;
            this.setVisibility(VISIBLE);
        } else if (plmnValid) {
            str = plmn;
            this.setVisibility(VISIBLE);
        } else if (spnValid) {
            str = spn;
            this.setVisibility(VISIBLE);
        } else {
            str = "";
            this.setVisibility(GONE);
        }
        setText(str);
    }


}


