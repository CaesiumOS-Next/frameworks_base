/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.networkstack;

import android.annotation.NonNull;
import android.content.res.Resources;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Proxy;

/**
 * Interface used to fetch resources for the network stack.
 *
 * <p>Depending on whether the network stack is running in an app or in the system_server process,
 * this interface may be implemented differently to fetch resources appropriately.
 */
public interface IResourceManager {
    // To add a resource, define it in the NetworkStack res package, and add a method to this
    // interface annotated with @ResourceName.
    // On devices that use the in-process NetworkStack (so the NetworkStack is not an APK),
    // resources will be fetched from the framework resources (frameworks/base/core/res). If not
    // defined, a default value (null for strings) is returned.

    /**
     * Get the captive portal HTTP URL defined in resources, if any.
     * @return The URL as String, or null if not defined in resources.
     */
    @ResourceName("config_captive_portal_http_url")
    String getCaptivePortalHttpUrl();


    @Retention(RetentionPolicy.RUNTIME)
    @interface ResourceName {
        String value();
    }

    /**
     * Create a IResourceManager that uses {@link ResourceName} annotations to fetch resources.
     *
     * @param resIds Object containing resource IDs (e.g. "R object").
     * @param resources Resources to be fetched using the resIds.
     */
    static IResourceManager make(@NonNull Class<?> resIds, @NonNull Resources resources) {
        return (IResourceManager) Proxy.newProxyInstance(
                IResourceManager.class.getClassLoader(),
                new Class<?>[]{IResourceManager.class},
                new ResourceManagerInvocationHandler(resIds, resources));
    }
}
