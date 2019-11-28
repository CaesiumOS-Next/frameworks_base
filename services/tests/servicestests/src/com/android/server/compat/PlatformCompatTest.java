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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;
import static org.testng.Assert.assertThrows;

import android.compat.Compatibility;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.compat.CompatibilityChangeConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashSet;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class PlatformCompatTest {
    private static final String PACKAGE_NAME = "my.package";
    private static final int TARGET_SDK = 10;
    private static final int TARGET_SDK_BEFORE = 9;
    private static final int TARGET_SDK_AFTER = 11;

    @Mock
    private Context mContext;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    CompatChange.ChangeListener mListener1, mListener2;
    @Mock
    PlatformCompat.AndroidBuildClassifier mBuildClassifier;
    PlatformCompat mPlatformCompat;
    CompatConfig mCompatConfig;

    static class OverridesBuilder {
        Set<Long> mEnabled;
        Set<Long> mDisabled;
        String mPackageName;

        OverridesBuilder() {
            mEnabled = new HashSet<>();
            mDisabled = new HashSet<>();
        }

        static OverridesBuilder create() {
            return new OverridesBuilder();
        }

        OverridesBuilder addEnabled(Long id) {
            mEnabled.add(id);
            return this;
        }

        OverridesBuilder addDisabled(Long id) {
            mEnabled.add(id);
            return this;
        }

        OverridesBuilder toPackage(String packageName) {
            mPackageName = packageName;
            return this;
        }

        void override(PlatformCompat platformCompat) {
            platformCompat.setOverrides(
                    new CompatibilityChangeConfig(
                            new Compatibility.ChangeConfig(mEnabled, mDisabled)), mPackageName);
        }

        void clear(PlatformCompat platformCompat) {
            platformCompat.clearOverrides(mPackageName);
        }
    }

    static class MockApplicationBuilder {
        boolean mIsDebuggable;
        int mTargetSdk;
        String mPackageName;

        MockApplicationBuilder() {
            mIsDebuggable = false;
            mTargetSdk = 0;
            mPackageName = PACKAGE_NAME;
        }

        static MockApplicationBuilder create() {
            return new MockApplicationBuilder();
        }

        MockApplicationBuilder withTargetSdk(int targetSdk) {
            mTargetSdk = targetSdk;
            return this;
        }

        MockApplicationBuilder debuggable() {
            mIsDebuggable = true;
            return this;
        }

        MockApplicationBuilder withPackageName(String packageName) {
            mPackageName = packageName;
            return this;
        }

        void mock(PackageManager pm) throws PackageManager.NameNotFoundException {
            final ApplicationInfo applicationInfo = new ApplicationInfo();
            if (mIsDebuggable) {
                applicationInfo.flags |= ApplicationInfo.FLAG_DEBUGGABLE;
            }
            applicationInfo.targetSdkVersion = mTargetSdk;
            when(pm.getApplicationInfoAsUser(eq(mPackageName), anyInt(), anyInt()))
                    .thenReturn(applicationInfo);
        }
    }

    private void setDebuggableBuild() {
        when(mBuildClassifier.isDebuggableBuild()).thenReturn(true);
    }

    private void setBetaBuild() {
        when(mBuildClassifier.isDebuggableBuild()).thenReturn(false);
        when(mBuildClassifier.isFinalBuild()).thenReturn(false);
    }

    private void setFinalBuild() {
        when(mBuildClassifier.isDebuggableBuild()).thenReturn(false);
        when(mBuildClassifier.isFinalBuild()).thenReturn(true);
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.getPackageUid(eq(PACKAGE_NAME), eq(0))).thenThrow(
                new PackageManager.NameNotFoundException());
        mCompatConfig = new CompatConfig();
        mPlatformCompat = new PlatformCompat(mContext, mCompatConfig, mBuildClassifier);
    }

    @Test
    public void testRegisterListenerToSameIdThrows() {
        setDebuggableBuild();
        // Registering a listener to change 1 is successful.
        mPlatformCompat.registerListener(1, mListener1);
        // Registering a listener to change 2 is successful.
        mPlatformCompat.registerListener(2, mListener1);
        // Trying to register another listener to change id 1 fails.
        assertThrows(IllegalStateException.class,
                () -> mPlatformCompat.registerListener(1, mListener1));
    }

    @Test
    public void testRegisterListenerReturn() {
        setDebuggableBuild();
        OverridesBuilder.create()
                .addEnabled(1L)
                .toPackage(PACKAGE_NAME)
                .override(mPlatformCompat);

        // Change id 1 is known (added in setOverrides).
        assertThat(mPlatformCompat.registerListener(1, mListener1)).isTrue();
        // Change 2 is unknown.
        assertThat(mPlatformCompat.registerListener(2, mListener1)).isFalse();
    }

    @Test
    public void testListenerCalledOnSetOverrides() {
        setDebuggableBuild();
        mPlatformCompat.registerListener(1, mListener1);
        mPlatformCompat.registerListener(2, mListener1);

        OverridesBuilder.create()
                .addEnabled(1L)
                .addDisabled(2L)
                .toPackage(PACKAGE_NAME)
                .override(mPlatformCompat);

        verify(mListener1, times(2)).onCompatChange(PACKAGE_NAME);
    }

    @Test
    public void testListenerNotCalledOnWrongPackage() {
        setDebuggableBuild();
        mPlatformCompat.registerListener(1, mListener1);
        mPlatformCompat.registerListener(2, mListener1);

        OverridesBuilder.create()
                .addEnabled(1L)
                .addDisabled(2L)
                .toPackage(PACKAGE_NAME)
                .override(mPlatformCompat);

        verify(mListener1, never()).onCompatChange("other.package");
    }

    @Test
    public void testListenerCalledOnSetOverridesTwoListeners() {
        setDebuggableBuild();
        mPlatformCompat.registerListener(1, mListener1);

        OverridesBuilder builder = OverridesBuilder.create()
                .addEnabled(1L)
                .addDisabled(2L)
                .toPackage(PACKAGE_NAME);

        builder.override(mPlatformCompat);

        verify(mListener1, times(1)).onCompatChange(PACKAGE_NAME);
        verify(mListener2, never()).onCompatChange(PACKAGE_NAME);

        reset(mListener1);
        reset(mListener2);

        mPlatformCompat.registerListener(2, mListener2);

        builder.override(mPlatformCompat);

        verify(mListener1, times(1)).onCompatChange(PACKAGE_NAME);
        verify(mListener2, times(1)).onCompatChange(PACKAGE_NAME);
    }

    @Test
    public void testListenerCalledOnSetOverridesForTest() {
        setDebuggableBuild();
        mPlatformCompat.registerListener(1, mListener1);
        mPlatformCompat.registerListener(2, mListener1);

        OverridesBuilder.create()
                .addEnabled(1L)
                .addDisabled(2L)
                .toPackage(PACKAGE_NAME)
                .override(mPlatformCompat);

        verify(mListener1, times(2)).onCompatChange(PACKAGE_NAME);
    }

    @Test
    public void testListenerCalledOnSetOverridesTwoListenersForTest() {
        setDebuggableBuild();
        mPlatformCompat.registerListener(1, mListener1);

        OverridesBuilder builder = OverridesBuilder.create()
                .addEnabled(1L)
                .addDisabled(2L)
                .toPackage(PACKAGE_NAME);

        builder.override(mPlatformCompat);

        verify(mListener1, times(1)).onCompatChange(PACKAGE_NAME);
        verify(mListener2, never()).onCompatChange(PACKAGE_NAME);

        reset(mListener1);
        reset(mListener2);

        mPlatformCompat.registerListener(2, mListener2);

        builder.override(mPlatformCompat);

        verify(mListener1, times(1)).onCompatChange(PACKAGE_NAME);
        verify(mListener2, times(1)).onCompatChange(PACKAGE_NAME);
    }

    @Test
    public void testListenerCalledOnClearOverrides() {
        setDebuggableBuild();
        mPlatformCompat.registerListener(1, mListener1);
        mPlatformCompat.registerListener(2, mListener2);

        OverridesBuilder.create()
                .addEnabled(1L)
                .toPackage(PACKAGE_NAME)
                .override(mPlatformCompat);

        verify(mListener1, times(1)).onCompatChange(PACKAGE_NAME);
        verify(mListener2, never()).onCompatChange(PACKAGE_NAME);

        reset(mListener1);
        reset(mListener2);

        mPlatformCompat.clearOverrides(PACKAGE_NAME);
        verify(mListener1, times(1)).onCompatChange(PACKAGE_NAME);
        verify(mListener2, never()).onCompatChange(PACKAGE_NAME);
    }

    @Test
    public void testListenerCalledOnClearOverridesMultipleOverrides() {
        setDebuggableBuild();
        mPlatformCompat.registerListener(1, mListener1);
        mPlatformCompat.registerListener(2, mListener2);

        OverridesBuilder.create()
                .addEnabled(1L)
                .addDisabled(2L)
                .toPackage(PACKAGE_NAME)
                .override(mPlatformCompat);
        verify(mListener1, times(1)).onCompatChange(PACKAGE_NAME);
        verify(mListener2, times(1)).onCompatChange(PACKAGE_NAME);

        reset(mListener1);
        reset(mListener2);

        mPlatformCompat.clearOverrides(PACKAGE_NAME);
        verify(mListener1, times(1)).onCompatChange(PACKAGE_NAME);
        verify(mListener2, times(1)).onCompatChange(PACKAGE_NAME);
    }

    @Test
    public void testListenerCalledOnClearOverrideExists() {
        setDebuggableBuild();
        mPlatformCompat.registerListener(1, mListener1);
        mPlatformCompat.registerListener(2, mListener2);

        OverridesBuilder.create()
                .addEnabled(1L)
                .toPackage(PACKAGE_NAME)
                .override(mPlatformCompat);
        verify(mListener1, times(1)).onCompatChange(PACKAGE_NAME);
        verify(mListener2, never()).onCompatChange(PACKAGE_NAME);

        reset(mListener1);
        reset(mListener2);

        mPlatformCompat.clearOverride(1, PACKAGE_NAME);
        verify(mListener1, times(1)).onCompatChange(PACKAGE_NAME);
        verify(mListener2, never()).onCompatChange(PACKAGE_NAME);
    }

    @Test
    public void testListenerCalledOnClearOverrideDoesntExist() {
        setDebuggableBuild();
        mPlatformCompat.registerListener(1, mListener1);

        mPlatformCompat.clearOverride(1, PACKAGE_NAME);
        // Listener not called when a non existing override is removed.
        verify(mListener1, never()).onCompatChange(PACKAGE_NAME);
    }

    @Test
    public void testAllowAnyOverrideForDebuggableBuild() throws Exception {
        setDebuggableBuild();
        MockApplicationBuilder.create()
                .debuggable()
                .withTargetSdk(TARGET_SDK)
                .mock(mPackageManager);
        ChangeConfigBuilder.create()
                .targetSdkChangeWithId(TARGET_SDK_BEFORE, 1)
                .targetSdkChangeWithId(TARGET_SDK, 2)
                .targetSdkChangeWithId(TARGET_SDK_AFTER, 3)
                .enabledChangeWithId(4)
                .disabledChangeWithId(5)
                .addAll(mCompatConfig);

        assertThat(mPlatformCompat.allowOverride(1, PACKAGE_NAME)).isTrue();
        assertThat(mPlatformCompat.allowOverride(2, PACKAGE_NAME)).isTrue();
        assertThat(mPlatformCompat.allowOverride(3, PACKAGE_NAME)).isTrue();
        assertThat(mPlatformCompat.allowOverride(4, PACKAGE_NAME)).isTrue();
        assertThat(mPlatformCompat.allowOverride(5, PACKAGE_NAME)).isTrue();
        verify(mBuildClassifier, never()).isFinalBuild(); // Should not check if it's a final build
    }

    @Test
    public void testAllowAnyTargetSdkForDebuggableAppBetaBuild() throws Exception {
        setBetaBuild();
        MockApplicationBuilder.create()
                .debuggable()
                .withTargetSdk(TARGET_SDK)
                .mock(mPackageManager);
        ChangeConfigBuilder.create()
                .targetSdkChangeWithId(TARGET_SDK_BEFORE, 1)
                .targetSdkChangeWithId(TARGET_SDK, 2)
                .targetSdkChangeWithId(TARGET_SDK_AFTER, 3)
                .addAll(mCompatConfig);

        assertThat(mPlatformCompat.allowOverride(1, PACKAGE_NAME)).isTrue();
        assertThat(mPlatformCompat.allowOverride(2, PACKAGE_NAME)).isTrue();
        assertThat(mPlatformCompat.allowOverride(3, PACKAGE_NAME)).isTrue();
    }

    @Test
    public void testRejectAnyNonTargetSdkForDebuggableAppBetaBuild() throws Exception {
        setBetaBuild();
        MockApplicationBuilder.create()
                .debuggable()
                .withTargetSdk(TARGET_SDK)
                .mock(mPackageManager);
        ChangeConfigBuilder.create()
                .enabledChangeWithId(1)
                .disabledChangeWithId(2)
                .addAll(mCompatConfig);

        assertThat(mPlatformCompat.allowOverride(1, PACKAGE_NAME)).isFalse();
        assertThat(mPlatformCompat.allowOverride(2, PACKAGE_NAME)).isFalse();
    }

    @Test
    public void testRejectAnyOverrideForNonDebuggableAppBetaBuild() throws Exception {
        setBetaBuild();
        MockApplicationBuilder.create()
                .withTargetSdk(10)
                .mock(mPackageManager);
        ChangeConfigBuilder.create()
                .targetSdkChangeWithId(TARGET_SDK_BEFORE, 1)
                .targetSdkChangeWithId(TARGET_SDK, 2)
                .targetSdkChangeWithId(TARGET_SDK_AFTER, 3)
                .enabledChangeWithId(4)
                .disabledChangeWithId(5)
                .addAll(mCompatConfig);

        assertThat(mPlatformCompat.allowOverride(1, PACKAGE_NAME)).isFalse();
        assertThat(mPlatformCompat.allowOverride(2, PACKAGE_NAME)).isFalse();
        assertThat(mPlatformCompat.allowOverride(3, PACKAGE_NAME)).isFalse();
        assertThat(mPlatformCompat.allowOverride(4, PACKAGE_NAME)).isFalse();
        assertThat(mPlatformCompat.allowOverride(5, PACKAGE_NAME)).isFalse();
    }

    @Test
    public void testAllowTargetSdkOptinForDebuggableAppFinalBuild() throws Exception {
        setFinalBuild();
        MockApplicationBuilder.create()
                .debuggable()
                .withTargetSdk(TARGET_SDK)
                .mock(mPackageManager);
        ChangeConfigBuilder.create()
                .targetSdkChangeWithId(TARGET_SDK_AFTER, 1)
                .addAll(mCompatConfig);

        assertThat(mPlatformCompat.allowOverride(1, PACKAGE_NAME)).isTrue();
    }

    @Test
    public void testRejectTargetSdkOptoutForDebuggableAppFinalBuild() throws Exception {
        setFinalBuild();
        MockApplicationBuilder.create()
                .debuggable()
                .withTargetSdk(TARGET_SDK)
                .mock(mPackageManager);
        ChangeConfigBuilder.create()
                .targetSdkChangeWithId(TARGET_SDK_BEFORE, 1)
                .targetSdkChangeWithId(TARGET_SDK, 2)
                .addAll(mCompatConfig);

        assertThat(mPlatformCompat.allowOverride(1, PACKAGE_NAME)).isFalse();
        assertThat(mPlatformCompat.allowOverride(2, PACKAGE_NAME)).isFalse();
    }

    @Test
    public void testRejectAnyNonTargetSdkForDebuggableAppFinalBuild() throws Exception {
        setFinalBuild();
        MockApplicationBuilder.create()
                .debuggable()
                .withTargetSdk(TARGET_SDK)
                .mock(mPackageManager);
        ChangeConfigBuilder.create()
                .enabledChangeWithId(1)
                .disabledChangeWithId(2)
                .addAll(mCompatConfig);

        assertThat(mPlatformCompat.allowOverride(1, PACKAGE_NAME)).isFalse();
        assertThat(mPlatformCompat.allowOverride(2, PACKAGE_NAME)).isFalse();
    }

    @Test
    public void testRejectAnyOverrideForNonDebuggableAppFinalBuild() throws Exception {
        setFinalBuild();
        MockApplicationBuilder.create()
                .withTargetSdk(10)
                .mock(mPackageManager);
        ChangeConfigBuilder.create()
                .targetSdkChangeWithId(TARGET_SDK_BEFORE, 1)
                .targetSdkChangeWithId(TARGET_SDK, 2)
                .targetSdkChangeWithId(TARGET_SDK_AFTER, 3)
                .enabledChangeWithId(4)
                .disabledChangeWithId(5)
                .addAll(mCompatConfig);

        assertThat(mPlatformCompat.allowOverride(1, PACKAGE_NAME)).isFalse();
        assertThat(mPlatformCompat.allowOverride(2, PACKAGE_NAME)).isFalse();
        assertThat(mPlatformCompat.allowOverride(3, PACKAGE_NAME)).isFalse();
        assertThat(mPlatformCompat.allowOverride(4, PACKAGE_NAME)).isFalse();
        assertThat(mPlatformCompat.allowOverride(5, PACKAGE_NAME)).isFalse();
    }

}
