/*
 * Copyright (C) 2006 The Android Open Source Project
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

package android.os;

import android.compat.annotation.UnsupportedAppUsage;
import android.os.Trace;

/**
 * Native implementation of the service manager.  Most clients will only
 * care about asInterface().
 *
 * @hide
 */
public final class ServiceManagerNative {
    private ServiceManagerNative() {}

    /**
     * Cast a Binder object into a service manager interface, generating
     * a proxy if needed.
     *
     * TODO: delete this method and have clients use
     *     IServiceManager.Stub.asInterface instead
     */
    @UnsupportedAppUsage
    public static IServiceManager asInterface(IBinder obj) {
        Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "ServiceManagerNative.asInterface");

        if (obj == null) {
            return null;
        }

        // ServiceManager is never local
        IServiceManager out = new ServiceManagerProxy(obj);
        Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);

        return out;
    }
}

// This class should be deleted and replaced with IServiceManager.Stub whenever
// mRemote is no longer used
class ServiceManagerProxy implements IServiceManager {
    public ServiceManagerProxy(IBinder remote) {
        Trace.traceBegin(
                Trace.TRACE_TAG_ACTIVITY_MANAGER, "ServiceManagerProxy.ServiceManagerProxy()");
        mRemote = remote;
        mServiceManager = IServiceManager.Stub.asInterface(this.getNativeServiceManager());
        Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
    }

    public IBinder asBinder() {
        Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "ServiceManagerProxy.asBinder()");
        Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
        return mRemote;
    }

    // TODO(b/355394904): This function has been deprecated, please use getService2 instead.
    @UnsupportedAppUsage
    public IBinder getService(String name) throws RemoteException {
        Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "ServiceManagerProxy.getService()");
        // Same as checkService (old versions of servicemanager had both methods).
        IBinder out = checkService(name).getBinder();
        Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
        return out;
    }

    public Service getService2(String name) throws RemoteException {
        // Same as checkService (old versions of servicemanager had both methods).
        Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "ServiceManagerProxy.getService2()");
        Service out = checkService(name);
        Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
        return out;
    }

    public Service checkService(String name) throws RemoteException {
        Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "ServiceManagerProxy.checkService()");
        Service out = mServiceManager.checkService(name);
        Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
        return out;
    }

    public void addService(String name, IBinder service, boolean allowIsolated, int dumpPriority)
            throws RemoteException {
        mServiceManager.addService(name, service, allowIsolated, dumpPriority);
    }

    public String[] listServices(int dumpPriority) throws RemoteException {
        return mServiceManager.listServices(dumpPriority);
    }

    public void registerForNotifications(String name, IServiceCallback cb)
            throws RemoteException {
        mServiceManager.registerForNotifications(name, cb);
    }

    public void unregisterForNotifications(String name, IServiceCallback cb)
            throws RemoteException {
        throw new RemoteException();
    }

    public boolean isDeclared(String name) throws RemoteException {
        return mServiceManager.isDeclared(name);
    }

    public String[] getDeclaredInstances(String iface) throws RemoteException {
        return mServiceManager.getDeclaredInstances(iface);
    }

    public String updatableViaApex(String name) throws RemoteException {
        return mServiceManager.updatableViaApex(name);
    }

    public String[] getUpdatableNames(String apexName) throws RemoteException {
        return mServiceManager.getUpdatableNames(apexName);
    }

    public ConnectionInfo getConnectionInfo(String name) throws RemoteException {
        return mServiceManager.getConnectionInfo(name);
    }

    public void registerClientCallback(String name, IBinder service, IClientCallback cb)
            throws RemoteException {
        throw new RemoteException();
    }

    public void tryUnregisterService(String name, IBinder service) throws RemoteException {
        throw new RemoteException();
    }

    public ServiceDebugInfo[] getServiceDebugInfo() throws RemoteException {
        return mServiceManager.getServiceDebugInfo();
    }

    /**
     * Same as mServiceManager but used by apps.
     *
     * Once this can be removed, ServiceManagerProxy should be removed entirely.
     */
    @UnsupportedAppUsage
    private IBinder mRemote;

    private IServiceManager mServiceManager;

    private native IBinder getNativeServiceManager();
}
