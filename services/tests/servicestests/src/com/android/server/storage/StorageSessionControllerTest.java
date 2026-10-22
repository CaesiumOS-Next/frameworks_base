/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.storage;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.util.SparseArray;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class StorageSessionControllerTest {
    private static final int USER_ID = 10;
    private static final int PROFILE_ID = 11;

    private StorageSessionController mController;
    private StorageUserConnection mConnection;
    private SparseArray<StorageUserConnection> mConnections;
    private HandlerThread mWorker;
        private Context mContext;
        private static final ComponentName COMPONENT =
            new ComponentName("test.package", "test.Service");

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        mContext = mock(Context.class);
        mController = new StorageSessionController(mContext);
        Field component = StorageSessionController.class.getDeclaredField(
            "mExternalStorageServiceComponent");
        component.setAccessible(true);
        component.set(mController, COMPONENT);
        Field connections = StorageSessionController.class.getDeclaredField("mConnections");
        connections.setAccessible(true);
        mConnections = (SparseArray<StorageUserConnection>) connections.get(mController);
        mConnection = new StorageUserConnection(mContext, USER_ID, mController);
        mConnections.put(USER_ID, mConnection);
        Field worker = StorageUserConnection.class.getDeclaredField("mHandlerThread");
        worker.setAccessible(true);
        mWorker = (HandlerThread) worker.get(mConnection);
    }

    @After
    public void tearDown() throws Exception {
        mConnection.close();
        mWorker.join(5000);
        assertThat(mWorker.isAlive()).isFalse();
    }

    @Test
    public void userRemovalClosesAndEvictsConnection() throws Exception {
        mController.onUserRemoved(USER_ID);

        mWorker.join(5000);
        assertThat(mWorker.isAlive()).isFalse();
        assertThat(mConnections.get(USER_ID)).isNull();
    }

    @Test
    public void userStoppingPreservesConnectionForRestart() {
        mController.onUserStopping(USER_ID);
        mController.onUserStopping(USER_ID);

        assertThat(mConnections.get(USER_ID)).isSameInstanceAs(mConnection);
        assertThat(mWorker.isAlive()).isTrue();
    }

    @Test
    public void sharedProfileRemovalPreservesParentConnection() {
        mController.onUserRemoved(PROFILE_ID);

        assertThat(mConnections.get(USER_ID)).isSameInstanceAs(mConnection);
        assertThat(mWorker.isAlive()).isTrue();
    }

    @Test
    public void repeatedRemovalIsSafe() throws Exception {
        mController.onUserRemoved(USER_ID);
        mController.onUserRemoved(USER_ID);
        mController.onUserStopping(USER_ID);

        mWorker.join(5000);
        assertThat(mWorker.isAlive()).isFalse();
        assertThat(mConnections.size()).isEqualTo(0);
    }

    @Test
    public void permanentCloseRejectsSessionWithoutBinding() {
        mConnection.close();

        assertThrows(StorageSessionController.ExternalStorageServiceException.class,
                () -> mConnection.startSession("test", mock(ParcelFileDescriptor.class),
                        "/upper", "/lower"));

        verify(mContext, never()).bindServiceAsUser(any(Intent.class),
                any(ServiceConnection.class), anyInt(), any(Handler.class), any(UserHandle.class));
    }

    @Test
    public void temporaryDisconnectionAllowsRebinding() throws Exception {
        ServiceConnection[] binding = new ServiceConnection[1];
        doAnswer(invocation -> {
            binding[0] = invocation.getArgument(1);
            return true;
        }).when(mContext).bindServiceAsUser(any(Intent.class), any(ServiceConnection.class),
                anyInt(), any(Handler.class), any(UserHandle.class));
        Field activeField = StorageUserConnection.class.getDeclaredField("mActiveConnection");
        activeField.setAccessible(true);
        Object active = activeField.get(mConnection);
        Method connect = active.getClass().getDeclaredMethod("connectIfNeeded");
        connect.setAccessible(true);

        connect.invoke(active);
        binding[0].onServiceDisconnected(COMPONENT);
        connect.invoke(active);

        verify(mContext, times(2)).bindServiceAsUser(any(Intent.class),
                any(ServiceConnection.class), anyInt(), any(Handler.class), any(UserHandle.class));
        assertThat(mWorker.isAlive()).isTrue();
    }
}