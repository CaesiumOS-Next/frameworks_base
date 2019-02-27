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

import android.content.res.Resources;

import com.android.networkstack.IResourceManager.ResourceName;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * {@link InvocationHandler} to be used by {@link IResourceManager} to fetch resources from
 * {@link IResourceManager.ResourceName} annotations.
 */
class ResourceManagerInvocationHandler implements InvocationHandler {
    private final Class<?> mResIds;
    private final Resources mResources;

    /**
     * Create a ResourceManagerInvocationHandler.
     * @param resIds Resource ID object ("R object").
     * @param resources Resource resolver.
     */
    ResourceManagerInvocationHandler(Class<?> resIds, Resources resources) {
        mResIds = resIds;
        mResources = resources;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args)
            throws NoSuchFieldException, IllegalAccessException {
        final ResourceName resName = method.getAnnotation(ResourceName.class);
        if (resName == null) {
            throw new UnsupportedOperationException(method.getName()
                    + " missing " + ResourceName.class.getName() + " annotation");
        }

        final Class<?> resType = method.getReturnType();
        // This would need to be modified to support other resource types (int for example)
        if (!method.getReturnType().equals(String.class)) {
            throw new UnsupportedOperationException("Resource type not supported: " + resType);
        }

        // String resource IDs have the form R.string.[name]
        final Class<?> subResIds = getSubclass(mResIds, "string");
        try {
            final int resId = subResIds.getField(resName.value()).getInt(subResIds);
            return mResources.getString(resId);
        } catch (NoSuchFieldException e) {
            // Accept undefined resources returning null. On devices that use the in-process
            // network stack in particular, resources may be defined in com.android.internal.R,
            // but all keys in com.android.networkstack.R do not need to be duplicated there.
            return null;
        }
    }

    private static Class<?> getSubclass(Class<?> clazz, String name) throws NoSuchFieldException {
        for (Class<?> c : clazz.getDeclaredClasses()) {
            if (c.getSimpleName().equals(name)) {
                return c;
            }
        }
        throw new NoSuchFieldException("No such member: " + name + " on: " + clazz);
    }
}
