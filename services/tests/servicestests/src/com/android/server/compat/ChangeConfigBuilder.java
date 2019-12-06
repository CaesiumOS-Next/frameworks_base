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

import com.android.compat.annotation.Change;
import com.android.compat.annotation.XmlWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
/**
 * Helper class for creating a proper CompatConfig.
 */
class ChangeConfigBuilder {
    private ArrayList<CompatChange> mChanges;

    private ChangeConfigBuilder() {
        mChanges = new ArrayList<>();
    }

    static ChangeConfigBuilder create() {
        return new ChangeConfigBuilder();
    }

    ChangeConfigBuilder targetSdkChangeWithId(int sdk, long id) {
        mChanges.add(new CompatChange(id, "", sdk, false, ""));
        return this;
    }

    ChangeConfigBuilder targetSdkDisabledChangeWithId(int sdk, long id) {
        mChanges.add(new CompatChange(id, "", sdk, true, ""));
        return this;
    }

    ChangeConfigBuilder targetSdkChangeWithIdAndName(int sdk, long id, String name) {
        mChanges.add(new CompatChange(id, name, sdk, false, ""));
        return this;
    }

    ChangeConfigBuilder targetSdkChangeWithIdAndDescription(int sdk, long id, String description) {
        mChanges.add(new CompatChange(id, "", sdk, false, description));
        return this;
    }

    ChangeConfigBuilder enabledChangeWithId(long id) {
        mChanges.add(new CompatChange(id, "", -1, false, ""));
        return this;
    }

    ChangeConfigBuilder enabledChangeWithIdAndName(long id, String name) {
        mChanges.add(new CompatChange(id, name, -1, false, ""));
        return this;
    }
    ChangeConfigBuilder enabledChangeWithIdAndDescription(long id, String description) {
        mChanges.add(new CompatChange(id, "", -1, false, description));
        return this;
    }

    ChangeConfigBuilder disabledChangeWithId(long id) {
        mChanges.add(new CompatChange(id, "", -1, true, ""));
        return this;
    }

    ChangeConfigBuilder disabledChangeWithIdAndName(long id, String name) {
        mChanges.add(new CompatChange(id, name, -1, true, ""));
        return this;
    }

    ChangeConfigBuilder disabledChangeWithIdAndDescription(long id, String description) {
        mChanges.add(new CompatChange(id, "", -1, true, description));
        return this;
    }

    void addAll(CompatConfig config) {
        for (CompatChange change : mChanges) {
            config.addChange(change);
        }
    }

    void saveToFile(File dir, String filename) {
        File file = new File(dir.getPath() + filename);
        XmlWriter writer = new XmlWriter();
        for (CompatChange change : mChanges) {
            writer.addChange(new Change(change.getId(), change.getName(), change.getDisabled(),
                                    change.getEnableAfterTargetSdk(), change.getDescription()));
        }
        try {
            file.createNewFile();
            writer.write(new FileOutputStream(file));
        } catch (IOException e) {
            throw new RuntimeException(
                    "Encountered an error while writing compat config file", e);
        }
    }
}
