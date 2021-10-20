/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.wm;

import android.content.Context;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.ArraySet;
import android.util.Slog;
import android.util.SparseArray;
import android.view.WindowInsets;
import android.view.WindowInsets.Type.InsetsType;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Runtime adjustments applied to the global window policy.
 *
 * It can force immersive mode behavior for system bars (based on a package list).
 *
 * Control by setting {@link Settings.Global#POLICY_CONTROL} to one or more type-target expressions.
 * The strings to select types are the names of methods of {@link WindowInsets.Type} which return
 * {@link InsetsType}.
 * e.g.
 *   to force hiding system bars everywhere:
 *     "hide:systemBars=*"
 *   to force hiding status bars for all apps except a specific package:
 *     "hide:statusBars=apps,-com.package"
 *   to force hiding system bars for specific packages:
 *     "hide:systemBars=com.package.one,com.package.two"
 *
 * Separate multiple type-target expressions with ';'
 *   e.g. "hide:statusBars=apps;hide:navigationBars=com.package"
 */
public class PolicyControl {
    private static final String TAG = "PolicyControl";
    private static final boolean DEBUG = false;
    private static final SparseArray<Filter> FILTERS = new SparseArray<>();

    private static String sSettingValue;

    static boolean isHidden(@InsetsType int type, WindowState win) {
        final SparseArray<Filter> filters = FILTERS;
        for (int i = filters.size() - 1; i >= 0; i--) {
            final @InsetsType int types = filters.keyAt(i);
            if ((type & types) == 0) {
                continue;
            }
            if (filters.valueAt(i).matches(win)) {
                return true;
            }
        }
        return false;
    }

    static boolean reloadFromSetting(Context context) {
        if (DEBUG) Slog.d(TAG, "reloadFromSetting()");
        String value = null;
        try {
            value = Settings.Global.getStringForUser(context.getContentResolver(),
                    Settings.Global.POLICY_CONTROL,
                    UserHandle.USER_CURRENT);
            if (Objects.equals(sSettingValue, value)) {
                return false;
            }
            setFilters(value);
            sSettingValue = value;
        } catch (Throwable t) {
            Slog.w(TAG, "Error loading policy control, value=" + value, t);
            return false;
        }
        return true;
    }

    public static void dump(String prefix, PrintWriter pw) {
        if (sSettingValue == null) {
            return;
        }
        pw.print(prefix); pw.println("InsetsPolicyControl:");
        prefix += "  ";
        if (FILTERS.size() == 0) {
            return;
        }
        pw.print(prefix); pw.println("Filters:");
        prefix += "  ";
        for (int i = 0, size = FILTERS.size(); i < size; i++) {
            pw.print(prefix);
            pw.print(WindowInsets.Type.toString(FILTERS.keyAt(i)));
            pw.print(": ");
            FILTERS.valueAt(i).dump(pw);
        }
        pw.println();
    }

    private static void setFilters(String value) {
        if (DEBUG) Slog.d(TAG, "setFilters: " + value);
        FILTERS.clear();
        if (value != null) {
            final String[] vtts = value.split(";");
            for (String vtt : vtts) {
                final int i = vtt.indexOf('=');
                if (i == -1) {
                    if (DEBUG) Slog.w(TAG, "missing '='");
                    continue;
                }
                final String[] vt = vtt.substring(0, i).split(":");
                if (vt.length != 2) {
                    if (DEBUG) Slog.w(TAG, "missing ':'");
                    continue;
                }
                final String visibility = vt[0];
                final SparseArray<Filter> filters = visibility.equals("hide")
                        ? FILTERS
                        : null;
                if (filters == null) {
                    if (DEBUG) Slog.w(TAG, "invalid visibility");
                    continue;
                }
                final @InsetsType int types = stringToInsetsType(vt[1]);
                if (types == 0) {
                    if (DEBUG) Slog.w(TAG, "invalid types");
                    continue;
                }
                final Filter filter = Filter.parse(vtt.substring(i + 1));
                if (filter == null) {
                    if (DEBUG) Slog.w(TAG, "no targets parsed");
                    continue;
                }
                filters.put(types, filter);
                if (DEBUG) Slog.d(TAG, WindowInsets.Type.toString(types) + ": " + filter);
            }
        }
    }

    private static @InsetsType int stringToInsetsType(String typesString) {
        try {
            final Method method = WindowInsets.Type.class.getDeclaredMethod(typesString);
            if (method.isAnnotationPresent(InsetsType.class)) {
                return (int) method.invoke(null);
            }
        } catch (Exception e) {
            // We will skip the filter.
        }
        return 0;
    }

    private static class Filter {
        private static final String ALL = "*";
        private static final String APPS = "apps";

        private final ArraySet<String> mAllowList;
        private final ArraySet<String> mDenyList;

        private Filter(ArraySet<String> allowList, ArraySet<String> denyList) {
            mAllowList = allowList;
            mDenyList = denyList;
        }

        boolean matches(WindowState win) {
            if (win == null) {
                return false;
            }
            boolean isApp = win.mActivityRecord != null;
            if (isApp && mDenyList.contains(APPS)) {
                return false;
            }
            if (onDenyList(win.getOwningPackage())) {
                return false;
            }
            if (isApp && mAllowList.contains(APPS)) {
                return true;
            }
            return onAllowList(win.getOwningPackage());
        }

        private boolean onDenyList(String packageName) {
            return mDenyList.contains(packageName) || mDenyList.contains(ALL);
        }

        private boolean onAllowList(String packageName) {
            return mAllowList.contains(ALL) || mAllowList.contains(packageName);
        }

        void dump(PrintWriter pw) {
            pw.print("Filter[");
            dump("allowList", mAllowList, pw); pw.print(',');
            dump("denyList", mDenyList, pw); pw.print(']');
        }

        private void dump(String name, ArraySet<String> set, PrintWriter pw) {
            pw.print(name); pw.print("=(");
            final int n = set.size();
            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    pw.print(',');
                }
                pw.print(set.valueAt(i));
            }
            pw.print(')');
        }

        @Override
        public String toString() {
            StringWriter sw = new StringWriter();
            dump(new PrintWriter(sw, true));
            return sw.toString();
        }

        // targets = comma-delimited list of tokens, where token = (package name|apps|*)
        // e.g. "com.package1", or "apps, com.android.keyguard" or "*"
        private static Filter parse(String targets) {
            if (targets == null) {
                return null;
            }
            ArraySet<String> allowList = new ArraySet<>();
            ArraySet<String> denyList = new ArraySet<>();
            for (String token : targets.split(",")) {
                token = token.trim();
                if (token.startsWith("-") && token.length() > 1) {
                    token = token.substring(1);
                    denyList.add(token);
                } else {
                    allowList.add(token);
                }
            }
            return new Filter(allowList, denyList);
        }
    }
}
