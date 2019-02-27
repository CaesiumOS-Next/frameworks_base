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

package com.android.networkstack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.res.Resources;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Tests for {@link IResourceManager}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class IResourceManagerTest {
    private Resources mResources;

    @Before
    public void setUp() {
        mResources = spy(ApplicationProvider.getApplicationContext().getResources());
    }

    @Test
    public void testAppResources() throws Exception {
        // IResourceManager.make() dynamically implements all methods using provided annotations.
        // This test ensures that each method is implemented properly.
        final IResourceManager rm = IResourceManager.make(
                com.android.networkstack.R.class, mResources);

        int i = 0;
        for (Method m : IResourceManager.class.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers())) continue;
            assertNotNull(m.invoke(rm));
            i++;
            verify(mResources, times(i)).getString(anyInt());
        }
    }

    public static class TestDefinedR {
        public static class string {
            public static int config_captive_portal_http_url = 123;
        }
    }

    @Test
    public void testResourceId() {
        final String testValue = "http://example.com/test";
        doReturn(testValue).when(mResources).getString(
                TestDefinedR.string.config_captive_portal_http_url);
        final IResourceManager rm = IResourceManager.make(TestDefinedR.class, mResources);
        final String url = rm.getCaptivePortalHttpUrl();
        assertEquals(testValue, url);
    }

    public static class TestUndefinedR {
        public static class string {
            public static int config_captive_portal_http_url_unused = 456;
        }
    }

    @Test
    public void testResourceId_Undefined() {
        final IResourceManager rm = IResourceManager.make(TestUndefinedR.class, mResources);
        final String url = rm.getCaptivePortalHttpUrl();
        assertNull(url);

        verify(mResources, never()).getString(anyInt());
    }
}
