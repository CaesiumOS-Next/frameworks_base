/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.webkit;

import android.view.View;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.content.ComponentName;
import android.content.res.Resources;
import android.os.Build;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.util.Log;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import androidx.annotation.IntDef;
import android.view.inputmethod.InputMethodManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Client to support additional functionalities for selection menu.
 */
public class PlatformSelectionActionMenuClient {

    private static final ComponentName MANAGE_APPS_COMPONENT =
            new ComponentName(
                    "com.android.settings",
                    "com.samsung.android.settings.display.SecProcessTextManageAppsFragment");
    private static final ComponentName WRITING_TOOLKIT_COMPONENT =
            new ComponentName(
                    "com.samsung.android.honeyboard",
                    "com.samsung.android.writingtoolkit.view.WritingToolkitActivity");

    private static final String STR_TEXT_MANAGER_APPS_RESOLVER = "process_text_manager_apps";
    private static final String TRANSLATOR_PACKAGE_NAME = "com.samsung.android.app.interpreter";
    private static final int MAXIMUM_BUILD_VERSION_CODE_SUPPORTED = 36;
    private static final int SCAN_TEXT_ID;
    private static final String FEATURE_FLAG_STATUS_CHECK_CLASS =
            "com.samsung.android.feature.SemFloatingFeature";
    private static final String AI_FEATURES_DISABLED_FLAG =
            "SEC_FLOATING_FEATURE_COMMON_DISABLE_NATIVE_AI";
    private static final String ACTION_WRITING_TOOLKIT =
            "com.samsung.android.intent.action.WritingToolkit";
    private static final String WRITING_TOOLKIT_HBD = "actionShowToolKitHbd";
    private static final String WRITING_TOOLKIT_SUBJECT = "toolkitSubject";
    private static final String WRITING_TOOLKIT_IS_TEXT_EDITABLE = "isTextEditable";
    private static final String WRITING_TOOLKIT_URI = "honeyboard://writing-toolkit";

    static {
        // We can not access this resource as regular resource since it is an extended resource.
        // Hence, we are using API that uses reflection.
        SCAN_TEXT_ID = Resources.getSystem().getIdentifier("writing_toolkit", "id", "android");
    }


    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            SamsungDefaultItemOrder.WRITING_TOOLKIT,
            SamsungDefaultItemOrder.CUT,
            SamsungDefaultItemOrder.COPY,
            SamsungDefaultItemOrder.PASTE,
            SamsungDefaultItemOrder.TRANSLATE,
            SamsungDefaultItemOrder.PASTE_AS_PLAIN_TEXT,
            SamsungDefaultItemOrder.SELECT_ALL,
            SamsungDefaultItemOrder.SHARE,
            SamsungDefaultItemOrder.WEB_SEARCH
    })
    public @interface SamsungDefaultItemOrder {
        int WRITING_TOOLKIT = 1;
        int CUT = 2;
        int COPY = 3;
        int PASTE = 4;
        int TRANSLATE = 5;
        int PASTE_AS_PLAIN_TEXT = 6;
        int SELECT_ALL = 7;
        int SHARE = 8;
        int WEB_SEARCH = 9;
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            InputDefaultItemOrder.CUT,
            InputDefaultItemOrder.COPY,
            InputDefaultItemOrder.PASTE,
            InputDefaultItemOrder.PASTE_AS_PLAIN_TEXT,
            InputDefaultItemOrder.SHARE,
            InputDefaultItemOrder.SELECT_ALL,
            InputDefaultItemOrder.WEB_SEARCH
    })
    public @interface InputDefaultItemOrder {
        int CUT = 1;
        int COPY = 2;
        int PASTE = 3;
        int PASTE_AS_PLAIN_TEXT = 4;
        int SHARE = 5;
        int SELECT_ALL = 6;
        int WEB_SEARCH = 7;
    }

    /**
     * This method and InputDefaultItemOrder need to be kept in sync with chromium ordering
     * and is responsibility of framework to maintain as and when required.
     * @param order default menu item order for which platform menu item order is needed.
     * @return platform menu item order corresponding to input menu item order.
     */
    /**
     * Maps Chromium's default menu item order to Samsung's platform-specific order.
     *
     * @param order The Chromium menu item order constant from InputDefaultItemOrder
     * @return The corresponding Samsung menu item order constant from SamsungDefaultItemOrder,
     *         or -1 if no mapping exists
     */
    public static int getPlatformDefaultMenuItemOrder(int order) {
        Log.i("Anukul"," order recv = "+order);

        if (order == InputDefaultItemOrder.CUT) {
            return SamsungDefaultItemOrder.CUT;
        } else if (order == InputDefaultItemOrder.COPY) {
            return SamsungDefaultItemOrder.COPY;
        } else if (order == InputDefaultItemOrder.PASTE) {
            return SamsungDefaultItemOrder.PASTE;
        } else if (order == InputDefaultItemOrder.SELECT_ALL) {
            return SamsungDefaultItemOrder.SELECT_ALL;
        } else if (order == InputDefaultItemOrder.SHARE) {
            return SamsungDefaultItemOrder.SHARE;
        } else if (order == InputDefaultItemOrder.PASTE_AS_PLAIN_TEXT) {
            return SamsungDefaultItemOrder.PASTE_AS_PLAIN_TEXT;
        } else if (order == InputDefaultItemOrder.WEB_SEARCH) {
            return SamsungDefaultItemOrder.WEB_SEARCH;
        }
        return -1;
    }

    /**
     * Returns additional text processing items if supported by platform.
     */
    /**
     * Gets additional text processing menu items supported by the platform.
     *
     * @return PlatformMenuData containing the "Manage Apps" menu item that allows
     *         users to configure text processing applications
     */
    public PlatformMenuData getAdditionalTextProcessingItems() {
        return new PlatformMenuData("Manage Apps",Menu.NONE,0,Menu.CATEGORY_SECONDARY,null,createManageAppsIntent(),false);
    }

    /**
     * Filters text processing activities based on user preferences from Manage Apps settings.
     *
     * @param context The application context
     * @param activities The list of ResolveInfo objects representing available text processing activities
     * @return Filtered list containing only activities enabled in Manage Apps settings,
     *         or empty list if none are enabled or user hasn't configured preferences
     */
    public List<ResolveInfo> filterTextProcessingActivities(Context context, List<ResolveInfo> activities) {
        if (!isManageAppsSupported(context)) {
            return activities;
        }
        String textManagerApps;
        try {
            // textManagerApps contains list of apps enabled in Manage apps fragment.
            // 1. A return value of null means user has not visited Manage apps fragment yet.
            // 2. A return value of empty means user has visited Manage apps fragment and has
            //    disabled all apps
            textManagerApps =
                    Settings.Global.getString(
                            context.getContentResolver(), STR_TEXT_MANAGER_APPS_RESOLVER);
        } catch (SecurityException e) {
            return activities;
        }
        if (textManagerApps == null || textManagerApps.isEmpty()) {
            // Return empty list if no text process menu item is enabled or user has never used
            // Manage apps, Settings#getString query on content resolver returns null in such case.
            return new ArrayList<>();
        }
        List<String> splitTextManagerApps = Arrays.asList(textManagerApps.split("#"));
        List<ResolveInfo> updatedSupportedItems = new ArrayList<>();
        for (int i = 0; i < activities.size(); i++) {
            ResolveInfo resolveInfo = activities.get(i);
            if (resolveInfo.activityInfo == null
                    || (!splitTextManagerApps.contains(resolveInfo.activityInfo.packageName)
                    && !splitTextManagerApps.contains(resolveInfo.activityInfo.name))) {
                continue;
            }
            updatedSupportedItems.add(resolveInfo);
        }
        return updatedSupportedItems;
    }

    /**
     * Gets platform-specific additional menu items for text selection.
     *
     * @param context The application context
     * @param isSelectionPassword Whether the selected text is from a password field
     * @param isSelectionReadOnly Whether the selected text is from a read-only field
     * @param selectedText The currently selected text
     * @return List of PlatformMenuData objects representing additional menu items,
     *         including Translate and Writing Toolkit if available and applicable
     */
    public List<PlatformMenuData> getAdditionalMenuItems(Context context, boolean isSelectionPassword,
                                                         boolean isSelectionReadOnly,
                                                         String selectedText) {
        List<PlatformMenuData> menuDataList = new ArrayList<>();
        if(shouldAddTranslateMenu(context,selectedText,isSelectionPassword)) {
            // Get list of apps registered for text processing.
            List<ResolveInfo> textProcessActivities =
                    queryIntentActivities(context,createProcessTextIntent(), 0);
            // Identify and get ResolveInfo for Translate app.
            ResolveInfo translateResolveInfo = null;
            for (ResolveInfo resolveInfo : textProcessActivities) {
                if (resolveInfo.activityInfo.packageName.equals(TRANSLATOR_PACKAGE_NAME)) {
                    translateResolveInfo = resolveInfo;
                    break;
                }
            }
            if (translateResolveInfo == null) {
                // Do not add Translate menu if resolve info is not available.
                return menuDataList;
            }
            // Create menu item from Translate app resolve info and then add to default menu.
            menuDataList.add(
                    new PlatformMenuData(
                            translateResolveInfo.loadLabel(
                                    context.getPackageManager()), Menu.NONE,0, SamsungDefaultItemOrder.TRANSLATE,
                            getTranslationActionClickListener(context,
                                    selectedText, translateResolveInfo),null, false));
        }

        if(shouldAddWritingToolkitMenu(context,selectedText,isSelectionPassword)) {
            menuDataList.add(
                    new PlatformMenuData(SCAN_TEXT_ID,SCAN_TEXT_ID,0, SamsungDefaultItemOrder.WRITING_TOOLKIT,
                            null,createWritingToolkitIntent(selectedText,isSelectionReadOnly),false));
        }

        return menuDataList;
    }

    private static Intent createWritingToolkitIntent(
            String selectedText, boolean isSelectionReadOnly) {
        final int MAX_SHARE_QUERY_LENGTH_BYTES = 100000;
        String textForProcessing =
                (selectedText.length() >= MAX_SHARE_QUERY_LENGTH_BYTES)
                        ? selectedText.substring(0, MAX_SHARE_QUERY_LENGTH_BYTES) + "&"
                        : selectedText;
        Intent intent =
                new Intent()
                        .setAction(ACTION_WRITING_TOOLKIT)
                        .setData(Uri.parse(WRITING_TOOLKIT_URI))
                        .putExtra(WRITING_TOOLKIT_SUBJECT, textForProcessing)
                        .putExtra(WRITING_TOOLKIT_IS_TEXT_EDITABLE, !isSelectionReadOnly);
        intent.setFlags(0);
        return intent;
    }



    /**
     * Handles click events for Samsung-specific menu items.
     *
     * @param context The application context
     * @param item The menu item that was clicked
     * @param containerView The view containing the selection
     * @return true if the click was handled, false otherwise
     */
    public static boolean handleMenuItemClick(
            Context context, MenuItem item, ViewGroup containerView) {
        Intent intent = item.getIntent();
        assert intent != null : "Samsung menu item should have Intent.";
        startActivity(context,intent);
        return true;
    }

    /**
     * Checks if a menu item is a Samsung-specific custom item that can be handled.
     *
     * @param item The menu item to check
     * @return true if the item is a Samsung Writing Toolkit menu item, false otherwise
     */
    public static boolean canHandleCustomMenuItem(MenuItem item) {
        return SCAN_TEXT_ID != 0 && item.getItemId() == SCAN_TEXT_ID;
    }

    /**
     * Prepares the system for handling Samsung-specific menu items.
     * Sends a private command to the input method manager to initialize Writing Toolkit.
     *
     * @param context The application context
     * @param containerView The view containing the selection
     */
    public static void prepareForMenuHandling(Context context,View containerView) {
        InputMethodManager inputMethodManager =
                (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.sendAppPrivateCommand(
                    containerView, WRITING_TOOLKIT_HBD, /* data= */ null);
        }
    }


    /**
     * Determines whether the Translate menu should be added to the selection menu.
     *
     * @param context The application context
     * @param selectedText The currently selected text
     * @param isSelectionPassword Whether the selection is from a password field
     * @return true if Translate menu should be added, false otherwise
     */
    public static boolean shouldAddTranslateMenu(
            Context context,String selectedText, boolean isSelectionPassword) {
        return isManageAppsSupported(context)
                && isPackageInstalled(context,TRANSLATOR_PACKAGE_NAME)
                && !selectedText.isEmpty()
                && !isSelectionPassword;
    }

    private static boolean shouldAddWritingToolkitMenu(Context context, String selectedText,boolean isSelectionPassword) {
        return isSamsungDevice()
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
                && Build.VERSION.SDK_INT <= MAXIMUM_BUILD_VERSION_CODE_SUPPORTED
                && !selectedText.isEmpty()
                && !isSelectionPassword
                && SCAN_TEXT_ID != 0
                && isWritingToolkitActivityAvailable(context)
                && !isAiFeaturesDisabled();
    }

    private static boolean isWritingToolkitActivityAvailable(Context context) {
        Intent intent = new Intent();
        intent.setComponent(WRITING_TOOLKIT_COMPONENT);
        return !queryIntentActivities(context,intent, PackageManager.MATCH_ALL)
                .isEmpty();
    }

    private static boolean isAiFeaturesDisabled() {
        Boolean aiFeaturesDisabled = false;
        try {
            Class<?> featureStatusCheckClass = Class.forName(FEATURE_FLAG_STATUS_CHECK_CLASS);
            // Executing Reflection:
            // SemFloatingFeature.getInstance().getBoolean(AI_FEATURES_DISABLED_FLAG);
            // While invoking getInstance method argument is passed null since it is a
            // static method.
            aiFeaturesDisabled =
                    (Boolean)
                            featureStatusCheckClass
                                    .getMethod("getBoolean", String.class)
                                    .invoke(
                                            featureStatusCheckClass
                                                    .getMethod("getInstance")
                                                    .invoke(/* obj= */ null),
                                            AI_FEATURES_DISABLED_FLAG);
        } catch (ClassNotFoundException
                 | NoSuchMethodException
                 | InvocationTargetException
                 | IllegalAccessException e) {
            aiFeaturesDisabled = true;
        }
        return Boolean.TRUE.equals(aiFeaturesDisabled);
    }

    /**
     * Checks whether a cached selection menu can be reused.
     *
     * @param context The application context
     * @return true if the menu can be reused (when Manage Apps is not supported),
     *         false if the menu needs to be rebuilt
     */
    public boolean canReuseCachedSelectionMenu(Context context) {
        return !isManageAppsSupported(context);
    }

    private static View.OnClickListener getTranslationActionClickListener(
            Context context,String selectedText, ResolveInfo info) {
        /**
         * Android Intent size limitations prevent sending over a megabyte of data. Limit query lengths
         * to 100kB because other things may be added to the Intent.
         */
        final int MAX_SHARE_QUERY_LENGTH_BYTES = 100000;
        return v -> {
            String textForProcessing =
                    (selectedText.length() >= MAX_SHARE_QUERY_LENGTH_BYTES)
                            ? selectedText.substring(0, MAX_SHARE_QUERY_LENGTH_BYTES) + "&"
                            : selectedText;
            Intent intent =
                    createProcessTextIntent()
                            .setClassName(info.activityInfo.packageName, info.activityInfo.name)
                            .putExtra(Intent.EXTRA_PROCESS_TEXT, textForProcessing);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(context,intent);
        };
    }

    private static void startActivity(Context context,Intent intent) {
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
        }
    }

    private static Intent createManageAppsIntent() {
        return new Intent().setComponent(MANAGE_APPS_COMPONENT);
    }

    private static boolean isManageAppsSupported(Context context) {
        if (!isSamsungDevice()
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                || Build.VERSION.SDK_INT > Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return false;
        }
        List<ResolveInfo> list =
                context.getPackageManager()
                        .queryIntentActivities(
                                createManageAppsIntent(), PackageManager.MATCH_DEFAULT_ONLY);
        return !list.isEmpty();
    }

    private static boolean isSamsungDevice() {
        return "SAMSUNG".equalsIgnoreCase(Build.MANUFACTURER);
    }

    private static Intent createProcessTextIntent() {
        return new Intent().setAction(Intent.ACTION_PROCESS_TEXT).setType("text/plain");
    }

    private static boolean isPackageInstalled(Context context,String packageName) {
        return getPackageInfo(context,packageName, 0) != null;
    }

    private static PackageInfo getPackageInfo(Context context,String packageName, int flags) {
        PackageManager pm = context.getPackageManager();
        try {
            return pm.getPackageInfo(packageName, flags);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static List<ResolveInfo> queryIntentActivities(Context context,Intent intent, int flags) {
        PackageManager pm = context.getPackageManager();
        return pm.queryIntentActivities(intent, flags);
    }


    /**
     * Represents platform-specific menu data for selection actions.
     * Contains all necessary information to create and handle menu items.
     */
    public class PlatformMenuData {
        private final CharSequence mTitle; // Display title of the menu item
        private final int mTitleres; // Resource ID for the title (if using string resource)
        private final int mId; // Unique identifier for the menu item
        private final int mIconAttr; // Resource attribute for the menu icon
        private final int mOrderInCategory; // Order position within its menu category
        private final View.OnClickListener mClickListener; // Click handler for the menu item
        private final Intent mIntent; // Intent to launch when menu item is selected
        private final boolean mOnlyNeedOrderingChange; // Flag indicating if only ordering needs update
        /**
         * Constructs PlatformMenuData with a CharSequence title.
         *
         * @param title The display title of the menu item
         * @param id Unique identifier for the menu item
         * @param icon Resource attribute for the menu icon
         * @param order Order position within its menu category
         * @param clicklistener Click handler for the menu item
         * @param intent Intent to launch when menu item is selected
         * @param onlyNeedOrderingChange Flag indicating if only ordering needs update
         */
        PlatformMenuData(CharSequence title, int id,int icon,int order,View.OnClickListener clicklistener,Intent intent,boolean onlyNeedOrderingChange) {
            mTitle = title;
            mTitleres = 0;
            mId = id;
            mIconAttr = icon;
            mOrderInCategory = order;
            mClickListener = clicklistener;
            mIntent = intent;
            mOnlyNeedOrderingChange = onlyNeedOrderingChange;
        }

        /**
         * Constructs PlatformMenuData with a title resource ID.
         *
         * @param titleres Resource ID for the title string
         * @param id Unique identifier for the menu item
         * @param icon Resource attribute for the menu icon
         * @param order Order position within its menu category
         * @param clicklistener Click handler for the menu item
         * @param intent Intent to launch when menu item is selected
         * @param onlyNeedOrderingChange Flag indicating if only ordering needs update
         */
        PlatformMenuData(int titleres, int id,int icon,int order,View.OnClickListener clicklistener,Intent intent, boolean onlyNeedOrderingChange) {
            mTitleres = titleres;
            mTitle = null;
            mId = id;
            mIconAttr = icon;
            mOrderInCategory = order;
            mClickListener = clicklistener;
            mIntent = intent;
            mOnlyNeedOrderingChange = onlyNeedOrderingChange;
        }

        /**
         * Gets the menu item's display title.
         * @return The title as a CharSequence
         */
        public CharSequence getTitle() {
            return mTitle;
        }

        /**
         * Gets the menu item's title resource ID.
         * @return The string resource ID for the title
         */
        public int getTitleres() {
            return mTitleres;
        }

        /**
         * Gets the menu item's unique identifier.
         * @return The item ID
         */
        public int getId() {
            return mId;
        }

        /**
         * Gets the menu item's icon resource attribute.
         * @return The icon resource attribute
         */
        public int getIconAttr() {
            return mIconAttr;
        }

        /**
         * Gets the menu item's order position within its category.
         * @return The order position
         */
        public int getOrderInCategory() {
            return mOrderInCategory;
        }

        /**
         * Gets the menu item's click handler.
         * @return The OnClickListener for this menu ite
         */
        public View.OnClickListener getClickListener() {
            return mClickListener;
        }

        /**
         * Creates the Intent associated with this menu item.
         * @return The Intent to be launched when this menu item is selected
         */
        public Intent createMenuIntent() {return mIntent;}

        /**
         * Checks if this menu item only needs an ordering change.
         * @return true if only ordering needs update, false otherwise
         */
        public boolean needsOnlyOrderingChange() {return mOnlyNeedOrderingChange;}
    }
}