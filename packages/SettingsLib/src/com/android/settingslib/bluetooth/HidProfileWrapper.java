/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.settingslib.bluetooth;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidHost;
import android.bluetooth.BluetoothCodecStatus;
import android.bluetooth.BluetoothProfile;
import com.android.settingslib.R;
import java.util.List;

/**
 * This interface replicates some methods of HidProfile that are new and not
 * yet available in our current version of  Robolectric. It provides a thin wrapper to call the real
 * methods in production and a mock in tests.
 */
public interface HidProfileWrapper extends LocalBluetoothProfile {

    static interface Factory {
        HidProfileWrapper getInstance(HidProfile service);
    }

    /**
     * @return the real {@code HidProfile} object
     */
    HidProfile getService();

    public boolean isProfileReady();

    public boolean isConnectable();

    public boolean isAutoConnectable();

    public boolean connect(BluetoothDevice device);

    public boolean disconnect(BluetoothDevice device);

    public int getConnectionStatus(BluetoothDevice device);

    public boolean isPreferred(BluetoothDevice device);

    public int getPreferred(BluetoothDevice device);

    public void setPreferred(BluetoothDevice device, boolean preferred);

    public int getOrdinal();

    public int getNameResource(BluetoothDevice device);

    public int getSummaryResourceForDevice(BluetoothDevice device);

    public int getDrawableResource(BluetoothClass btClass);

}