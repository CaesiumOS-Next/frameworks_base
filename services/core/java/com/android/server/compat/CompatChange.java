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

package com.android.server.compat;

import android.content.pm.ApplicationInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the state of a single compatibilty change.
 *
 * <p>A compatibility change has a default settings, determined by the {@code enableAfterTargetSdk}
 * and {@code disabled} constructor parameters. These settings can be overridden for a specific
 * package using {@link #addPackageOverride(String, boolean)}.
 */
public class CompatChange {

    private final long mChangeId;
    private final String mName;
    private final int mEnableAfterTargetSdk;
    private final boolean mDisabled;
    private final Map<String, Boolean> mPackageOverrides = new HashMap<>();

    public CompatChange(long changeId) {
        this(changeId, null, -1, false);
    }

    public CompatChange(long changeId, String name, int enableAfterTargetSdk, boolean disabled) {
        mChangeId = changeId;
        mName = name;
        mEnableAfterTargetSdk = enableAfterTargetSdk;
        mDisabled = disabled;
    }

    public long getId() {
        return mChangeId;
    }

    public String getName() {
        return mName;
    }

    /**
     * Force the enabled state of this change for a given package name. The change will only take
     * affect  after that packages process is killed and restarted.
     *
     * @param pname Package name to enable the change for.
     * @param enabled Whether or not to enable te change.
     */
    public void addPackageOverride(String pname, boolean enabled) {
        mPackageOverrides.put(pname, enabled);
    }

    /**
     * Remove any package override for the given package name. This will result in the package
     * seeing the default behaviour again.
     *
     * @param pname Package name to reset to defaults for.
     */
    public void removeOverride(boolean pname) {
        mPackageOverrides.remove(pname);
    }

    /**
     * Find if this change is enabled for the given package, taking into account any overrides that
     * exist.
     *
     * @param app Info about the app in question
     * @return {@code true} if the change should be enabled for the package.
     */
    public boolean isEnabled(ApplicationInfo app) {
        if (mPackageOverrides.containsKey(app.packageName)) {
            return mPackageOverrides.get(app.packageName);
        }
        if (mDisabled) {
            return false;
        }
        if (mEnableAfterTargetSdk != -1) {
            return app.targetSdkVersion > mEnableAfterTargetSdk;
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ChangeId(")
                .append(mChangeId);
        if (mName != null) {
            sb.append("; name=").append(mName);
        }
        if (mEnableAfterTargetSdk != -1) {
            sb.append("; enableAfterTargetSdk=").append(mEnableAfterTargetSdk);
        }
        if (mDisabled) {
            sb.append("; disabled");
        }
        if (mPackageOverrides.size() > 0) {
            sb.append("; packageOverrides=").append(mPackageOverrides);
        }
        return sb.append(")").toString();
    }
}
