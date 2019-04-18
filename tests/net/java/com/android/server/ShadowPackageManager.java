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

package com.android.server;

import static android.content.pm.ApplicationInfo.PRIVATE_FLAG_OEM;
import static android.content.pm.ApplicationInfo.PRIVATE_FLAG_PRODUCT;
import static android.content.pm.ApplicationInfo.PRIVATE_FLAG_VENDOR;
import static android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED;
import static android.content.pm.PackageManager.MATCH_ANY_USER;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Parcel;
import android.os.Process;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.Log;

import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A shadow package manager class that maintains its own list of installed packages and exposes
 * a mocked {@link PackageManager} instance that returns corect information about those installed
 * packages for unit tests. This class provides simple APIs to manage the installed packages
 * such as {@link #installPackage} {@link #deletePackage} and {@link #reset}), and a builder class
 * {@link PackageBuilder} to construct package info easily.
 */
public class ShadowPackageManager {
    private static final String TAG = "ShadowPackageManager";

    public static enum PartitionType {
        PARTITION_OEM, PARTITION_PRODUCT, PARTITION_VENDOR, PARTITION_SYSTEM
    }

    private static class PackageInfoHolder {
        private Set<Integer> installedUsers;
        private PackageInfo mPackageInfo;

        public PackageInfoHolder(PackageInfo packageInfo, int userId) {
            this.mPackageInfo = packageInfo;
            this.installedUsers = new ArraySet<>();
            this.installedUsers.add(userId);
        }

        int getUid(int userId) {
            return UserHandle.getUid(userId, mPackageInfo.applicationInfo.uid);
        }

        String getPackageName() {
            return mPackageInfo.packageName;
        }

        PackageInfo getPackageInfoForUser(int userId) {
            Parcel parcel = Parcel.obtain();
            // Use Parcel to clone the PackageInfo object.
            mPackageInfo.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            PackageInfo result = PackageInfo.CREATOR.createFromParcel(parcel);
            result.applicationInfo.uid = UserHandle.getUid(userId, result.applicationInfo.uid);
            return result;
        }
    }

    public static class PackageBuilder {
        private final PackageInfo mPackageInfo;

        public PackageBuilder(String packageName, int uid) {
            mPackageInfo = new PackageInfo();
            mPackageInfo.applicationInfo = new ApplicationInfo();
            mPackageInfo.packageName = packageName;
            // Normalize uid to appId for internal state, getPackageInfoForUser() should return
            // the correct uid for the calling user
            mPackageInfo.applicationInfo.uid = UserHandle.getAppId(uid);
        }

        /**
         * Set requested permissions, assume all of them are granted.
         */
        public PackageBuilder setRequestedPermissions(String... permissions) {
            if (permissions == null) permissions = new String[0];
            int[] requestedPermissionsFlags = new int[permissions.length];
            for (int i = 0; i < permissions.length; i++) {
                requestedPermissionsFlags[i] = REQUESTED_PERMISSION_GRANTED;
            }
            mPackageInfo.requestedPermissions = permissions;
            mPackageInfo.requestedPermissionsFlags = requestedPermissionsFlags;
            return this;
        }

        public PackageBuilder setTargetSdkVersion(int sdkVersion) {
            mPackageInfo.applicationInfo.targetSdkVersion = sdkVersion;
            return this;
        }

        public PackageBuilder setPartition(PartitionType partition) {
            int privateFlags = 0;
            switch (partition) {

                case PARTITION_OEM:
                    privateFlags = PRIVATE_FLAG_OEM;
                    break;
                case PARTITION_PRODUCT:
                    privateFlags = PRIVATE_FLAG_PRODUCT;
                    break;
                case PARTITION_VENDOR:
                    privateFlags = PRIVATE_FLAG_VENDOR;
                    break;
                case PARTITION_SYSTEM:
                    break;
                default:
                    fail("Invalid partition argument: " + partition);
            }
            mPackageInfo.applicationInfo.privateFlags = privateFlags;
            return this;
        }

        public PackageInfo build() {
            return mPackageInfo;
        }
    }

    private Map<String, PackageInfoHolder> mPackages = new HashMap<>();

    public void reset() {
        mPackages.clear();
    }

    public void installPackage(PackageInfo pkg, int userId) {
        PackageInfoHolder info = mPackages.get(pkg.packageName);
        if (info == null) {
            mPackages.put(pkg.packageName, new PackageInfoHolder(pkg, userId));
        } else {
            assertPackageInfoEquals("Install an existing package, but package info changed",
                    info, pkg);
            info.installedUsers.add(userId);
        }
    }

    public void dump() {
        for (PackageInfoHolder info : mPackages.values()) {
            Log.d(TAG, "package: " + info.mPackageInfo.packageName);
            Log.d(TAG, "  users: " + info.installedUsers);
        }
    }

    public int deletePackage(String packageName, int userId) {
        PackageInfoHolder info = mPackages.get(packageName);
        if (info == null) {
            Log.w(TAG, "Package " + packageName + " does not exist");
        } else if (!info.installedUsers.contains(userId)) {
            Log.w(TAG, "Package " + packageName + " is not installed on user " + userId);
        } else {
            info.installedUsers.remove(userId);
            if (info.installedUsers.size() == 0) {
                mPackages.remove(packageName);
            }
            return info.getUid(userId);
        }
        return Process.INVALID_UID;
    }

    /**
     * Return a mock PackageManager instance. The {@code userId} argument specifies the default
     * user associated wit the PackageManager instance.
     */
    public PackageManager getMock(int userId) {
        PackageManager mock = Mockito.mock(PackageManager.class, new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                throw new RuntimeException (invocation.getMethod().getName() + " is not stubbed");
            }

        });

        try {
            doAnswer(new Answer<String[]>() {
                @Override
                public String[] answer(InvocationOnMock invocation) throws Throwable {
                    Object[] args = invocation.getArguments();
                    return ShadowPackageManager.this.getPackagesForUid((int) args[0]);
                }
            }).when(mock).getPackagesForUid(anyInt());

            doAnswer(new Answer<List<PackageInfo>>() {
                @Override
                public List<PackageInfo> answer(InvocationOnMock invocation) throws Throwable {
                    Object[] args = invocation.getArguments();
                    return ShadowPackageManager.this.getInstalledPackagesAsUser((int) args[0],
                            userId);
                }
            }).when(mock).getInstalledPackages(anyInt());

            doAnswer(new Answer<PackageInfo>() {
                @Override
                public PackageInfo answer(InvocationOnMock invocation) throws Throwable {
                    Object[] args = invocation.getArguments();
                    return ShadowPackageManager.this.getPackageInfoAsUser((String) args[0],
                            (int) args[1], userId);
                }
            }).when(mock).getPackageInfo(anyString(), anyInt());

            doAnswer(new Answer<PackageInfo>() {
                @Override
                public PackageInfo answer(InvocationOnMock invocation) throws Throwable {
                    Object[] args = invocation.getArguments();
                    return ShadowPackageManager.this.getPackageInfoAsUser((String) args[0],
                            (int) args[1], (int) args[2]);
                }
            }).when(mock).getPackageInfoAsUser(anyString(), anyInt(), anyInt());

            doAnswer(new Answer<String>() {
                @Override
                public String answer(InvocationOnMock invocation) throws Throwable {
                    Object[] args = invocation.getArguments();
                    return ShadowPackageManager.this.getNameForUid((int) args[0]);
                }
            }).when(mock).getNameForUid(anyInt());
        } catch (Exception e) {}
        return mock;
    }

    private String[] getPackagesForUid(int uid) {
        final int userId = UserHandle.getUserId(uid);
        return mPackages.values().stream()
                .filter(info -> info.getUid(userId) == uid)
                .filter(info -> info.installedUsers.contains(userId))
                .map(info -> info.getPackageName())
                .toArray(String[]::new);
    }

    private List<PackageInfo> getInstalledPackagesAsUser(int flags, int userId) {
        List<PackageInfo> result = new ArrayList<>();
        for (PackageInfoHolder info : mPackages.values()) {
            if (info.installedUsers.contains(userId) || ((flags & MATCH_ANY_USER) != 0)) {
                result.add(info.getPackageInfoForUser(userId));
            }
        }
        return result;
    }

    private PackageInfo getPackageInfoAsUser(String packageName, int flags, int userId)
            throws NameNotFoundException {
        PackageInfoHolder info = mPackages.get(packageName);
        if (info == null || !info.installedUsers.contains(userId)) {
            throw new NameNotFoundException(packageName + " does not exist on user " + userId);
        }
        return info.getPackageInfoForUser(userId);
    }

    private String getNameForUid(int uid) {
        for (PackageInfoHolder info : mPackages.values()) {
            if (UserHandle.isSameApp(info.getUid(0), uid)) {
                return info.getPackageName() + ":" + UserHandle.getUserId(uid);
            }
        }
        return null;
    }

    private void assertPackageInfoEquals(String message, PackageInfoHolder expected,
            PackageInfo actual) {
        assertEquals(message, expected.mPackageInfo.packageName, actual.packageName);
        assertEquals(message, expected.mPackageInfo.applicationInfo.uid, actual.applicationInfo.uid);
        assertArrayEquals(message, expected.mPackageInfo.requestedPermissions,
                actual.requestedPermissions);
        assertArrayEquals(message, expected.mPackageInfo.requestedPermissionsFlags,
                actual.requestedPermissionsFlags);
        assertEquals(message, expected.mPackageInfo.applicationInfo.privateFlags,
                actual.applicationInfo.privateFlags);
        assertEquals(message, expected.mPackageInfo.applicationInfo.targetSdkVersion,
                actual.applicationInfo.targetSdkVersion);
    }
}
