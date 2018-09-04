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

package com.android.server.net;

import android.net.INetdUnsolicitedEventCallback;

/**
 * Base {@link INetdUnsolicitedEventCallback} that provides no-op
 * implementations which can be overridden.
 *
 * @hide
 */
public class BaseNetdUnsolicitedEventCallback extends INetdUnsolicitedEventCallback.Stub {
    @Override
    public void onInterfaceClassActivityChangedEvent(boolean isActive,
            int label, long timestamp, int uid) {
        // default no-op
    }

    @Override
    public void onQuotaLimitReachedEvent(String alertName,  String ifName) {
        // default no-op
    }

    @Override
    public void onInterfaceDnsServersAddedEvent(String ifName, long lifetime, String[] servers) {
        // default no-op
    }

    @Override
    public void onInterfaceAddressChangedEvent(boolean updated,
            String addr, String ifName, int flags, int scope) {
        // default no-op
    }
    @Override
    public void onInterfaceAddedEvent(String ifName) {
        // default no-op
    }

    @Override
    public void onInterfaceRemovedEvent(String ifName) {
        // default no-op
    }

    @Override
    public void onInterfaceChangedEvent(String ifName, boolean status) {
        // default no-op
    }

    @Override
    public void onInterfaceLinkStatusChangedEvent(String ifName, boolean status) {
        // default no-op
    }

    @Override
    public void onRouteChangedEvent(boolean updated, String route, String gateway, String ifName) {
        // default no-op
    }

    @Override
    public void onStrictCleartextDetectedEvent(int uid, String hex) {
        // default no-op
    }
}
