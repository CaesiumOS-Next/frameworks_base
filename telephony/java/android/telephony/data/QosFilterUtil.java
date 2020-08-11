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

package android.telephony.data;

import android.net.LinkAddress;

import java.net.InetAddress;
import java.util.List;

/**
 * Matches telephony qos filters with socket addresses
 *
 * @hide
 */
public class QosFilterUtil {

    public static boolean anyAddressMatch(InetAddress targetAddress, int targetPort,
            List<LinkAddress> filterAddresses, QosFilter.PortRange filterPort) {

        return filterAddresses.stream()
                .anyMatch(l -> isMatch(targetAddress, targetPort, l, filterPort));
    }

    public static boolean isMatch(InetAddress targetAddress, int targetPort,
            LinkAddress filterAddress, QosFilter.PortRange filterPort) {

        // Accounting for ipv4 vs. ipv6
        // **** Need this to be looked at closer ****
        int prefixLength = targetAddress.getAddress().length < 16 ? 32 : 64;

        LinkAddress linkAddress = new LinkAddress(targetAddress, prefixLength);
        return linkAddress.isSameAddressAs(filterAddress)
                && filterPort.inRange(targetPort);
    }
}
