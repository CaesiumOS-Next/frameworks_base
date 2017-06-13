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

package com.android.commands.svc;

import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.bluetooth.BluetoothAdapter;
import android.os.RemoteException;
import android.os.ServiceManager;

public class BluetoothCommand extends Svc.Command {

    public BluetoothCommand() {
        super("bluetooth");
    }

    @Override
    public String shortHelp() {
        return "Control Bluetooth service";
    }

    @Override
    public String longHelp() {
        return shortHelp() + "\n"
                + "\n"
                + "usage: svc bluetooth [enable|disable]\n"
                + "         Turn Bluetooth on or off.\n\n";
    }

    @Override
    public void run(String[] args) {
        IPackageManager pm = IPackageManager.Stub.asInterface(
                ServiceManager.getService("package"));
        try {
            if (!pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH, 0) &&
                !pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE, 0)) {
                System.err.println("Bluetooth feature not supported.");
                return;
            }
        } catch (RemoteException e) {
            System.err.println("RemoteException from the PackageManager, is the system running?");
            return;
        }

        if (args.length == 2 && "enable".equals(args[1])) {
            BluetoothAdapter.getDefaultAdapter().enable();
        } else if (args.length == 2 && "disable".equals(args[1])) {
            BluetoothAdapter.getDefaultAdapter().disable();
        } else {
            System.err.println(longHelp());
        }
    }
}
