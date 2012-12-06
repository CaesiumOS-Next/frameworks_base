/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.systemui.statusbar;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;


import com.android.systemui.R;
import com.android.systemui.statusbar.policy.NetworkController_brcm;

// Intimately tied to the design of res/layout/signal_cluster_view_brcm.xml
public class SignalClusterView_brcm
        extends LinearLayout
        implements NetworkController_brcm.SignalCluster {

    static final boolean DEBUG = true;
    static final String TAG = "SignalClusterView";

    NetworkController_brcm mNC;

    private boolean mWifiVisible = false;
    private int mWifiStrengthId = 0;
    private boolean mMobileVisible = false;
    private boolean mMobileVisible2 = false;
    private int mMobileStrengthId1 = 0, mMobileTypeId1 = 0;
    private int mMobileStrengthId2 = 0, mMobileTypeId2 = 0;
    private int mSimAbsentId1 = 0, mSimAbsentId2 = 0;
    private boolean mIsAirplaneMode = false;
    private boolean mIsAirplaneMode2 = false;
    private int mAirplaneIconId = 0;
    private String mWifiDescription, mMobileDescription, mMobileTypeDescription;

    ViewGroup mWifiGroup, mMobileGroup;
    ImageView mWifi, mAirplane, mAirplane2;
    ImageView mMobile1, mMobile2, mMobileType1, mMobileType2;
    ViewGroup mMobileGroup2;
    View mSpacer;
    ViewGroup mSimGroup, mSimGroup2;
    ImageView mSimAbsent, mSimAbsent2;
    View mSimSpacer;

    public SignalClusterView_brcm(Context context) {
        this(context, null);
    }

    public SignalClusterView_brcm(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SignalClusterView_brcm(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setNetworkController(NetworkController_brcm nc) {
        if (DEBUG) Log.d(TAG, "NetworkController=" + nc);
        mNC = nc;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mWifiGroup      = (ViewGroup) findViewById(R.id.wifi_combo);
        mWifi           = (ImageView) findViewById(R.id.wifi_signal);
        mMobileGroup    = (ViewGroup) findViewById(R.id.mobile_combo);
        mMobile1         = (ImageView) findViewById(R.id.mobile_signal);
        mMobileType1     = (ImageView) findViewById(R.id.mobile_type);
        mMobileGroup2    = (ViewGroup) findViewById(R.id.mobile_combo2);
        mMobile2         = (ImageView) findViewById(R.id.mobile_signal2);
        mMobileType2     = (ImageView) findViewById(R.id.mobile_type2);
        mSpacer         =             findViewById(R.id.spacer);
        mSimGroup        = (ViewGroup) findViewById(R.id.mobile_sim);
        mSimAbsent       = (ImageView) findViewById(R.id.mobile_sim_absent);
        mSimGroup2       = (ViewGroup) findViewById(R.id.mobile_sim2);
        mSimAbsent2      = (ImageView) findViewById(R.id.mobile_sim_absent2);
        mSimSpacer       =             findViewById(R.id.sim_spacer);
        mAirplane       = (ImageView) findViewById(R.id.airplane);
        mAirplane2       = (ImageView) findViewById(R.id.airplane2);

        apply();
    }

    @Override
    protected void onDetachedFromWindow() {
        mWifiGroup      = null;
        mWifi           = null;
        mMobileGroup    = null;
        mMobile1         = null;
        mMobileType1     = null;
        mMobileGroup2    = null;
        mMobile2         = null;
        mMobileType2     = null;
        mSimGroup        = null;
        mSimAbsent       = null;
        mSimGroup2       = null;
        mSimAbsent2      = null;
        mSimSpacer       = null;
        mSpacer         = null;
        mAirplane       = null;
        mAirplane2       = null;

        super.onDetachedFromWindow();
    }

    @Override
    public void setWifiIndicators(boolean visible, int strengthIcon, String contentDescription) {
        mWifiVisible = visible;
        mWifiStrengthId = strengthIcon;
        mWifiDescription = contentDescription;

        apply();
    }

    @Override
    public void setMobileDataIndicators1(boolean visible, int strengthIcon,
            int typeIcon, int simAbsentIcon, String contentDescription, String typeContentDescription) {
        mMobileVisible = visible;
        mMobileStrengthId1 = strengthIcon;
        mMobileTypeId1 = typeIcon;
        mSimAbsentId1 = simAbsentIcon;
        mMobileDescription = contentDescription;
        mMobileTypeDescription = typeContentDescription;

        apply();
    }

    @Override
    public void setMobileDataIndicators2(boolean visible, int strengthIcon,
            int typeIcon, int simAbsentIcon, String contentDescription, String typeContentDescription) {
        mMobileVisible2 = visible;
        mMobileStrengthId2 = strengthIcon;
        mMobileTypeId2 = typeIcon;
        mSimAbsentId2 = simAbsentIcon;
        mMobileDescription = contentDescription;
        mMobileTypeDescription = typeContentDescription;

        apply();
    }

    @Override
    public void setIsAirplaneMode(boolean is, int airplaneIconId) {
        mIsAirplaneMode = is;
        mAirplaneIconId = airplaneIconId;

        apply();
    }

    @Override
    public void setIsAirplaneMode2(boolean is, int airplaneIconId) {
        mIsAirplaneMode2 = is;
        mAirplaneIconId = airplaneIconId;

        apply();
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        // Standard group layout onPopulateAccessibilityEvent() implementations
        // ignore content description, so populate manually
        if (mWifiVisible && mWifiGroup != null && mWifiGroup.getContentDescription() != null)
            event.getText().add(mWifiGroup.getContentDescription());
        if (mMobileVisible && mMobileGroup != null && mMobileGroup.getContentDescription() != null)
            event.getText().add(mMobileGroup.getContentDescription());
        if (mMobileVisible2 && mMobileGroup2 != null && mMobileGroup2.getContentDescription() != null)
            event.getText().add(mMobileGroup2.getContentDescription());
        return super.dispatchPopulateAccessibilityEvent(event);
    }

   @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);

        if (mWifi != null) {
            mWifi.setImageDrawable(null);
        }

        if (mMobile1 != null) {
            mMobile1.setImageDrawable(null);
        }

        if (mMobileType1 != null) {
            mMobileType1.setImageDrawable(null);
        }

        if (mMobile2 != null) {
            mMobile2.setImageDrawable(null);
        }

        if (mMobileType2 != null) {
            mMobileType2.setImageDrawable(null);
        }

        if(mAirplane != null) {
            mAirplane.setImageDrawable(null);
        }

        if(mAirplane2 != null) {
            mAirplane.setImageDrawable(null);
        }

        apply();
    }

    // Run after each indicator change.
    private void apply() {
        if (mWifiGroup == null) return;

        if (mWifiVisible) {
            mWifi.setImageResource(mWifiStrengthId);
            mWifiGroup.setContentDescription(mWifiDescription);
            mWifiGroup.setVisibility(View.VISIBLE);
        } else {
            mWifiGroup.setVisibility(View.GONE);
        }

        if (DEBUG) Log.d(TAG,
                String.format("wifi: %s sig=%d",
                    (mWifiVisible ? "VISIBLE" : "GONE"),
                    mWifiStrengthId));

        if (mMobileVisible && !mIsAirplaneMode) {
            mMobile1.setImageResource(mMobileStrengthId1);
            mMobileType1.setImageResource(mMobileTypeId1);
            mMobileGroup.setContentDescription(mMobileTypeDescription + " " + mMobileDescription);
            mMobileGroup.setVisibility(View.VISIBLE);
            mSimGroup.setVisibility(View.VISIBLE);
            mSimAbsent.setImageResource(mSimAbsentId1);
        } else {
            mMobileGroup.setVisibility(View.GONE);
            mSimGroup.setVisibility(View.GONE);
        }

        if (mMobileVisible2 && !mIsAirplaneMode2) {
            mMobile2.setImageResource(mMobileStrengthId2);
            mMobileType2.setImageResource(mMobileTypeId2);
            mMobileGroup2.setContentDescription(mMobileTypeDescription + " " + mMobileDescription);
            mMobileGroup2.setVisibility(View.VISIBLE);
            mSimGroup2.setVisibility(View.VISIBLE);
            mSimAbsent2.setImageResource(mSimAbsentId2);
            if (mSimAbsentId2 != 0 )
                mSimSpacer.setVisibility(View.VISIBLE);
            else
                mSimSpacer.setVisibility(View.GONE);
        } else {
            mMobileGroup2.setVisibility(View.GONE);
            mSimSpacer.setVisibility(View.GONE);
            mSimGroup2.setVisibility(View.GONE);
        }

        if (mIsAirplaneMode) {
            mAirplane.setImageResource(mAirplaneIconId);
            mAirplane.setVisibility(View.VISIBLE);
        } else {
            mAirplane.setVisibility(View.GONE);
        }

        mAirplane2.setVisibility(View.GONE); // show only one airplane icon

        if (mMobileVisible && mMobileVisible2 && mWifiVisible && mIsAirplaneMode) {
            mSpacer.setVisibility(View.INVISIBLE);
        } else {
            mSpacer.setVisibility(View.GONE);
        }

        if (DEBUG) {
            Log.d(TAG,
                    String.format("mobile1: %s sig=%d typ=%d",
                        (mMobileVisible ? "VISIBLE" : "GONE"),
                        mMobileStrengthId1, mMobileTypeId1));
            Log.d(TAG,
                    String.format("mobile2: %s sig=%d typ=%d",
                        (mMobileVisible2 ? "VISIBLE" : "GONE"),
                        mMobileStrengthId2, mMobileTypeId2));
        }

        boolean isMobileType1Visible, isMobileType2Visible;
        isMobileType1Visible = mMobileVisible && !mWifiVisible && !mIsAirplaneMode;
        isMobileType2Visible = mMobileVisible2 && !mWifiVisible && !mIsAirplaneMode2;
        mMobileType1.setVisibility(isMobileType1Visible ? View.VISIBLE : View.GONE);
        mMobileType2.setVisibility(isMobileType2Visible ? View.VISIBLE : View.GONE);
    }
}

