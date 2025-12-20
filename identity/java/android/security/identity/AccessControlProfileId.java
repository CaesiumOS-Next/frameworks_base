<<<<<<< PATCH SET (a2fabce912a05f5e6692d264ad226cf64c49ed4d Revert "Add Framework APIs for Identity Credential.")
||||||| BASE      (045b6de97564ef58e33890ae2f87b504c4c725d2 Add Framework APIs for Identity Credential.)
/*
 * Copyright 2020 The Android Open Source Project
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

package android.security.identity;

/**
 * A class used to wrap an access control profile identifiers.
 */
public class AccessControlProfileId {
    private int mId = 0;

    /**
     * Constructs a new object holding a numerical identifier.
     *
     * @param id the identifier.
     */
    public AccessControlProfileId(int id) {
        this.mId = id;
    }

    /**
     * Gets the numerical identifier wrapped by this object.
     *
     * @return the identifier.
     */
    public int getId() {
        return this.mId;
    }
}
=======
/*
 * Copyright 2020 The Android Open Source Project
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

package android.security.identity;

/**
 * A class used to wrap an access control profile identifiers.
 */
public class AccessControlProfileId {
    private int mId = 0;

    /**
     * Constructs a new object holding a numerical identifier.
     *
     * <p>The identifier must be a non-negative number and less than 32.
     *
     * @param id the identifier.
     */
    public AccessControlProfileId(int id) {
        this.mId = id;
    }

    /**
     * Gets the numerical identifier wrapped by this object.
     *
     * @return the identifier.
     */
    public int getId() {
        return this.mId;
    }
}
>>>>>>> BASE      (1cdfff555f4a21f71ccc978290e2e212e2f8b168 Merge "Remove Redundant Variable for getStatusBarHeightForRo)
