/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.util;

import android.annotation.Hide;

/**
 * A variant of {@link ArraySet} that uses {@link System#identityHashCode(Object)}
 * for hashing instead of {@link Object#hashCode()}.
 *
 * @hide
 */
@Hide
public final class IdentityArraySet<E> extends ArraySet<E> {

    public IdentityArraySet() {
        super();
    }

    public IdentityArraySet(int capacity) {
        super(capacity);
    }

    @Override
    protected int getHashCode(Object o) {
        return System.identityHashCode(o);
    }
}
