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

package com.android.vpndialogs;

import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS;

import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.VpnManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.Html;
import android.text.Html.ImageGetter;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.app.AlertActivity;
import com.android.internal.net.VpnConfig;

public class ConfirmDialog extends AlertActivity
        implements DialogInterface.OnClickListener, ImageGetter {
    private static final String TAG = "VpnConfirm";

    // Usually the label represents the app name, 150 code points might be enough to display the app
    // name, and 150 code points won't cover the warning message from VpnDialog.
    @VisibleForTesting
    static final int MAX_VPN_LABEL_LENGTH = 150;

    /**
     * Maximum count of visible units reserved for simplified VPN label.
     * Use only with {@link #getSimplifiedLabelImpl(String, String)}.
     * @hide
    */
    private static final int MAX_VISIBLE_UNITS = 30;

    @VpnManager.VpnType private final int mVpnType;

    private String mPackage;

    private VpnManager mVm;

    private View mView;


    /**
     * Pattern for matching visible units in VPN label.
     * Matches HTML entities, Unicode surrogate pairs and all other characters.
     * Use only with {@link #getSimplifiedLabelImpl(String, String)}.
     * @hide
    */
    private static final Pattern VISIBLE_UNIT_PATTERN = Pattern.compile(
            "&(?:#\\d+|#[xX][0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);" +
                "|[\\uD800-\\uDBFF][\\uDC00-\\uDFFF]" +
                "|[\\s\\S]"
    );

    public ConfirmDialog() {
        this(VpnManager.TYPE_VPN_SERVICE);
    }

    public ConfirmDialog(@VpnManager.VpnType int vpnType) {
        mVpnType = vpnType;
    }

    /**
     * This function will use the string resource to combine the VPN label and the package name.
     *
     * If the VPN label violates the length restriction, the first 30 code points of VPN label and
     * the package name will be returned. Or return the VPN label and the package name directly if
     * the VPN label doesn't violate the length restriction.
     *
     * The result will be something like,
     * - ThisIsAVeryLongVpnAppNameWhich... (com.vpn.app)
     *   if the VPN label violates the length restriction.
     * or
     * - VpnLabelWith&lt;br&gt;HtmlTag (com.vpn.app)
     *   if the VPN label doesn't violate the length restriction.
     *
     */
    private String getSimplifiedLabel(String vpnLabel, String packageName) {
        if (vpnLabel.codePointCount(0, vpnLabel.length()) > 30) {
            return getSimplifiedLabelImpl(vpnLabel, packageName);
        }

        return getString(R.string.sanitized_vpn_label, vpnLabel, packageName);
    }

    /**
     * Generates a simplified VPN label for UI display.
     *
     * <p>This method truncates by "visible units" rather than ordinary Java {@code char}s,
     * preventing multi-character units such as emoji, surrogate pairs, or combining
     * characters from being split in the middle. The truncation process is constrained
     * by the following two conditions:
     * <ul>
     *     <li>{@code MAX_VISIBLE_UNITS}: the maximum number of visible units to retain;</li>
     *     <li>{@code MAX_VPN_LABEL_LENGTH}: the character length threshold for the retained
     *     content; once reached, no further units are appended.</li>
     * </ul>
     *
     * <p>If the extracted visible content is exactly the same as the original label, the
     * original label is returned directly. Otherwise, the ellipsis-enabled resource template
     * {@code R.string.sanitized_vpn_label_with_ellipsis} is used to generate the simplified
     * display text, with the package name included as contextual information.
     *
     * @param vpnLabel    the original VPN label
     * @param packageName the package name of the app that owns the VPN, used to add source
     *                    information to the simplified label
     * @return the simplified VPN label; if no simplification is needed, returns the original label
     */
    private String getSimplifiedLabelImpl(String vpnLabel, String packageName) {
        Matcher matcher = VISIBLE_UNIT_PATTERN.matcher(vpnLabel);
        StringBuilder matched = new StringBuilder();
        int totalLength = 0;
        int visibleCount = 0;
        while (visibleCount < MAX_VISIBLE_UNITS && matcher.find()) {
            String unit = matcher.group();
            matched.append(unit);
            totalLength += unit.length();
            visibleCount ++;
            if(totalLength >= MAX_VPN_LABEL_LENGTH) {
                break;
            }
        }
        String newVpnLabel = matched.toString();
        if(newVpnLabel.equals(vpnLabel)) {
            return vpnLabel;
        }

        return getString(R.string.sanitized_vpn_label_with_ellipsis,
                matched.toString(), packageName);
    }

    @VisibleForTesting
    protected String getSanitizedVpnLabel(String vpnLabel, String packageName) {
        final String sanitizedVpnLabel = Html.escapeHtml(vpnLabel);
        final boolean exceedMaxVpnLabelLength = sanitizedVpnLabel.codePointCount(0,
                sanitizedVpnLabel.length()) > MAX_VPN_LABEL_LENGTH;
        if (exceedMaxVpnLabelLength || !vpnLabel.equals(sanitizedVpnLabel)) {
            return getSimplifiedLabel(sanitizedVpnLabel, packageName);
        }

        return sanitizedVpnLabel;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPackage = getCallingPackage();
        mVm = getSystemService(VpnManager.class);

        if (mVm.prepareVpn(mPackage, null, UserHandle.myUserId())) {
            setResult(RESULT_OK);
            finish();
            return;
        }
        if (UserManager.get(this).hasUserRestriction(UserManager.DISALLOW_CONFIG_VPN)) {
            finish();
            return;
        }
        final String alwaysOnVpnPackage = mVm.getAlwaysOnVpnPackageForUser(UserHandle.myUserId());
        // Can't prepare new vpn app when another vpn is always-on
        if (alwaysOnVpnPackage != null && !alwaysOnVpnPackage.equals(mPackage)) {
            finish();
            return;
        }
        mView = View.inflate(this, R.layout.confirm, null);
        ((TextView) mView.findViewById(R.id.warning)).setText(
                Html.fromHtml(getString(R.string.warning, getSanitizedVpnLabel(
                        getVpnLabel().toString(), mPackage)),
                        this /* imageGetter */, null /* tagHandler */));
        mAlertParams.mTitle = getText(R.string.prompt);
        mAlertParams.mPositiveButtonText = getText(android.R.string.ok);
        mAlertParams.mPositiveButtonListener = this;
        mAlertParams.mNegativeButtonText = getText(android.R.string.cancel);
        mAlertParams.mView = mView;
        setupAlert();

        getWindow().setCloseOnTouchOutside(false);
        getWindow().addPrivateFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);
        Button button = mAlert.getButton(DialogInterface.BUTTON_POSITIVE);
        button.setFilterTouchesWhenObscured(true);
    }

    @VisibleForTesting
    public CharSequence getWarningText() {
        return ((TextView) mView.findViewById(R.id.warning)).getText();
    }

    private CharSequence getVpnLabel() {
        try {
            return VpnConfig.getVpnLabel(this, mPackage);
        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Drawable getDrawable(String source) {
        // Should only reach this when fetching the VPN icon for the warning string.
        final Drawable icon = getDrawable(R.drawable.ic_vpn_dialog);
        icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());

        final TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.textColorPrimary, tv, true)) {
            icon.setTint(getColor(tv.resourceId));
        } else {
            Log.w(TAG, "Unable to resolve theme color");
        }

        return icon;
    }

    @Override
    public void onBackPressed() {
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        try {
            if (mVm.prepareVpn(null, mPackage, UserHandle.myUserId())) {
                // Authorize this app to initiate VPN connections in the future without user
                // intervention.
                mVm.setVpnPackageAuthorization(mPackage, UserHandle.myUserId(), mVpnType);
                setResult(RESULT_OK);
            }
        } catch (Exception e) {
            Log.e(TAG, "onClick", e);
        }
    }
}
