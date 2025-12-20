/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.hardware.biometrics;

import static android.hardware.biometrics.Flags.FLAG_ADD_KEY_AGREEMENT_CRYPTO_OBJECT;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
<<<<<<< PATCH SET (a2fabce912a05f5e6692d264ad226cf64c49ed4d Revert "Add Framework APIs for Identity Credential.")
import android.security.keystore.AndroidKeyStoreProvider;
||||||| BASE      (045b6de97564ef58e33890ae2f87b504c4c725d2 Add Framework APIs for Identity Credential.)
import android.security.identity.IdentityCredential;
import android.security.keystore.AndroidKeyStoreProvider;
=======
import android.annotation.Nullable;
import android.security.identity.IdentityCredential;
import android.security.identity.PresentationSession;
import android.security.keystore2.AndroidKeyStoreProvider;
>>>>>>> BASE      (1cdfff555f4a21f71ccc978290e2e212e2f8b168 Merge "Remove Redundant Variable for getStatusBarHeightForRo)

import java.security.Signature;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;

/**
 * A wrapper class for the crypto objects supported by BiometricPrompt and FingerprintManager.
<<<<<<< PATCH SET (a2fabce912a05f5e6692d264ad226cf64c49ed4d Revert "Add Framework APIs for Identity Credential.")
 * Currently the framework supports {@link Signature}, {@link Cipher} and {@link Mac} objects.
||||||| BASE      (045b6de97564ef58e33890ae2f87b504c4c725d2 Add Framework APIs for Identity Credential.)
 * Currently the framework supports {@link Signature}, {@link Cipher}, {@link Mac} and
 * {@link IdentityCredential} objects.
=======
 * Currently the framework supports {@link Signature}, {@link Cipher}, {@link Mac},
 * {@link KeyAgreement}, {@link IdentityCredential}, and {@link PresentationSession} objects.
>>>>>>> BASE      (1cdfff555f4a21f71ccc978290e2e212e2f8b168 Merge "Remove Redundant Variable for getStatusBarHeightForRo)
 * @hide
 */
public class CryptoObject {
    private final Object mCrypto;

    /**
     * Create from a {@link Signature} object.
     *
     * @param signature a {@link Signature} object.
     */
    public CryptoObject(@NonNull Signature signature) {
        mCrypto = signature;
    }

    /**
     * Create from a {@link Cipher} object.
     *
     * @param cipher a {@link Cipher} object.
     */
    public CryptoObject(@NonNull Cipher cipher) {
        mCrypto = cipher;
    }

    /**
     * Create from a {@link Mac} object.
     *
     * @param mac a {@link Mac} object.
     */
    public CryptoObject(@NonNull Mac mac) {
        mCrypto = mac;
    }

<<<<<<< PATCH SET (a2fabce912a05f5e6692d264ad226cf64c49ed4d Revert "Add Framework APIs for Identity Credential.")
||||||| BASE      (045b6de97564ef58e33890ae2f87b504c4c725d2 Add Framework APIs for Identity Credential.)
    public CryptoObject(@NonNull IdentityCredential credential) {
        mCrypto = credential;
    }

=======
    /**
     * Create from a {@link IdentityCredential} object.
     *
     * @param credential a {@link IdentityCredential} object.
     * @deprecated Use {@link PresentationSession} instead of {@link IdentityCredential}.
     */
    @Deprecated
    public CryptoObject(@NonNull IdentityCredential credential) {
        mCrypto = credential;
    }

>>>>>>> BASE      (1cdfff555f4a21f71ccc978290e2e212e2f8b168 Merge "Remove Redundant Variable for getStatusBarHeightForRo)
    /**
     * Create from a {@link PresentationSession} object.
     *
     * @param session a {@link PresentationSession} object.
     */
    public CryptoObject(@NonNull PresentationSession session) {
        mCrypto = session;
    }

    /**
     * Create from a {@link KeyAgreement} object.
     *
     * @param keyAgreement a {@link KeyAgreement} object.
     */
    @FlaggedApi(FLAG_ADD_KEY_AGREEMENT_CRYPTO_OBJECT)
    public CryptoObject(@NonNull KeyAgreement keyAgreement) {
        mCrypto = keyAgreement;
    }

    public CryptoObject(long operationHandle) {
        mCrypto = operationHandle;
    }

    /**
     * Get {@link Signature} object.
     * @return {@link Signature} object or null if this doesn't contain one.
     */
    public @Nullable Signature getSignature() {
        return mCrypto instanceof Signature ? (Signature) mCrypto : null;
    }

    /**
     * Get {@link Cipher} object.
     * @return {@link Cipher} object or null if this doesn't contain one.
     */
    public @Nullable Cipher getCipher() {
        return mCrypto instanceof Cipher ? (Cipher) mCrypto : null;
    }

    /**
     * Get {@link Mac} object.
     * @return {@link Mac} object or null if this doesn't contain one.
     */
    public @Nullable Mac getMac() {
        return mCrypto instanceof Mac ? (Mac) mCrypto : null;
    }

    /**
<<<<<<< PATCH SET (a2fabce912a05f5e6692d264ad226cf64c49ed4d Revert "Add Framework APIs for Identity Credential.")
||||||| BASE      (045b6de97564ef58e33890ae2f87b504c4c725d2 Add Framework APIs for Identity Credential.)
     * Get {@link IdentityCredential} object.
     * @return {@link IdentityCredential} object or null if this doesn't contain one.
     */
    public IdentityCredential getIdentityCredential() {
        return mCrypto instanceof IdentityCredential ? (IdentityCredential) mCrypto : null;
    }

    /**
=======
     * Get {@link IdentityCredential} object.
     * @return {@link IdentityCredential} object or null if this doesn't contain one.
     * @deprecated Use {@link PresentationSession} instead of {@link IdentityCredential}.
     */
    @Deprecated
    public @Nullable IdentityCredential getIdentityCredential() {
        return mCrypto instanceof IdentityCredential ? (IdentityCredential) mCrypto : null;
    }

    /**
     * Get {@link PresentationSession} object.
     * @return {@link PresentationSession} object or null if this doesn't contain one.
     */
    public @Nullable PresentationSession getPresentationSession() {
        return mCrypto instanceof PresentationSession ? (PresentationSession) mCrypto : null;
    }

    /**
     * Get {@link KeyAgreement} object. A key-agreement protocol is a protocol whereby
     * two or more parties can agree on a shared secret using public key cryptography.
     *
     * @return {@link KeyAgreement} object or null if this doesn't contain one.
     */
    @FlaggedApi(FLAG_ADD_KEY_AGREEMENT_CRYPTO_OBJECT)
    public @Nullable KeyAgreement getKeyAgreement() {
        return mCrypto instanceof KeyAgreement ? (KeyAgreement) mCrypto : null;
    }

    /**
>>>>>>> BASE      (1cdfff555f4a21f71ccc978290e2e212e2f8b168 Merge "Remove Redundant Variable for getStatusBarHeightForRo)
     * @hide
     * @return the opId associated with this object or 0 if none
     */
<<<<<<< PATCH SET (a2fabce912a05f5e6692d264ad226cf64c49ed4d Revert "Add Framework APIs for Identity Credential.")
    public final long getOpId() {
        return mCrypto != null
                ? AndroidKeyStoreProvider.getKeyStoreOperationHandle(mCrypto) : 0;
||||||| BASE      (045b6de97564ef58e33890ae2f87b504c4c725d2 Add Framework APIs for Identity Credential.)
    public final long getOpId() {
        if (mCrypto == null) {
            return 0;
        } else if (mCrypto instanceof IdentityCredential) {
            return ((IdentityCredential) mCrypto).getCredstoreOperationHandle();
        }
        return AndroidKeyStoreProvider.getKeyStoreOperationHandle(mCrypto);
=======
    public long getOpId() {
        if (mCrypto == null) {
            return 0;
        } else if (mCrypto instanceof Long) {
            return (long) mCrypto;
        } else if (mCrypto instanceof IdentityCredential) {
            return ((IdentityCredential) mCrypto).getCredstoreOperationHandle();
        } else if (mCrypto instanceof PresentationSession) {
            return ((PresentationSession) mCrypto).getCredstoreOperationHandle();
        }
        return AndroidKeyStoreProvider.getKeyStoreOperationHandle(mCrypto);
>>>>>>> BASE      (1cdfff555f4a21f71ccc978290e2e212e2f8b168 Merge "Remove Redundant Variable for getStatusBarHeightForRo)
    }
}
