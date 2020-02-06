/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.server.UserspaceRebootLogger.ENCRYPTION_STATE_LOCKED;
import static com.android.server.UserspaceRebootLogger.ENCRYPTION_STATE_UNLOCKED;
import static com.android.server.UserspaceRebootLogger.OUTCOME_SUCCESS;

import static org.mockito.Mockito.spy;

import android.os.SystemProperties;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.MockitoSession;

/**
 * Tests for {@link UserspaceRebootLogger}.
 */
@RunWith(JUnit4.class)
public class UserspaceRebootLoggerTest {

    private MockitoSession mMockingSession;

    @Before
    public void setUp() {
        mMockingSession = mockitoSession()
                .mockStatic(UserspaceRebootLogger.class)
                .mockStatic(SystemProperties.class)
                .startMocking();
    }

    @After
    public void tearDown() {
        mMockingSession.finishMocking();
    }

    @Test
    public void testLogEventAsync_success_userLocked() {
        UserspaceRebootLogger logger = spy(new UserspaceRebootLogger(true, 23, 37, "ignored"));
        logger.logEventAsync(false, MoreExecutors.directExecutor());
        verify(logger).log(OUTCOME_SUCCESS, 14, ENCRYPTION_STATE_LOCKED);
    }

    @Test
    public void testLogEventAsync_success_userUnlocked() {
        UserspaceRebootLogger logger = spy(new UserspaceRebootLogger(true, 23, 37, "ignored"));
        logger.logEventAsync(true, MoreExecutors.directExecutor());
        verify(logger).log(OUTCOME_SUCCESS, 14, ENCRYPTION_STATE_UNLOCKED);
    }
}
