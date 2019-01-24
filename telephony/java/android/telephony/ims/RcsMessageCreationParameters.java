/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.telephony.ims;

import static android.telephony.ims.RcsMessage.LOCATION_NOT_SET;

import android.annotation.CheckResult;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.SubscriptionManager;

/**
 * The collection of parameters to be passed into
 * {@link RcsThread#addIncomingMessage(RcsIncomingMessageCreationParameters)} and
 * {@link RcsThread#addOutgoingMessage(RcsMessageCreationParameters)} to create and persist
 * {@link RcsMessage}s on an {@link RcsThread}
 *
 * @hide TODO - make public
 */
public class RcsMessageCreationParameters implements Parcelable {
    // The globally unique id of the RcsMessage to be created.
    private final String mRcsMessageGlobalId;

    // The subscription that this message was/will be received/sent from.
    private final int mSubId;
    // The sending/receiving status of the message
    private final @RcsMessage.RcsMessageStatus int mMessageStatus;
    // The timestamp of message creation
    private final long mOriginationTimestamp;
    // The user visible content of the message
    private final String mText;
    // The latitude of the message if this is a location message
    private final double mLatitude;
    // The longitude of the message if this is a location message
    private final double mLongitude;

    /**
     * Intended to be used in {@link com.android.internal.telephony.ims.RcsMessageStoreController}
     * @hide - not meant for public use
     */
    @Nullable
    public String getRcsMessageGlobalId() {
        return mRcsMessageGlobalId;
    }

    /**
     * Intended to be used in {@link com.android.internal.telephony.ims.RcsMessageStoreController}
     * @hide - not meant for public use
     */
    public int getSubId() {
        return mSubId;
    }

    /**
     * Intended to be used in {@link com.android.internal.telephony.ims.RcsMessageStoreController}
     * @hide - not meant for public use
     */
    public int getMessageStatus() {
        return mMessageStatus;
    }

    /**
     * Intended to be used in {@link com.android.internal.telephony.ims.RcsMessageStoreController}
     * @hide - not meant for public use
     */
    public long getOriginationTimestamp() {
        return mOriginationTimestamp;
    }

    /**
     * Intended to be used in {@link com.android.internal.telephony.ims.RcsMessageStoreController}
     * @hide - not meant for public use
     */
    @Nullable
    public String getText() {
        return mText;
    }

    /**
     * Intended to be used in {@link com.android.internal.telephony.ims.RcsMessageStoreController}
     * @hide - not meant for public use
     */
    public double getLatitude() {
        return mLatitude;
    }

    /**
     * Intended to be used in {@link com.android.internal.telephony.ims.RcsMessageStoreController}
     * @hide - not meant for public use
     */
    public double getLongitude() {
        return mLongitude;
    }

    /**
     * @param originationTimestamp The origination timestamp of the {@link RcsMessage} to be built.
     *                             Please see US5-13 - GSMA RCC.71 (RCS Universal Profile Service
     *                             Definition Document)
     * @return Returns a {@link Builder} to create an instance of
     *         {@link RcsMessageCreationParameters}
     *
     * @see RcsThread#addOutgoingMessage(RcsMessageCreationParameters)
     * @see RcsIncomingMessageCreationParameters
     */
    public static Builder builder(long originationTimestamp) {
        return new Builder(originationTimestamp);
    }

    /**
     * The base builder for creating {@link RcsMessage}s on {@link RcsThread}s.
     *
     * @see RcsIncomingMessageCreationParameters
     */
    public static class Builder {
        private String mRcsMessageGlobalId;
        private int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        private @RcsMessage.RcsMessageStatus int mMessageStatus;
        private long mOriginationTimestamp;
        private String mText;
        private double mLatitude = LOCATION_NOT_SET;
        private double mLongitude = LOCATION_NOT_SET;

        /**
         * @hide - not meant for public use
         */
        public Builder(long originationTimestamp) {
            mOriginationTimestamp = originationTimestamp;
        }

        /**
         * Sets the status of the {@link RcsMessage} to be built.
         *
         * @param rcsMessageStatus The status to be set
         * @return The same instance of {@link Builder} to chain methods
         * @see RcsMessage#setStatus(int)
         */
        @CheckResult
        public Builder setStatus(@RcsMessage.RcsMessageStatus int rcsMessageStatus) {
            mMessageStatus = rcsMessageStatus;
            return this;
        }

        /**
         * Sets the subsciption ID of the {@link RcsMessage} to be built.
         *
         * @param subId The status to be set
         * @return The same instance of {@link Builder} to chain methods
         * @see android.telephony.SubscriptionInfo#getSubscriptionId()
         * @see RcsMessage#setSubscriptionId(int)
         */
        @CheckResult
        public Builder setSubscriptionId(int subId) {
            mSubId = subId;
            return this;
        }

        /**
         * Sets the globally unique RCS message identifier for the {@link RcsMessage} to be built.
         * This function does not confirm that this message id is unique.
         *
         * @param rcsMessageId The ID to be set
         * @return The same instance of {@link Builder} to chain methods
         * @see RcsMessage#setRcsMessageId(String)
         */
        @CheckResult
        public Builder setRcsMessageId(String rcsMessageId) {
            mRcsMessageGlobalId = rcsMessageId;
            return this;
        }

        /**
         * Sets the text of the {@link RcsMessage} to be built.
         *
         * @param text The user visible text of the message
         * @return The same instance of {@link Builder} to chain methods
         * @see RcsMessage#setText(String)
         */
        @CheckResult
        public Builder setText(String text) {
            mText = text;
            return this;
        }

        /**
         * Sets the latitude of the {@link RcsMessage} to be built. Please see US5-24 - GSMA RCC.71
         * (RCS Universal Profile Service Definition Document)
         *
         * @param latitude The latitude of the location information associated with this message.
         * @return The same instance of {@link Builder} to chain methods
         * @see RcsMessage#setLatitude(double)
         */
        @CheckResult
        public Builder setLatitude(double latitude) {
            mLatitude = latitude;
            return this;
        }

        /**
         * Sets the longitude of the {@link RcsMessage} to be built. Please see US5-24 - GSMA RCC.71
         * (RCS Universal Profile Service Definition Document)
         *
         * @param longitude The longitude of the location information associated with this message.
         * @return The same instance of {@link Builder} to chain methods
         * @see RcsMessage#setLongitude(double)
         */
        @CheckResult
        public Builder setLongitude(double longitude) {
            mLongitude = longitude;
            return this;
        }

        /**
         * @hide
         */
        public RcsMessageCreationParameters build() {
            return new RcsMessageCreationParameters(this);
        }
    }

    protected RcsMessageCreationParameters(Builder builder) {
        mRcsMessageGlobalId = builder.mRcsMessageGlobalId;
        mSubId = builder.mSubId;
        mMessageStatus = builder.mMessageStatus;
        mOriginationTimestamp = builder.mOriginationTimestamp;
        mText = builder.mText;
        mLatitude = builder.mLatitude;
        mLongitude = builder.mLongitude;
    }

    protected RcsMessageCreationParameters(Parcel in) {
        mRcsMessageGlobalId = in.readString();
        mSubId = in.readInt();
        mMessageStatus = in.readInt();
        mOriginationTimestamp = in.readLong();
        mText = in.readString();
        mLatitude = in.readDouble();
        mLongitude = in.readDouble();
    }

    public static final Creator<RcsMessageCreationParameters> CREATOR =
            new Creator<RcsMessageCreationParameters>() {
                @Override
                public RcsMessageCreationParameters createFromParcel(Parcel in) {
                    return new RcsMessageCreationParameters(in);
                }

                @Override
                public RcsMessageCreationParameters[] newArray(int size) {
                    return new RcsMessageCreationParameters[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mRcsMessageGlobalId);
        dest.writeInt(mSubId);
        dest.writeInt(mMessageStatus);
        dest.writeLong(mOriginationTimestamp);
        dest.writeString(mText);
        dest.writeDouble(mLatitude);
        dest.writeDouble(mLongitude);
    }
}
