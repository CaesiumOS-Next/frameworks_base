/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.net;

/** {@hide} */
oneway interface INetdUnsolicitedEventCallback {

    // Possible addNetdEventCallback callers.
    const int CALLBACK_CALLER_NETWORKMANAGEMENT_SERVICE = 0;

    /**
     * Notifies that interfaces have been idle/active for a certain period of time.
     * It is the event for idletimer.
     *
     * @param isActive true for active status, false for idle
     * @param label unique identifier of the idletimer.
     *              Since NMS only set the identifier as int, only report event with int label.
     * @param timestamp kerenl timestamp of this event, 0 for no timestamp
     * @param uid uid of this event, -1 for no uid.
     *            It represents the uid that was responsible for waking the radio.
     */
    void onInterfaceClassActivityChangedEvent(
            boolean isActive,
            int label,
            long timestamp,
            int uid);

    /**
     * Notifies that a specific interface reached its quota limitation.
     *
     * @param alertName alert name of the quota limitation
     * @param ifName interface which reaches the limitation
     */
    void onQuotaLimitReachedEvent(String alertName, String ifName);

    /**
     * Notifies that some dns servers have been added on a specific interface.
     *
     * @param ifName interface name
     * @param lifetimeMs lifetime for the dns servers in milliseconds
     * @param servers the address of servers.
     *                  e.g. IpV4: 192.168.1.1
     *                       IpV6: 2001:4860:4860::6464
     */
    void onInterfaceDnsServersAddedEvent(String ifName, long lifetimeMs, in String[] servers);

    /**
     * Notifies that an address has changed on a specific interface.
     *
     * @param updated true for update, false for remove
     * @param addr address of this change
     * @param ifName the name of the interface that changed addresses
     * @param flags address flags of this change
     * @param scope address scope of this change
     */
    void onInterfaceAddressChangedEvent(
            boolean updated,
            String addr,
            String ifName,
            int flags,
            int scope);

    /**
     * Notifies that an interface has been added.
     *
     * @param ifName the name of the added interface
     */
    void onInterfaceAddedEvent(String ifName);

    /**
     * Notifies that an interface has been removed.
     *
     * @param ifName the name of the removed interface
     */
    void onInterfaceRemovedEvent(String ifName);

    /**
     * Notifies that the status of the specific interface has changed.
     *
     * @param ifName the name of the interface that changed status
     * @param status true for interface up, false for down
     */
    void onInterfaceChangedEvent(String ifName, boolean status);

    /**
     * Notifies that the link status of the specific interface has changed.
     *
     * @param ifName the name of the interface whose link status has changed
     * @param status true for interface link status up, false for link status down
     */
    void onInterfaceLinkStatusChangedEvent(String ifName, boolean status);

    /**
     * Notifies that a routing rule has changed.
     *
     * @param updated true for update, false for remove
     * @param route destination address of this routing rule
     * @param gateway address of gateway, empty string for no gateway
     * @param ifName interface name of this routing rule
     */
    void onRouteChangedEvent(boolean updated, String route, String gateway, String ifName);

    /**
     * Notifies that kernel has detected a socket sending data not wrapped
     * inside a layer of SSL/TLS encryption.
     *
     * @param uid uid of this event
     * @param hex packet content in hex format
     */
    void onStrictCleartextDetectedEvent(int uid, String hex);

}
