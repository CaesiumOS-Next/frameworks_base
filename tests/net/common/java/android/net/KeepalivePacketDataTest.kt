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
package android.net

import android.net.InvalidPacketException.ERROR_INVALID_IP_ADDRESS
import android.net.InvalidPacketException.ERROR_INVALID_PORT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import android.os.Build
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import java.net.InetAddress
import java.util.Arrays
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class KeepalivePacketDataTest {
    @Rule @JvmField
    val ignoreRule: DevSdkIgnoreRule = DevSdkIgnoreRule()
    private fun Address(addr: String): InetAddress = InetAddress.parseNumericAddress(addr)

    // Add for test because constructor of KeepalivePacketData is protected.
    internal inner class TestKeepalivePacketData(
        srcAddress: InetAddress?,
        srcPort: Int,
        dstAddress: InetAddress?,
        dstPort: Int,
        data: ByteArray
    )
            : KeepalivePacketData(srcAddress, srcPort, dstAddress, dstPort, data)

    @Test
    @IgnoreUpTo(Build.VERSION_CODES.Q)
    fun testConstructor() {
        var data: TestKeepalivePacketData
        val testBytes = byteArrayOf(12, 31, 22, 44)
        try {
            data = TestKeepalivePacketData(null, 4243, Address("198.168.0.2"), 4244,
                    testBytes)
            fail("Null src address should cause exception")
        } catch (e: InvalidPacketException) {
            assertEquals(e.error, ERROR_INVALID_IP_ADDRESS)
        }
        try {
            data = TestKeepalivePacketData(Address("198.168.0.2"), 4243, null, 4244,
                    testBytes)
            fail("Null dst address should cause exception")
        } catch (e: InvalidPacketException) {
            assertEquals(e.error, ERROR_INVALID_IP_ADDRESS)
        }
        try {
            data = TestKeepalivePacketData(Address("198.168.0.2"), 4243, Address("2001:db8::1"),
                    4244, testBytes)
            fail("Ip family mismatched should cause exception")
        } catch (e: InvalidPacketException) {
            assertEquals(e.error, ERROR_INVALID_IP_ADDRESS)
        }
        try {
            data = TestKeepalivePacketData(Address("198.168.0.2"), 65537, Address("198.168.0.1"),
                    4244, testBytes)
            fail("Invalid srcPort should cause exception")
        } catch (e: InvalidPacketException) {
            assertEquals(e.error, ERROR_INVALID_PORT)
        }
        try {
            data = TestKeepalivePacketData(Address("198.168.0.2"), 4243, Address("198.168.0.1"),
                    65537, testBytes)
            fail("Invalid dstPort should cause exception")
        } catch (e: InvalidPacketException) {
            assertEquals(e.error, ERROR_INVALID_PORT)
        }
        data = TestKeepalivePacketData(Address("198.168.0.2"), 4243, Address("198.168.0.1"),
                4244, testBytes)
        assertEquals(Address("198.168.0.2"), data.srcAddress)
        assertEquals(Address("198.168.0.1"), data.dstAddress)
        assertEquals(4243, data.srcPort)
        assertEquals(4244, data.dstPort)
        assertTrue(Arrays.equals(testBytes, data.getPacket()))
    }
}