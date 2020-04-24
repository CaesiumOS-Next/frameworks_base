/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.net.ip;

import static android.Manifest.permission.MANAGE_TEST_NETWORKS;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.INetd;
import android.net.LinkAddress;
import android.net.TestNetworkInterface;
import android.net.TestNetworkManager;
import android.net.util.InterfaceParams;
import android.net.util.SharedLog;
import android.os.IBinder;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.testutils.TapPacketReader;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileDescriptor;
import java.net.NetworkInterface;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class RouterAdvertisementDaemonTest {
    private final Context mContext = InstrumentationRegistry.getContext();

    private RouterAdvertisementDaemon mRaDaemon;
    private InterfaceParams mIfParams;
    private TapPacketReader mTapPacketReader;
    private TestNetworkInterface mTestIface;
    private String mIface;
    private INetd mNetd;

    @Before
    public void setUp() throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                MANAGE_TEST_NETWORKS);
        final TestNetworkManager tnm = mContext.getSystemService(TestNetworkManager.class);
        //LinkAddress ipv4Address = new LinkAddress("192.168.1.1/24");
        //mTestIface = tnm.createTunInterface(new LinkAddress[]{ ipv4Address });
        mTestIface = tnm.createTapInterface();
        mIface = mTestIface.getInterfaceName();
        //mNetd = INetd.Stub.asInterface(
        //        (IBinder) mContext.getSystemService(Context.NETD_SERVICE));
        //final InterfaceController ifController = new InterfaceController(mIface, mNetd, new SharedLog("RaTest"));
        //ifController.setInterfaceConfiguration(ipv4Address, true);
        assertNotNull(mIface);
        assertNotNull(NetworkInterface.getByName(mIface));

        mIfParams = InterfaceParams.getByName(mIface);
        final FileDescriptor fd = mTestIface.getFileDescriptor().getFileDescriptor();
        // mTapPacketReader = makePacketReader(fd, mIfParams.defaultMtu);
        mRaDaemon = new RouterAdvertisementDaemon(mIfParams);
    }

    @Test
    public void testSendRa() throws Exception {
        assertTrue(mRaDaemon.start());
//                try {
//                    Thread.sleep(30000);
//                } catch (InterruptedException ignored) {
//                    // Stop sleeping, immediately send an RA, and continue.
//                }
//        assertNotNull(mIface, NetworkInterface.getByName(mIface));
    }
}
