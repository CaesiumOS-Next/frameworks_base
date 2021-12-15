/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.app;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.annotation.TestApi;
import android.app.compat.CompatChanges;
import android.compat.annotation.ChangeId;
import android.compat.annotation.Disabled;
import android.compat.annotation.EnabledSince;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerExemptionManager;
import android.os.PowerExemptionManager.ReasonCode;
import android.os.PowerExemptionManager.TempAllowListType;

/**
 * Helper class for building an options Bundle that can be used with
 * {@link android.content.Context#sendBroadcast(android.content.Intent)
 * Context.sendBroadcast(Intent)} and related methods.
 * {@hide}
 */
@SystemApi
public class BroadcastOptions {
    private long mTemporaryAppAllowlistDuration;
    private @TempAllowListType int mTemporaryAppAllowlistType;
    private @ReasonCode int mTemporaryAppAllowlistReasonCode;
    private @Nullable String mTemporaryAppAllowlistReason;
    private int mMinManifestReceiverApiLevel = 0;
    private int mMaxManifestReceiverApiLevel = Build.VERSION_CODES.CUR_DEVELOPMENT;
    private boolean mDontSendToRestrictedApps = false;
    private boolean mAllowBackgroundActivityStarts;
    private long mRequiredCompatChangeId = CHANGE_INVALID;
    private boolean mRequiredCompatChangeEnabled = true;

    /**
     * {@link ChangeId} which is invalid.
     *
     * @hide
     */
    public static final long CHANGE_INVALID = Long.MIN_VALUE;

    /**
     * {@link ChangeId} which is always enabled, for testing purposes.
     *
     * @hide
     */
    @TestApi
    @ChangeId
    @EnabledSince(targetSdkVersion = android.os.Build.VERSION_CODES.BASE)
    public static final long CHANGE_ALWAYS_ENABLED = 209888056L;

    /**
     * {@link ChangeId} which is always disabled, for testing purposes.
     *
     * @hide
     */
    @TestApi
    @ChangeId
    @Disabled
    public static final long CHANGE_ALWAYS_DISABLED = 210856463L;

    /**
     * How long to temporarily put an app on the power allowlist when executing this broadcast
     * to it.
     */
    private static final String KEY_TEMPORARY_APP_ALLOWLIST_DURATION
            = "android:broadcast.temporaryAppAllowlistDuration";

    private static final String KEY_TEMPORARY_APP_ALLOWLIST_TYPE
            = "android:broadcast.temporaryAppAllowlistType";

    private static final String KEY_TEMPORARY_APP_ALLOWLIST_REASON_CODE =
            "android:broadcast.temporaryAppAllowlistReasonCode";

    private static final String KEY_TEMPORARY_APP_ALLOWLIST_REASON =
            "android:broadcast.temporaryAppAllowlistReason";

    /**
     * Corresponds to {@link #setMinManifestReceiverApiLevel}.
     */
    private static final String KEY_MIN_MANIFEST_RECEIVER_API_LEVEL
            = "android:broadcast.minManifestReceiverApiLevel";

    /**
     * Corresponds to {@link #setMaxManifestReceiverApiLevel}.
     */
    private static final String KEY_MAX_MANIFEST_RECEIVER_API_LEVEL
            = "android:broadcast.maxManifestReceiverApiLevel";

    /**
     * Corresponds to {@link #setDontSendToRestrictedApps}.
     */
    private static final String KEY_DONT_SEND_TO_RESTRICTED_APPS =
            "android:broadcast.dontSendToRestrictedApps";

    /**
     * Corresponds to {@link #setBackgroundActivityStartsAllowed}.
     */
    private static final String KEY_ALLOW_BACKGROUND_ACTIVITY_STARTS =
            "android:broadcast.allowBackgroundActivityStarts";

    private static final String KEY_REQUIRED_COMPAT_CHANGE_ID =
            "android:broadcast.requiredCompatChangeId";

    private static final String KEY_REQUIRED_COMPAT_CHANGE_ENABLED =
            "android:broadcast.requiredCompatChangeEnabled";

    /**
     * @hide
     * @deprecated Use {@link android.os.PowerExemptionManager#
     * TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_ALLOWED} instead.
     */
    @Deprecated
    public static final int TEMPORARY_WHITELIST_TYPE_FOREGROUND_SERVICE_ALLOWED =
            PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_ALLOWED;

    /**
     * @hide
     * @deprecated Use {@link android.os.PowerExemptionManager#
     * TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_NOT_ALLOWED} instead.
     */
    @Deprecated
    public static final int TEMPORARY_WHITELIST_TYPE_FOREGROUND_SERVICE_NOT_ALLOWED =
            PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_NOT_ALLOWED;

    public static BroadcastOptions makeBasic() {
        BroadcastOptions opts = new BroadcastOptions();
        return opts;
    }

    private BroadcastOptions() {
        resetTemporaryAppAllowlist();
    }

    /** @hide */
    @TestApi
    public BroadcastOptions(@NonNull Bundle opts) {
        // Match the logic in toBundle().
        if (opts.containsKey(KEY_TEMPORARY_APP_ALLOWLIST_DURATION)) {
            mTemporaryAppAllowlistDuration = opts.getLong(KEY_TEMPORARY_APP_ALLOWLIST_DURATION);
            mTemporaryAppAllowlistType = opts.getInt(KEY_TEMPORARY_APP_ALLOWLIST_TYPE);
            mTemporaryAppAllowlistReasonCode = opts.getInt(KEY_TEMPORARY_APP_ALLOWLIST_REASON_CODE,
                    PowerExemptionManager.REASON_UNKNOWN);
            mTemporaryAppAllowlistReason = opts.getString(KEY_TEMPORARY_APP_ALLOWLIST_REASON);
        } else {
            resetTemporaryAppAllowlist();
        }
        mMinManifestReceiverApiLevel = opts.getInt(KEY_MIN_MANIFEST_RECEIVER_API_LEVEL, 0);
        mMaxManifestReceiverApiLevel = opts.getInt(KEY_MAX_MANIFEST_RECEIVER_API_LEVEL,
                Build.VERSION_CODES.CUR_DEVELOPMENT);
        mDontSendToRestrictedApps = opts.getBoolean(KEY_DONT_SEND_TO_RESTRICTED_APPS, false);
        mAllowBackgroundActivityStarts = opts.getBoolean(KEY_ALLOW_BACKGROUND_ACTIVITY_STARTS,
                false);
        mRequiredCompatChangeId = opts.getLong(KEY_REQUIRED_COMPAT_CHANGE_ID, CHANGE_INVALID);
        mRequiredCompatChangeEnabled = opts.getBoolean(KEY_REQUIRED_COMPAT_CHANGE_ENABLED, true);
    }

    /**
     * Set a duration for which the system should temporary place an application on the
     * power allowlist when this broadcast is being delivered to it.
     * @param duration The duration in milliseconds; 0 means to not place on allowlist.
     * @deprecated use {@link #setTemporaryAppAllowlist(long, int, int,  String)} instead.
     */
    @Deprecated
    @RequiresPermission(anyOf = {android.Manifest.permission.CHANGE_DEVICE_IDLE_TEMP_WHITELIST,
            android.Manifest.permission.START_ACTIVITIES_FROM_BACKGROUND,
            android.Manifest.permission.START_FOREGROUND_SERVICES_FROM_BACKGROUND})
    public void setTemporaryAppWhitelistDuration(long duration) {
        setTemporaryAppAllowlist(duration,
                PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_ALLOWED,
                PowerExemptionManager.REASON_UNKNOWN, null);
    }

    /**
     * Set a duration for which the system should temporary place an application on the
     * power allowlist when this broadcast is being delivered to it, specify the temp allowlist
     * type.
     * @param duration the duration in milliseconds.
     *                 0 means to not place on allowlist, and clears previous call to this method.
     * @param type one of {@link TempAllowListType}.
     *             {@link PowerExemptionManager#TEMPORARY_ALLOW_LIST_TYPE_NONE} means
     *             to not place on allowlist, and clears previous call to this method.
     * @param reasonCode one of {@link ReasonCode}, use
     *                  {@link PowerExemptionManager#REASON_UNKNOWN} if not sure.
     * @param reason A human-readable reason explaining why the app is temp allowlisted. Only
     *               used for logging purposes. Could be null or empty string.
     */
    @RequiresPermission(anyOf = {android.Manifest.permission.CHANGE_DEVICE_IDLE_TEMP_WHITELIST,
            android.Manifest.permission.START_ACTIVITIES_FROM_BACKGROUND,
            android.Manifest.permission.START_FOREGROUND_SERVICES_FROM_BACKGROUND})
    public void setTemporaryAppAllowlist(long duration, @TempAllowListType int type,
            @ReasonCode int reasonCode, @Nullable String reason) {
        mTemporaryAppAllowlistDuration = duration;
        mTemporaryAppAllowlistType = type;
        mTemporaryAppAllowlistReasonCode = reasonCode;
        mTemporaryAppAllowlistReason = reason;

        if (!isTemporaryAppAllowlistSet()) {
            resetTemporaryAppAllowlist();
        }
    }

    private boolean isTemporaryAppAllowlistSet() {
        return mTemporaryAppAllowlistDuration > 0
                && mTemporaryAppAllowlistType
                    != PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_NONE;
    }

    private void resetTemporaryAppAllowlist() {
        mTemporaryAppAllowlistDuration = 0;
        mTemporaryAppAllowlistType = PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_NONE;
        mTemporaryAppAllowlistReasonCode = PowerExemptionManager.REASON_UNKNOWN;
        mTemporaryAppAllowlistReason = null;
    }

    /**
     * Return {@link #setTemporaryAppAllowlist}.
     * @hide
     */
    @TestApi
    public long getTemporaryAppAllowlistDuration() {
        return mTemporaryAppAllowlistDuration;
    }

    /**
     * Return {@link #mTemporaryAppAllowlistType}.
     * @hide
     */
    @TestApi
    public @TempAllowListType int getTemporaryAppAllowlistType() {
        return mTemporaryAppAllowlistType;
    }

    /**
     * Return {@link #mTemporaryAppAllowlistReasonCode}.
     * @hide
     */
    @TestApi
    public @ReasonCode int getTemporaryAppAllowlistReasonCode() {
        return mTemporaryAppAllowlistReasonCode;
    }

    /**
     * Return {@link #mTemporaryAppAllowlistReason}.
     * @hide
     */
    @TestApi
    public @Nullable String getTemporaryAppAllowlistReason() {
        return mTemporaryAppAllowlistReason;
    }

    /**
     * Set the minimum target API level of receivers of the broadcast.  If an application
     * is targeting an API level less than this, the broadcast will not be delivered to
     * them.  This only applies to receivers declared in the app's AndroidManifest.xml.
     *
     * @deprecated to give developers the most flexibility during beta releases,
     *             we strongly encourage using {@link ChangeId} instead of
     *             target SDK checks; callers should use
     *             {@link #setRequiredCompatChange(long, boolean)} instead.
     * @hide
     */
    @Deprecated
    public void setMinManifestReceiverApiLevel(int apiLevel) {
        mMinManifestReceiverApiLevel = apiLevel;
    }

    /**
     * Return {@link #setMinManifestReceiverApiLevel}.
     *
     * @deprecated to give developers the most flexibility during beta releases,
     *             we strongly encourage using {@link ChangeId} instead of
     *             target SDK checks; callers should use
     *             {@link #setRequiredCompatChange(long, boolean)} instead.
     * @hide
     */
    @Deprecated
    public int getMinManifestReceiverApiLevel() {
        return mMinManifestReceiverApiLevel;
    }

    /**
     * Set the maximum target API level of receivers of the broadcast.  If an application
     * is targeting an API level greater than this, the broadcast will not be delivered to
     * them.  This only applies to receivers declared in the app's AndroidManifest.xml.
     *
     * @deprecated to give developers the most flexibility during beta releases,
     *             we strongly encourage using {@link ChangeId} instead of
     *             target SDK checks; callers should use
     *             {@link #setRequiredCompatChange(long, boolean)} instead.
     * @hide
     */
    @TestApi
    @SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
    @Deprecated
    public void setMaxManifestReceiverApiLevel(int apiLevel) {
        mMaxManifestReceiverApiLevel = apiLevel;
    }

    /**
     * Return {@link #setMaxManifestReceiverApiLevel}.
     *
     * @deprecated to give developers the most flexibility during beta releases,
     *             we strongly encourage using {@link ChangeId} instead of
     *             target SDK checks; callers should use
     *             {@link #setRequiredCompatChange(long, boolean)} instead.
     * @hide
     */
    @TestApi
    @SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
    @Deprecated
    public int getMaxManifestReceiverApiLevel() {
        return mMaxManifestReceiverApiLevel;
    }

    /**
     * Sets whether pending intent can be sent for an application with background restrictions
     * @param dontSendToRestrictedApps if true, pending intent will not be sent for an application
     * with background restrictions. Default value is {@code false}
     */
    public void setDontSendToRestrictedApps(boolean dontSendToRestrictedApps) {
        mDontSendToRestrictedApps = dontSendToRestrictedApps;
    }

    /**
     * @hide
     * @return #setDontSendToRestrictedApps
     */
    public boolean isDontSendToRestrictedApps() {
        return mDontSendToRestrictedApps;
    }

    /**
     * Sets the process will be able to start activities from background for the duration of
     * the broadcast dispatch. Default value is {@code false}
     */
    @RequiresPermission(android.Manifest.permission.START_ACTIVITIES_FROM_BACKGROUND)
    public void setBackgroundActivityStartsAllowed(boolean allowBackgroundActivityStarts) {
        mAllowBackgroundActivityStarts = allowBackgroundActivityStarts;
    }

    /**
     * @hide
     * @return #setAllowBackgroundActivityStarts
     */
    public boolean allowsBackgroundActivityStarts() {
        return mAllowBackgroundActivityStarts;
    }

    /**
     * When set, this broadcast will only be delivered to apps which have the
     * given {@link ChangeId} in the given state.
     * <p>
     * Each {@link BroadcastOptions} instance supports only a single
     * {@link ChangeId} requirement, so any subsequent calls will override any
     * previously defined requirement.
     * <p>
     * This requirement applies to both manifest registered and runtime
     * registered receivers.
     *
     * @param changeId the {@link ChangeId} to inspect
     * @param enabled the required enabled state of the inspected
     *            {@link ChangeId} for this broadcast to be delivered
     * @see CompatChanges#isChangeEnabled
     * @see #clearRequiredCompatChange()
     */
    public void setRequiredCompatChange(long changeId, boolean enabled) {
        mRequiredCompatChangeId = changeId;
        mRequiredCompatChangeEnabled = enabled;
    }

    /**
     * Clear any previously defined requirement for this broadcast requested via
     * {@link #setRequiredCompatChange(long, boolean)}.
     */
    public void clearRequiredCompatChange() {
        mRequiredCompatChangeId = CHANGE_INVALID;
        mRequiredCompatChangeEnabled = true;
    }

    /**
     * Test if the given app meets the {@link ChangeId} state required by this
     * broadcast, if any.
     *
     * @hide
     */
    @TestApi
    public boolean testRequiredCompatChange(int uid) {
        if (mRequiredCompatChangeId != CHANGE_INVALID) {
            return CompatChanges.isChangeEnabled(mRequiredCompatChangeId,
                    uid) == mRequiredCompatChangeEnabled;
        } else {
            return true;
        }
    }

    /**
     * Returns the created options as a Bundle, which can be passed to
     * {@link android.content.Context#sendBroadcast(android.content.Intent)
     * Context.sendBroadcast(Intent)} and related methods.
     * Note that the returned Bundle is still owned by the BroadcastOptions
     * object; you must not modify it, but can supply it to the sendBroadcast
     * methods that take an options Bundle.
     */
    public Bundle toBundle() {
        Bundle b = new Bundle();
        if (isTemporaryAppAllowlistSet()) {
            b.putLong(KEY_TEMPORARY_APP_ALLOWLIST_DURATION, mTemporaryAppAllowlistDuration);
            b.putInt(KEY_TEMPORARY_APP_ALLOWLIST_TYPE, mTemporaryAppAllowlistType);
            b.putInt(KEY_TEMPORARY_APP_ALLOWLIST_REASON_CODE, mTemporaryAppAllowlistReasonCode);
            b.putString(KEY_TEMPORARY_APP_ALLOWLIST_REASON, mTemporaryAppAllowlistReason);
        }
        if (mMinManifestReceiverApiLevel != 0) {
            b.putInt(KEY_MIN_MANIFEST_RECEIVER_API_LEVEL, mMinManifestReceiverApiLevel);
        }
        if (mMaxManifestReceiverApiLevel != Build.VERSION_CODES.CUR_DEVELOPMENT) {
            b.putInt(KEY_MAX_MANIFEST_RECEIVER_API_LEVEL, mMaxManifestReceiverApiLevel);
        }
        if (mDontSendToRestrictedApps) {
            b.putBoolean(KEY_DONT_SEND_TO_RESTRICTED_APPS, true);
        }
        if (mAllowBackgroundActivityStarts) {
            b.putBoolean(KEY_ALLOW_BACKGROUND_ACTIVITY_STARTS, true);
        }
        if (mRequiredCompatChangeId != CHANGE_INVALID) {
            b.putLong(KEY_REQUIRED_COMPAT_CHANGE_ID, mRequiredCompatChangeId);
            b.putBoolean(KEY_REQUIRED_COMPAT_CHANGE_ENABLED, mRequiredCompatChangeEnabled);
        }
        return b.isEmpty() ? null : b;
    }
}
