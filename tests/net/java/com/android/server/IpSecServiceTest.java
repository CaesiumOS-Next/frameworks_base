/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.INetd;
import android.net.IpSecAlgorithm;
import android.net.IpSecConfig;
import android.net.IpSecManager;
import android.net.IpSecSpiResponse;
import android.net.IpSecTransform;
import android.net.IpSecTransformResponse;
import android.net.IpSecUdpEncapResponse;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;

/** Unit tests for {@link IpSecService}. */
@RunWith(JUnit4.class)
public class IpSecServiceTest {

    private static final int DROID_SPI = 0xD1201D;
    private static final int TEST_UDP_ENCAP_INVALID_PORT = 100;
    private static final int TEST_UDP_ENCAP_PORT = 34567;

    private static final String IPV4_LOOPBACK = "127.0.0.1";
    private static final String IPV4_ADDR = "192.168.0.2";

    private static final byte[] CRYPT_KEY = {
        0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
        0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
        0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
        0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F
    };
    private static final byte[] AUTH_KEY = {
        0x7A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7F,
        0x7A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7F
    };

    Context mMockContext;
    INetd mMockNetd;
    IpSecService.IpSecServiceConfiguration mMockIpSecSrvConfig;
    IpSecService mIpSecService;
    ConnectivityManager mMockConnectivityManager;

    @Before
    public void setUp() throws Exception {
        mMockContext = mock(Context.class);
        mMockNetd = mock(INetd.class);
        mMockIpSecSrvConfig = mock(IpSecService.IpSecServiceConfiguration.class);
        mIpSecService = new IpSecService(mMockContext, mMockIpSecSrvConfig);
        mMockConnectivityManager = mock(ConnectivityManager.class);

        // Injecting mock netd
        when(mMockIpSecSrvConfig.getNetdInstance()).thenReturn(mMockNetd);
        // Injecting mock connectivity manager
        when(mMockContext.getSystemService(Context.CONNECTIVITY_SERVICE))
                .thenReturn(mMockConnectivityManager);
    }

    @Test
    public void testIpSecServiceCreate() throws InterruptedException {
        IpSecService ipSecSrv = IpSecService.create(mMockContext);
        assertNotNull(ipSecSrv);
    }

    @Test
    public void testIpSecServiceReserveSpi() throws Exception {
        when(mMockNetd.ipSecAllocateSpi(
                        anyInt(),
                        eq(IpSecTransform.DIRECTION_OUT),
                        anyString(),
                        eq(IPV4_LOOPBACK),
                        eq(DROID_SPI)))
                .thenReturn(DROID_SPI);

        IpSecSpiResponse spiResp =
                mIpSecService.reserveSecurityParameterIndex(
                        IpSecTransform.DIRECTION_OUT, IPV4_LOOPBACK, DROID_SPI, new Binder());
        assertEquals(IpSecManager.Status.OK, spiResp.status);
        assertEquals(DROID_SPI, spiResp.spi);
    }

    /**
     * This function finds an available port
     * */
    int
    findUnusedPort() throws Exception {
        ServerSocket s = new ServerSocket(0);
        int resport = s.getLocalPort();
        s.close();
        return resport;
    }

    @Test
    public void testOpenAndCloseUdpEncapsulationSocket() throws Exception {
        // Get an available port and store it in localport.
        int localport = findUnusedPort();

        IpSecUdpEncapResponse udpEncapResp =
                mIpSecService.openUdpEncapsulationSocket(localport, new Binder());
        assertNotNull(udpEncapResp);
        assertEquals(localport, udpEncapResp.port);

        mIpSecService.closeUdpEncapsulationSocket(udpEncapResp.resourceId);
        Os.close(udpEncapResp.fileDescriptor.getFileDescriptor());
    }

    @Test
    public void testOpenUdpEncapsulationSocketAfterClose() throws Exception {
        int localport = findUnusedPort();
        IpSecUdpEncapResponse udpEncapResp =
                mIpSecService.openUdpEncapsulationSocket(localport, new Binder());
        assertNotNull(udpEncapResp);
        assertEquals(IpSecManager.Status.OK, udpEncapResp.status);
        assertEquals(localport, udpEncapResp.port);

        mIpSecService.closeUdpEncapsulationSocket(udpEncapResp.resourceId);
        Os.close(udpEncapResp.fileDescriptor.getFileDescriptor());
        udpEncapResp =
                mIpSecService.openUdpEncapsulationSocket(localport, new Binder());
        assertNotNull(udpEncapResp);
        assertEquals(IpSecManager.Status.OK, udpEncapResp.status);

        mIpSecService.closeUdpEncapsulationSocket(udpEncapResp.resourceId);
        Os.close(udpEncapResp.fileDescriptor.getFileDescriptor());
    }

    @Test
    public void testOpenUdpEncapsulationSocketOnRandomPort() throws Exception {
        IpSecUdpEncapResponse udpEncapResp =
                mIpSecService.openUdpEncapsulationSocket(0, new Binder());
        assertNotNull(udpEncapResp);
        assertEquals(IpSecManager.Status.OK, udpEncapResp.status);
        mIpSecService.closeUdpEncapsulationSocket(udpEncapResp.resourceId);
        Os.close(udpEncapResp.fileDescriptor.getFileDescriptor());
    }

    @Test
    public void testOpenUdpEncapsulationSocketPortRange() throws Exception {
        try {
            IpSecUdpEncapResponse udpEncapResp =
                    mIpSecService.openUdpEncapsulationSocket(
                            TEST_UDP_ENCAP_INVALID_PORT, new Binder());
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testOpenUdpEncapsulationSocketTwice() throws Exception {
        // Get an available port and store it in localport.
        int localport = findUnusedPort();

        IpSecUdpEncapResponse udpEncapResp =
                mIpSecService.openUdpEncapsulationSocket(localport, new Binder());
        assertNotNull(udpEncapResp);
        assertEquals(localport, udpEncapResp.port);

        IpSecUdpEncapResponse testUdpEncapResp =
                mIpSecService.openUdpEncapsulationSocket(localport, new Binder());
        assertEquals(IpSecManager.Status.RESOURCE_UNAVAILABLE, testUdpEncapResp.status);

        mIpSecService.closeUdpEncapsulationSocket(udpEncapResp.resourceId);
        Os.close(udpEncapResp.fileDescriptor.getFileDescriptor());
    }

    @Test
    public void testReleaseSecurityParameterIndex() throws Exception {
        when(mMockNetd.ipSecAllocateSpi(
                        anyInt(),
                        eq(IpSecTransform.DIRECTION_OUT),
                        anyString(),
                        eq(IPV4_LOOPBACK),
                        eq(DROID_SPI)))
                .thenReturn(DROID_SPI);

        IpSecSpiResponse spiResp =
                mIpSecService.reserveSecurityParameterIndex(
                        IpSecTransform.DIRECTION_OUT, IPV4_LOOPBACK, DROID_SPI, new Binder());

        mIpSecService.releaseSecurityParameterIndex(spiResp.resourceId);

        ArgumentCaptor<Integer> resourceIdCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> spiCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockNetd, times(1))
                .ipSecDeleteSecurityAssociation(
                        resourceIdCaptor.capture(),
                        anyInt(),
                        anyString(),
                        anyString(),
                        spiCaptor.capture());
        assertEquals(spiResp.resourceId, (int) resourceIdCaptor.getValue());
        assertEquals(DROID_SPI, (int) spiCaptor.getValue());
    }

    @Test
    public void testReleaseInvalidSecurityParameterIndex() throws Exception {
        try {
            mIpSecService.releaseSecurityParameterIndex(1);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testCloseInvalidUdpEncapsulationSocket() throws Exception {
        try {
            mIpSecService.closeUdpEncapsulationSocket(1);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
        }
    }

    IpSecConfig buildIpSecConfig() throws Exception {
        IpSecManager ipSecManager = new IpSecManager(mIpSecService);

        // Mocking the netd to add SA
        /*when(mMockNetd.ipSecAddSecurityAssociation(
                        anyInt(),
                        eq(0),
                        anyInt(),
                        eq(""),
                        eq(""),
                        anyLong(),
                        eq(DROID_SPI),
                        anyString(),
                        eq(AUTH_KEY),
                        anyInt(),
                        anyString(),
                        eq(CRYPT_KEY),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt()))
                .thenReturn(DROID_SPI);*/
        when(mMockNetd.ipSecAllocateSpi(anyInt(), anyInt(), anyString(), anyString(), anyInt()))
                .thenReturn(DROID_SPI);

        IpSecAlgorithm encryptAlgo = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm authAlgo =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA256, AUTH_KEY, AUTH_KEY.length * 8);

        InetAddress localAddr = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        // Allocate and add SPI records in the IpSecService
        IpSecManager.SecurityParameterIndex outSpi =
                ipSecManager.reserveSecurityParameterIndex(
                        IpSecTransform.DIRECTION_OUT, localAddr);
        IpSecManager.SecurityParameterIndex inSpi =
                ipSecManager.reserveSecurityParameterIndex(
                        IpSecTransform.DIRECTION_IN, localAddr, DROID_SPI);

        IpSecConfig ipSecConfig =
                new IpSecTransform.Builder(mMockContext)
                        .setSpi(IpSecTransform.DIRECTION_OUT, outSpi)
                        .setSpi(IpSecTransform.DIRECTION_IN, inSpi)
                        .setEncryption(IpSecTransform.DIRECTION_OUT, encryptAlgo)
                        .setAuthentication(IpSecTransform.DIRECTION_OUT, authAlgo)
                        .setEncryption(IpSecTransform.DIRECTION_IN, encryptAlgo)
                        .setAuthentication(IpSecTransform.DIRECTION_IN, authAlgo)
                        .getIpSecConfig();
        return ipSecConfig;
    }

    @Test
    public void testCreateTransportModeTransform() throws Exception {
        IpSecConfig ipSecConfig = buildIpSecConfig();

        IpSecTransformResponse createTransformResp =
                mIpSecService.createTransportModeTransform(ipSecConfig, new Binder());
        assertEquals(IpSecManager.Status.OK, createTransformResp.status);

        ArgumentCaptor<Integer> spiCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> encryptAlgoName = ArgumentCaptor.forClass(String.class);
        verify(mMockNetd, times(2))
                .ipSecAddSecurityAssociation(
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyString(),
                        anyString(),
                        anyLong(),
                        spiCaptor.capture(),
                        anyString(),
                        anyObject(),
                        anyInt(),
                        encryptAlgoName.capture(),
                        anyObject(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt());
        List<Integer> spis = spiCaptor.getAllValues();
        List<String> algoNames = encryptAlgoName.getAllValues();
        for (int i = 0; i < 2; i++) {
            assertEquals(DROID_SPI, (int) spis.get(i));
            assertEquals(IpSecAlgorithm.CRYPT_AES_CBC, algoNames.get(i));
        }
    }

    @Test
    public void testDeleteTransportModeTransform() throws Exception {
        IpSecConfig ipSecConfig = buildIpSecConfig();

        IpSecTransformResponse createTransformResp =
                mIpSecService.createTransportModeTransform(ipSecConfig, new Binder());
        mIpSecService.deleteTransportModeTransform(createTransformResp.resourceId);

        ArgumentCaptor<Integer> resourceIdCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> spiCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> directionCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mMockNetd, times(2))
                .ipSecDeleteSecurityAssociation(
                        resourceIdCaptor.capture(),
                        directionCaptor.capture(),
                        anyString(),
                        anyString(),
                        spiCaptor.capture());

        List<Integer> resIds = resourceIdCaptor.getAllValues();
        List<Integer> spis = spiCaptor.getAllValues();

        for (int i = 0; i < 2; i++) {
            assertEquals(createTransformResp.resourceId, (int) resIds.get(i));
            assertEquals(DROID_SPI, (int) spis.get(i));
        }
    }

    @Test
    public void testDeleteInvalidTransportModeTransform() throws Exception {
        try {
            mIpSecService.deleteTransportModeTransform(1);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testApplyTransportModeTransform() throws Exception {
        IpSecConfig ipSecConfig = buildIpSecConfig();

        IpSecTransformResponse createTransformResp =
                mIpSecService.createTransportModeTransform(ipSecConfig, new Binder());

        int resourceId = createTransformResp.resourceId;
        mIpSecService.applyTransportModeTransform(mock(ParcelFileDescriptor.class), resourceId);

        ArgumentCaptor<Integer> resourceIdCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> spiCaptor = ArgumentCaptor.forClass(Integer.class);
        // netd method was called twice since there are two direction
        verify(mMockNetd, times(2))
                .ipSecApplyTransportModeTransform(
                        anyObject(),
                        resourceIdCaptor.capture(),
                        anyInt(),
                        anyString(),
                        anyString(),
                        spiCaptor.capture());

        List<Integer> resIds = resourceIdCaptor.getAllValues();
        List<Integer> spis = spiCaptor.getAllValues();

        for (int i = 0; i < 2; i++) {
            assertEquals(createTransformResp.resourceId, (int) resIds.get(i));
            assertEquals(DROID_SPI, (int) spis.get(i));
        }
    }

    @Test
    public void testRemoveTransportModeTransform() throws Exception {
        ParcelFileDescriptor pfd = ParcelFileDescriptor.fromSocket(new Socket());
        mIpSecService.removeTransportModeTransform(pfd, 1);

        ArgumentCaptor<FileDescriptor> fdCaptor = ArgumentCaptor.forClass(FileDescriptor.class);
        verify(mMockNetd, times(1)).ipSecRemoveTransportModeTransform(fdCaptor.capture());
        assertEquals(pfd.getFileDescriptor(), (FileDescriptor) fdCaptor.getValue());
    }
}
