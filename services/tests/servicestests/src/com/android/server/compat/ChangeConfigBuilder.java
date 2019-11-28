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
package com.android.server.compat;

import java.util.ArrayList;
/**
 * Helper class for creating a proper CompatConfig.
 */
public class ChangeConfigBuilder {
    private ArrayList<CompatChange> mChanges;

    private ChangeConfigBuilder() {
        mChanges = new ArrayList<>();
    }

    public static ChangeConfigBuilder create() {
        return new ChangeConfigBuilder();
    }

    public ChangeConfigBuilder targetSdkChangeWithId(int sdk, int id) {
        mChanges.add(new CompatChange(id, null, sdk, false));
        return this;
    }

    public ChangeConfigBuilder enabledChangeWithId(int id) {
        mChanges.add(new CompatChange(id, null, -1, false));
        return this;
    }

    public ChangeConfigBuilder disabledChangeWithId(int id) {
        mChanges.add(new CompatChange(id, null, -1, true));
        return this;
    }

    public void addAll(CompatConfig config) {
        for (CompatChange change : mChanges) {
            config.addChange(change);
        }
    }
}
