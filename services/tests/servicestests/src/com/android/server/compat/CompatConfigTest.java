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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import android.content.pm.ApplicationInfo;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.compat.IOverrideValidator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class CompatConfigTest {

    @Mock
    private IOverrideValidator mAlwaysValid;
    @Mock
    private IOverrideValidator mAlwaysInvalid;
    private CompatConfig mCompatConfig;

    private File createTempDir() {
        String base = System.getProperty("java.io.tmpdir");
        File dir = new File(base, UUID.randomUUID().toString());
        assertThat(dir.mkdirs()).isTrue();
        return dir;
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mAlwaysValid.allowOverride(anyLong(), anyString())).thenReturn(true);
        when(mAlwaysInvalid.allowOverride(anyLong(), anyString())).thenReturn(false);
        mCompatConfig = new CompatConfig();
    }

    @Test
    public void testUnknownChangeEnabled() {
        ApplicationInfo app = ApplicationBuilder.create()
                .withPackageName("com.some.package")
                .toApplicationInfo();
        assertThat(mCompatConfig.isChangeEnabled(1234L, app)).isTrue();
    }

    @Test
    public void testDisabledChangeDisabled() {
        final long changeId = 1234L;
        ChangeConfigBuilder.create()
                .disabledChangeWithId(changeId)
                .addAll(mCompatConfig);
        ApplicationInfo app = ApplicationBuilder.create()
                .withPackageName("com.some.package")
                .toApplicationInfo();

        assertThat(mCompatConfig.isChangeEnabled(changeId, app)).isFalse();
    }

    @Test
    public void testTargetSdkChangeDisabled() {
        final long changeId = 1234L;
        final int targetSdk = 2;
        ChangeConfigBuilder.create()
                .targetSdkChangeWithId(targetSdk, changeId)
                .addAll(mCompatConfig);
        ApplicationInfo app = ApplicationBuilder.create()
                .withPackageName("com.some.package")
                .withTargetSdk(targetSdk)
                .toApplicationInfo();

        assertThat(mCompatConfig.isChangeEnabled(changeId, app)).isFalse();
    }

    @Test
    public void testTargetSdkChangeEnabled() {
        final long changeId = 1234L;
        final int targetSdk = 2;
        final int targetSdkHigher = 3;
        ChangeConfigBuilder.create()
                .targetSdkChangeWithId(targetSdk, changeId)
                .addAll(mCompatConfig);
        ApplicationInfo app = ApplicationBuilder.create()
                .withPackageName("com.some.package")
                .withTargetSdk(targetSdkHigher)
                .toApplicationInfo();

        assertThat(mCompatConfig.isChangeEnabled(changeId, app)).isTrue();
    }

    @Test
    public void testDisabledOverrideTargetSdkChange() {
        final long changeId = 1234L;
        final int targetSdk = 2;
        final int targetSdkHigher = 3;
        ChangeConfigBuilder.create()
                .targetSdkDisabledChangeWithId(targetSdk, changeId)
                .addAll(mCompatConfig);
        ApplicationInfo app = ApplicationBuilder.create()
                .withPackageName("com.some.package")
                .withTargetSdk(targetSdkHigher)
                .toApplicationInfo();

        assertThat(mCompatConfig.isChangeEnabled(changeId, app)).isFalse();
    }

    @Test
    public void testGetDisabledChanges() {
        final long disabledChangeId = 1234L;
        final long otherChangeId = 2345L;
        ChangeConfigBuilder.create()
                .disabledChangeWithId(disabledChangeId)
                .enabledChangeWithId(otherChangeId)
                .addAll(mCompatConfig);
        ApplicationInfo app = ApplicationBuilder.create()
                .withPackageName("com.some.package")
                .withTargetSdk(2)
                .toApplicationInfo();

        assertThat(mCompatConfig.getDisabledChanges(app)).asList().containsExactly(
                disabledChangeId);
    }

    @Test
    public void testGetDisabledChangesSorted() {
        final long highestChangeId = 1234L;
        final long middleChangeId = 123L;
        final long lowestChangeId = 12L;
        ChangeConfigBuilder.create()
                .disabledChangeWithId(highestChangeId)
                .disabledChangeWithId(middleChangeId)
                .disabledChangeWithId(lowestChangeId)
                .addAll(mCompatConfig);
        ApplicationInfo app = ApplicationBuilder.create()
                .withPackageName("com.some.package")
                .withTargetSdk(2)
                .toApplicationInfo();

        assertThat(mCompatConfig.getDisabledChanges(app)).asList()
                .containsExactly(lowestChangeId, middleChangeId, highestChangeId);
    }

    @Test
    public void testPackageOverrideEnabled() {
        final long changeId = 1234L;
        final String appPackage = "com.some.package";
        final String otherAppPackage = "com.other.package";
        ChangeConfigBuilder.create()
                .disabledChangeWithId(changeId)
                .addAll(mCompatConfig);
        ApplicationInfo app = ApplicationBuilder.create()
                .withPackageName(appPackage)
                .withTargetSdk(2)
                .toApplicationInfo();
        ApplicationInfo otherApp = ApplicationBuilder.create()
                .withPackageName(otherAppPackage)
                .withTargetSdk(2)
                .toApplicationInfo();
        OverridesBuilder.create()
                .enable(changeId)
                .toPackage(appPackage)
                .override(mCompatConfig, mAlwaysValid);

        assertThat(mCompatConfig.isChangeEnabled(changeId, app)).isTrue();
        assertThat(mCompatConfig.isChangeEnabled(changeId, otherApp)).isFalse();
    }

    @Test
    public void testPackageOverrideDisabled() {
        final long changeId = 1234L;
        final String appPackage = "com.some.package";
        final String otherAppPackage = "com.other.package";
        ChangeConfigBuilder.create()
                .enabledChangeWithId(changeId)
                .addAll(mCompatConfig);
        ApplicationInfo app = ApplicationBuilder.create()
                .withPackageName(appPackage)
                .toApplicationInfo();
        ApplicationInfo otherApp = ApplicationBuilder.create()
                .withPackageName(otherAppPackage)
                .toApplicationInfo();
        OverridesBuilder.create()
                .disable(changeId)
                .toPackage(appPackage)
                .override(mCompatConfig, mAlwaysValid);
        assertThat(mCompatConfig.isChangeEnabled(changeId, app)).isFalse();
        assertThat(mCompatConfig.isChangeEnabled(changeId, otherApp)).isTrue();
    }

    @Test
    public void testPackageOverrideUnknownPackage() {
        final long changeId = 1234L;
        final String appPackage = "com.some.package";
        final String otherAppPackage = "com.other.package";
        ApplicationInfo app = ApplicationBuilder.create()
                .withPackageName(appPackage)
                .withTargetSdk(2)
                .toApplicationInfo();
        ApplicationInfo otherApp = ApplicationBuilder.create()
                .withPackageName(otherAppPackage)
                .withTargetSdk(2)
                .toApplicationInfo();
        OverridesBuilder.create()
                .disable(changeId)
                .toPackage(appPackage)
                .override(mCompatConfig, mAlwaysValid);

        assertThat(mCompatConfig.isChangeEnabled(changeId, app)).isFalse();
        assertThat(mCompatConfig.isChangeEnabled(changeId, otherApp)).isTrue();
    }

    @Test
    public void testPreventAddOverride() {
        final long changeId = 1234L;
        ChangeConfigBuilder.create()
                .disabledChangeWithId(changeId)
                .addAll(mCompatConfig);
        ApplicationInfo app = ApplicationBuilder.create()
                .withPackageName("com.some.package")
                .toApplicationInfo();
        OverridesBuilder.create()
                .enable(changeId)
                .toPackage("com.some.package")
                .override(mCompatConfig, mAlwaysInvalid);

        assertThat(mCompatConfig.isChangeEnabled(1234L, app)).isFalse();
    }

    @Test
    public void testPreventRemoveOverride() {
        final long changeId = 1234L;
        final String packageName = "com.some.package";
        ChangeConfigBuilder.create()
                .disabledChangeWithId(changeId)
                .addAll(mCompatConfig);
        ApplicationInfo app = ApplicationBuilder.create()
                .withPackageName(packageName)
                .toApplicationInfo();
        OverridesBuilder.create()
                .enable(changeId)
                .toPackage(packageName)
                .override(mCompatConfig, mAlwaysValid);
        // Validator allows turning on the change.
        assertThat(mCompatConfig.isChangeEnabled(changeId, app)).isTrue();
        // Try to turn off change, but validator prevents it
        mCompatConfig.removeOverride(changeId, packageName, mAlwaysInvalid);
        assertThat(mCompatConfig.isChangeEnabled(changeId, app)).isTrue();
    }

    @Test
    public void testPackageOverrideUnknownChange() {
        ApplicationInfo app = ApplicationBuilder.create()
                .withPackageName("com.some.package")
                .withTargetSdk(1)
                .toApplicationInfo();
        assertThat(mCompatConfig.isChangeEnabled(1234L, app)).isTrue();
    }

    @Test
    public void testRemovePackageOverride() {
        final long changeId = 1234L;
        final String packageName = "com.some.package";
        ChangeConfigBuilder.create()
                .enabledChangeWithId(changeId)
                .addAll(mCompatConfig);
        ApplicationInfo app = ApplicationBuilder.create()
                .withPackageName(packageName)
                .toApplicationInfo();

        assertThat(mCompatConfig.addOverride(changeId, packageName, false, mAlwaysValid))
                .isTrue();
        assertThat(mCompatConfig.isChangeEnabled(changeId, app)).isFalse();

        mCompatConfig.removeOverride(changeId, packageName, mAlwaysValid);
        assertThat(mCompatConfig.isChangeEnabled(changeId, app)).isTrue();
    }

    @Test
    public void testLookupChangeId() {
        final long changeId = 1234L;
        final String changeName = "MY_CHANGE";
        final long otherChangeId = 2345L;
        final String otherChangeName = "MY_CHANGE";
        ChangeConfigBuilder.create()
                .enabledChangeWithIdAndName(changeId, changeName)
                .enabledChangeWithIdAndName(otherChangeId, otherChangeName)
                .addAll(mCompatConfig);

        assertThat(mCompatConfig.lookupChangeId(changeName)).isEqualTo(changeId);
    }

    @Test
    public void testLookupChangeIdNotPresent() {
        assertThat(mCompatConfig.lookupChangeId("MY_CHANGE")).isEqualTo(-1L);
    }

    @Test
    public void testReadConfig() {
        File dir = createTempDir();
        ChangeConfigBuilder.create()
                .targetSdkChangeWithId(2, 1234L)
                .disabledChangeWithId(1235L)
                .enabledChangeWithId(1236)
                .saveToFile(dir, "/platform_compat_config.xml");
        mCompatConfig.initConfigFromLib(dir);

        ApplicationInfo appTargetSdk1 = ApplicationBuilder.create()
                .withPackageName("com.some.package")
                .withTargetSdk(1)
                .toApplicationInfo();
        ApplicationInfo appTargetSdk3 = ApplicationBuilder.create()
                .withPackageName("com.some.package")
                .withTargetSdk(3)
                .toApplicationInfo();
        ApplicationInfo appTargetSdk5 = ApplicationBuilder.create()
                .withPackageName("com.some.package")
                .withTargetSdk(5)
                .toApplicationInfo();

        assertThat(mCompatConfig.isChangeEnabled(1234L, appTargetSdk1)).isFalse();
        assertThat(mCompatConfig.isChangeEnabled(1234L, appTargetSdk3)).isTrue();
        assertThat(mCompatConfig.isChangeEnabled(1235L, appTargetSdk5)).isFalse();
        assertThat(mCompatConfig.isChangeEnabled(1236L, appTargetSdk1)).isTrue();
    }

    @Test
    public void testReadConfigMultipleFiles() {
        File dir = createTempDir();
        ChangeConfigBuilder.create()
                .targetSdkChangeWithId(2, 1234L)
                .saveToFile(dir, "/libcore_platform_compat_config.xml");
        ChangeConfigBuilder.create()
                .disabledChangeWithId(1235L)
                .saveToFile(dir, "/frameworks_platform_compat_config.xml");
        mCompatConfig.initConfigFromLib(dir);

        ApplicationInfo appTargetSdk1 = ApplicationBuilder.create()
                .withPackageName("com.some.package")
                .withTargetSdk(1)
                .toApplicationInfo();
        ApplicationInfo appTargetSdk3 = ApplicationBuilder.create()
                .withPackageName("com.some.package")
                .withTargetSdk(3)
                .toApplicationInfo();
        ApplicationInfo appTargetSdk5 = ApplicationBuilder.create()
                .withPackageName("com.some.package")
                .withTargetSdk(5)
                .toApplicationInfo();

        assertThat(mCompatConfig.isChangeEnabled(1234L, appTargetSdk1)).isFalse();
        assertThat(mCompatConfig.isChangeEnabled(1234L, appTargetSdk3)).isTrue();
        assertThat(mCompatConfig.isChangeEnabled(1235L, appTargetSdk5)).isFalse();
        assertThat(mCompatConfig.isChangeEnabled(1236L, appTargetSdk1)).isTrue();
    }
}
