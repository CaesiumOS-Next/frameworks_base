/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.wdstechnology.android.kryten;

import java.util.HashMap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ProvisioningPushReceiver extends BroadcastReceiver {

    @SuppressWarnings("unchecked")
    @Override
    public void onReceive(Context context, Intent intent) {
        byte[] document = intent.getByteArrayExtra("data");
        String sec = null;
        String mac = null;

        if (intent.getExtras() != null) {
            HashMap<String, String> provisioningParameters = (HashMap<String, String>)intent
                    .getExtras().get("contentTypeParameters");
            if (provisioningParameters != null) {
                sec = provisioningParameters.get("SEC");
                mac = provisioningParameters.get("MAC");
            }
        }

        ProvisioningNotification.createNotification(context, sec, mac, document);
    }

}
