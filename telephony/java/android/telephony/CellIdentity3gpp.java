/*
 * Copyright 2019 The Android Open Source Project
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

package android.telephony;

import android.annotation.CallSuper;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import java.util.Objects;

/**
 * CellIdentity represents the identity of a unique cell. This is the base class for
 * CellIdentityXxx which represents cell identity for specific network access technology.
 */
public abstract class CellIdentity implements Parcelable {

    /** @hide */
    protected final String mMccStr;
    // 2 or 3-digit Mobile Network Code in string format. Null for CDMA cell identity.
    /** @hide */
    protected final String mMncStr;

    /** @hide */
    protected CellIdentity3gpp(String tag, int type, String alphal, String alphas,
            String mcc, String mnc) {
        super(tag, type, alphal, alphas);

        // Only allow INT_MAX if unknown string mcc/mnc
        if (mcc == null || mcc.matches("^[0-9]{3}$")) {
            mMccStr = mcc;
        } else if (mcc.isEmpty() || mcc.equals(String.valueOf(Integer.MAX_VALUE))) {
            // If the mccStr is empty or unknown, set it as null.
            mMccStr = null;
        } else {
            // TODO: b/69384059 Should throw IllegalArgumentException for the invalid MCC format
            // after the bug got fixed.
            mMccStr = null;
            log("invalid MCC format: " + mcc);
        }

        if (mnc == null || mnc.matches("^[0-9]{2,3}$")) {
            mMncStr = mnc;
        } else if (mnc.isEmpty() || mnc.equals(String.valueOf(Integer.MAX_VALUE))) {
            // If the mncStr is empty or unknown, set it as null.
            mMncStr = null;
        } else {
            // TODO: b/69384059 Should throw IllegalArgumentException for the invalid MNC format
            // after the bug got fixed.
            mMncStr = null;
            log("invalid MNC format: " + mnc);
        }
    }

    /**
     * Construct from Parcel
     * @hide
     */
    protected CellIdentity(String tag, int type, Parcel source) {
        super(tag, type, source);
        mMccStr = source.readString();
        mMncStr = source.readString();
    }

    /** Implement the Parcelable interface */
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof CellIdentity3gpp)) {
            return false;
        }

        CellIdentity3gpp o = (CellIdentity) other;
        return super.equals(other) 
                && TextUtils.equals(mMccStr, o.mMccStr)
                && TextUtils.equals(mMncStr, o.mMncStr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mMccStr, mMncStr);
    }

    /**
     * Used by child classes for parceling.
     *
     * @hide
     */
    @CallSuper
    public void writeToParcel(Parcel dest, int type) {
        super.writeToParcel(dest, type);
        dest.writeString(mMccStr);
        dest.writeString(mMncStr);
    }

    /** Implement the Parcelable interface */
    public static final Creator<CellIdentity3gpp> CREATOR =
            new Creator<CellIdentity3gpp>() {
                @Override
                public CellIdentity3gpp createFromParcel(Parcel in) { 
                    int type = in.readInt();
                    switch (type) {
                        case CellInfo.TYPE_GSM: return CellIdentityGsm.createFromParcelBody(in);
                        case CellInfo.TYPE_WCDMA: return CellIdentityWcdma.createFromParcelBody(in);
                        case CellInfo.TYPE_CDMA: return CellIdentityCdma.createFromParcelBody(in);
                        case CellInfo.TYPE_LTE: return CellIdentityLte.createFromParcelBody(in);
                        case CellInfo.TYPE_TDSCDMA:
                            return CellIdentityTdscdma.createFromParcelBody(in);
                        case CellInfo.TYPE_NR: return CellIdentityNr.createFromParcelBody(in);
                        default: throw new IllegalArgumentException("Bad Cell identity Parcel");
                    }
                }

                @Override
                public CellIdentity[] newArray(int size) {
                    return new CellIdentity[size];
                }
            };

    /** @hide */
    protected void log(String s) {
        Rlog.w(mTag, s);
    }
}
