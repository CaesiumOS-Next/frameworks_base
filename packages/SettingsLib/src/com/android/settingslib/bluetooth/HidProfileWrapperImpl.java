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

public class HidProfileWrapperImpl implements HidProfileWrapper {

    public static class Factory implements HidProfileWrapper.Factory {
        @Override
        public HidProfileWrapper getInstance(HidProfile service) {
            return new HidProfileWrapperImpl(service);
        }
    }

    private HidProfile mService;

    public HidProfileWrapperImpl(HidProfile service) {
        mService = service;
    }

    @Override
    public HidProfile getService() {
        return mService;
    }

    @Override
    public boolean isProfileReady() {
        return mService.isProfileReady();
    }

    @Override
    public boolean isConnectable() {
        return mService.isConnectable();
    }

    @Override
    public boolean isAutoConnectable() {
        return mService.isAutoConnectable();
    }

    @Override
    public boolean connect(BluetoothDevice device) {
        return mService.connect(device);
    }

    @Override
    public boolean disconnect(BluetoothDevice device) {
        return mService.disconnect(device);
    }

    @Override
    public int getConnectionStatus(BluetoothDevice device) {
        return mService.getConnectionStatus(device);
    }

    @Override
    public boolean isPreferred(BluetoothDevice device) {
        return mService.isPreferred(device);
    }

    @Override
    public int getPreferred(BluetoothDevice device) {
       return mService.getPreferred(device);
    }

    @Override
    public void setPreferred(BluetoothDevice device, boolean preferred) {
        mService.setPreferred(device, preferred);
    }

    @Override
    public int getOrdinal() {
        return mService.getOrdinal();
    }

    @Override
    public int getNameResource(BluetoothDevice device) {
        return mService.getNameResource(device);
    }

    @Override
    public int getDrawableResource(BluetoothClass btClass) {
        return mService.getDrawableResource(btClass);
    }

    @Override
    public int getSummaryResourceForDevice(BluetoothDevice device) {
        return mService.getSummaryResourceForDevice(device);
    }

}

