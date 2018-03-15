/**
 * Copyright (C) 2014 The Android Open Source Project
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

/**
 * A class that coordinates access to the face authentication hardware.
 */
public class FaceAuthenticationManager {
    //
    // Error messages from face authentication hardware during initialization, enrollment, authentication or 
    // removal. Must agree with the list in HAL h file
    //
    /**
     * The hardware is unavailable. Try again later.
     */
    public static final int FACE_AUTHENTICATION_ERROR_HW_UNAVAILABLE = 1;
    /**
     * Error state returned when the sensor was unable to process the current image.
     */
    public static final int FACE_AUTHENTICATION_ERROR_UNABLE_TO_PROCESS = 2;
    /**
     * Error state returned when the current request has been running too long. This is intended to
     * prevent programs from waiting for the face authentication camera indefinitely. The timeout is
     * platform and sensor-specific, but is generally on the order of 30 seconds.
     */
    public static final int FACE_AUTHENTICATION_ERROR_TIMEOUT = 3;
    /**
     * Error state returned for operations like enrollment; the operation cannot be completed
     * because there's not enough storage remaining to complete the operation.
     */    
    public static final int FACE_AUTHENTICATION_ERROR_NO_SPACE = 4;
    /**
     * The operation was canceled because the face authentication camera is unavailable. For example,
     * this may happen when the user is switched, the device is locked or another pending operation
     * prevents or disables it.
     */
    public static final int FACE_AUTHENTICATION_ERROR_CANCELED = 5;
    /**
     * The {@link FaceAuthenticationManager#remove} call failed. Typically this will happen when the
     * provided face id was incorrect.
     *
     * @hide
     */
    public static final int FACE_AUTHENTICATION_ERROR_UNABLE_TO_REMOVE = 6;
    /**
     * The operation was canceled because the API is locked out due to too many attempts.
     * This occurs after 5 failed attempts, and lasts for 30 seconds.
     */
    public static final int FACE_AUTHENTICATION_ERROR_LOCKOUT = 7;
    /**
     * Hardware vendors may extend this list if there are conditions that do not fall under one of
     * the above categories. Vendors are responsible for providing error strings for these errors.
     * These messages are typically reserved for internal operations such as enrollment, but may be
     * used to express vendor errors not covered by the ones in HAL h file. Applications are
     * expected to show the error message string if they happen, but are advised not to rely on the
     * message id since they will be device and vendor-specific
     */
    public static final int FACE_AUTHENTICATION_ERROR_VENDOR = 8;
    /**
     * The operation was canceled because FACE_AUTHENTICATION_ERROR_LOCKOUT occurred too many times.
     * Face authentication is disabled until the user unlocks with strong authentication
     * (PIN/Pattern/Password)
     */
    public static final int FACE_AUTHENTICATION_ERROR_LOCKOUT_PERMANENT = 9;
    /**
     * The user canceled the operation. Upon receiving this, applications should use alternate
     * authentication (e.g. a password). The application should also provide the means to return
     * to face authentication, such as a "use face authentication" button.
     */
    public static final int FACE_AUTHENTICATION_ERROR_USER_CANCELED = 10;
    /**
     * The operation was canceled because a spoof attack was detected by the face authentication.
     * TBD lockout definitions for spoof detection.
     */
    public static final int FACE_AUTHENTICATION_ERROR_SUSPECTED_SPOOF = 11;
    /**
     * @hide
     */
    public static final int FACE_AUTHENTICATION_ERROR_VENDOR_BASE = 1000;
    //
    // Image acquisition messages. Must agree with those in HAL h file
    //
    /**
     * The image acquired was good.
     */
    public static final int FACE_AUTHENTICATION_ACQUIRED_GOOD = 0;
    /**
     * The face image was too bright or too dark to process. The user should be informed to chage
     * the environment lighting conditions.
     */
    public static final int FACE_AUTHENTICATION_ACQUIRED_BAD_LIGHT = 1;
    /**
     * Only a partial part of the face was detected. During enrollment, the user should be
     * informed on what needs to happen to resolve this problem, e.g. "Move to the right / left / up / down."
     */
    public static final int FACE_AUTHENTICATION_ACQUIRED_FACE_NOT_IN_CENTER = 2;
    /**
     * The detected face is too small, as the user is too far from the camera. The user should be informed to 
     * get closer to the camera.
     */
    public static final int FACE_AUTHENTICATION_ACQUIRED_TOO_FAR = 3;
    /**
     * The detected face is too close to the camera, and the image can't be processed. The user should be  
     * informed to get farther from the camera.
     */
    public static final int FACE_AUTHENTICATION_ACQUIRED_TOO_CLOSE = 4;
    /**
     * The detected face pose is too rotated, hiding face parts which are required for recognition. The user 
     * should be informed to turn to the camera.
     */
    public static final int FACE_AUTHENTICATION_ACQUIRED_TOO_STRONG_POSE = 5;  //too rotated
    /**
     * No face was detected in front of the camera. The user is expected to point the camera to a face 
     * when this is returned.
     */
    public static final int FACE_AUTHENTICATION_ACQUIRED_NOT_DETECTED = 6;
    /**
     * Hardware vendors may extend this list if there are conditions that do not fall under one of
     * the above categories. Vendors are responsible for providing error strings for these errors.
     * @hide
     */
    public static final int FACE_AUTHENTICATION_ACQUIRED_VENDOR = 7;
    /**
     * @hide
     */
    public static final int FACE_AUTHENTICATION_ACQUIRED_VENDOR_BASE = 1000;

    /**
     * A wrapper class for the crypto objects supported by FaceAuthenticationManager.
     */
    public static final class CryptoObject {
        public Signature getSignature() { }

        public Cipher getCipher() { }

        public Mac getMac() { }
    };
    /**
     * Container for callback data from {@link FaceAuthenticationManager#authenticate(CryptoObject,
     *     CancellationSignal, int, AuthenticationCallback, Handler)}.
     */
    public static class AuthenticationResult {
        /**
         * Authentication result
         *
         * @param crypto the crypto object
         * @param face the recognized face data, if allowed.
         * @hide
         */
        public AuthenticationResult(CryptoObject crypto, Face face, int userId) { }
        /**
         * Obtain the crypto object associated with this transaction
         * @return crypto object provided to {@link FaceAuthenticationManager#authenticate(CryptoObject,
         *     CancellationSignal, int, AuthenticationCallback, Handler)}.
         */
        public CryptoObject getCryptoObject() { }
        /**
         * Obtain the Face associated with this operation. Applications are strongly
         * discouraged from associating specific faces with specific applications or operations.
         *
         * @hide
         */
        public Face getFace() { }
        /**
         * Obtain the userId for which this face was authenticated.
         * @hide
         */
        public int getUserId() { }
        
    };
    /**
     * Callback structure provided to {@link FaceAuthenticationManager#authenticate(CryptoObject,
     * CancellationSignal, int, AuthenticationCallback, Handler)}. Users of {@link
     * FaceAuthenticationManager#authenticate(CryptoObject, CancellationSignal,
     * int, AuthenticationCallback, Handler) } must provide an implementation of this for listening to
     * fingerprint events.
     */
    public static abstract class AuthenticationCallback {
        /**
         * Called when an unrecoverable error has been encountered and the operation is complete.
         * No further callbacks will be made on this object.
         * @param errorCode An integer identifying the error message
         * @param errString A human-readable error string that can be shown in UI
         */
        public void onAuthenticationError(int errorCode, CharSequence errString) { }
        /**
         * Called when a recoverable error has been encountered during authentication. The help
         * string is provided to give the user guidance for what went wrong, such as
         * "Sensor dirty, please clean it."
         * @param helpCode An integer identifying the error message
         * @param helpString A human-readable string that can be shown in UI
         */
        public void onAuthenticationHelp(int helpCode, CharSequence helpString) { }
        /**
         * Called when a face is recognized.
         * @param result An object containing authentication-related data
         */
        public void onAuthenticationSucceeded(AuthenticationResult result) { }
        /**
         * Called when a face is detected but not recognized.
         */
        public void onAuthenticationFailed() { }
        /**
         * Called when a face image has been acquired, but wasn't processed yet.
         *
         * @param acquireInfo one of FACE_AUTHENTICATION_ACQUIRED_* constants
         * @hide
         */
        public void onAuthenticationAcquired(int acquireInfo) { }
    };
    /**
     * Callback structure provided to {@link FaceAuthenticationManager#enroll(long, EnrollmentCallback,
     * CancellationSignal, int). Users of {@link #FaceAuthenticationManager()}
     * must provide an implementation of this to {@link FaceAuthenticationManager#enroll(long,
     * CancellationSignal, int, EnrollmentCallback) for listening to face enrollment events.
     *
     * @hide
     */
    public static abstract class EnrollmentCallback {
        /**
         * Called when an unrecoverable error has been encountered and the operation is complete.
         * No further callbacks will be made on this object.
         * @param errMsgId An integer identifying the error message
         * @param errString A human-readable error string that can be shown in UI
         */
        public void onEnrollmentError(int errMsgId, CharSequence errString) { }
        /**
         * Called when a recoverable error has been encountered during enrollment. The help
         * string is provided to give the user guidance for what went wrong, such as
         * "Sensor dirty, please clean it" or what they need to do next, such as
         * "Rotate face up / down."
         * @param helpMsgId An integer identifying the error message
         * @param helpString A human-readable string that can be shown in UI
         */
        public void onEnrollmentHelp(int helpMsgId, CharSequence helpString) { }
        /**
         * Called as each enrollment step progresses. Enrollment is considered complete when
         * remaining reaches 0. This function will not be called if enrollment fails. See
         * {@link EnrollmentCallback#onEnrollmentError(int, CharSequence)}
         * @param remaining The number of remaining steps
         */
        public void onEnrollmentProgress(int remaining) { }
    };
    /**
     * Callback structure provided to {@link #remove}. Users of {@link FaceAuthenticationManager} may
     * optionally provide an implementation of this to
     * {@link #remove(Face, int, RemovalCallback)} for listening to face template
     * removal events.
     *
     * @hide
     */
    public static abstract class RemovalCallback {
        /**
         * Called when the given face can't be removed.
         * @param fp The face that the call attempted to remove
         * @param errMsgId An associated error message id
         * @param errString An error message indicating why the face id can't be removed
         */
        public void onRemovalError(Face face, int errMsgId, CharSequence errString) { }
        /**
         * Called when a given face is successfully removed.
         * @param face The face template that was removed.
         * @param remaining The number of faces yet to be removed in this operation. If
         *         {@link #remove} is called on one face, this should be 0. If
         *         {@link #remove} is called on a group, this should be the number of remaining
         *         faces in the group, and 0 after the last face is removed.
         */
        public void onRemovalSucceeded(Face face, int remaining) { }
    };
    /**
     * Callback structure provided to {@link FaceAuthenticationManager#enumerate(int). Users of
     * {@link #FaceAuthenticationManager()} may optionally provide an implementation of this to
     * {@link FaceAuthenticationManager#enumerate(int, EnumerateCallback)} for listening to
     * face template enumeration events.
     *
     * @hide
     */
    public static abstract class EnumerateCallback {
        /**
         * Called when the given user faces can't be enumerated.
         * @param errMsgId An associated error message id
         * @param errString An error message indicating why the faces can't be enumerated
         */
        public void onEnumerateError(int errMsgId, CharSequence errString) { }
        /**
         * Called when a given face is successfully enumerated.
         * @param face the face template that was enumerated.
         */
        public void onEnumerate(Face face) { }
    };
    /**
     * @hide
     */
    public static abstract class LockoutResetCallback {
        /**
         * Called when lockout period expired and clients are allowed to listen for face authentication
         * again.
         */
        public void onLockoutReset() { }
    };
    /**
     * Request authentication of a crypto object. This call operates the face recognition camera 
     * and starts capturing images. It terminates when
     * {@link AuthenticationCallback#onAuthenticationError(int, CharSequence)} or
     * {@link AuthenticationCallback#onAuthenticationSucceeded(AuthenticationResult)} is called, at
     * which point the object is no longer valid. The operation can be canceled by using the
     * provided cancel object.
     *
     * @param crypto object associated with the call or null if none required.
     * @param cancel an object that can be used to cancel authentication
     * @param flags optional flags; should be 0
     * @param callback an object to receive authentication events
     * @param handler an optional handler to handle callback events
     *
     * @throws IllegalArgumentException if the crypto operation is not supported or is not backed
     *         by <a href="{@docRoot}training/articles/keystore.html">Android Keystore
     *         facility</a>.
     * @throws IllegalStateException if the crypto primitive is not initialized.
     */
     @RequiresPermission(USE_FACE_AUTHENTICATION)
     public void authenticate(@Nullable CryptoObject crypto, @Nullable CancellationSignal cancel, 
             int flags, @NonNull AuthenticationCallback callback, @Nullable Handler handler) { }

    /**
     * Per-user version
     * @hide
     */
    @RequiresPermission(USE_FACE_AUTHENTICATION)
    public void authenticate(@Nullable CryptoObject crypto, @Nullable CancellationSignal cancel,
            int flags, @NonNull AuthenticationCallback callback, Handler handler, int userId) { }


    /**
     * Request face authentication enrollment. This call operates the face recognition camera 
     * and starts capturing images. Progress will be indicated by callbacks to the
     * {@link EnrollmentCallback} object. It terminates when
     * {@link EnrollmentCallback#onEnrollmentError(int, CharSequence)} or
     * {@link EnrollmentCallback#onEnrollmentProgress(int) is called with remaining == 0, at
     * which point the object is no longer valid. The operation can be canceled by using the
     * provided cancel object.
     * @param token a unique token provided by a recent creation or verification of device
     * credentials (e.g. pin, pattern or password).
     * @param cancel an object that can be used to cancel enrollment
     * @param flags optional flags
     * @param userId the user to whom this face will belong to
     * @param callback an object to receive enrollment events
     * @hide
     */
    @RequiresPermission(MANAGE_FACE_AUTHENTICATION)
    public void enroll(byte[] token, CancellationSignal cancel, int flags, 
            int userId, EnrollmentCallback callback) { }

    /**
     * Requests a pre-enrollment auth token to tie enrollment to the confirmation of
     * existing device credentials (e.g. pin/pattern/password).
     * @hide
     */
    @RequiresPermission(MANAGE_FACE_AUTHENTICATION)
    public long preEnroll() { }

    /**
     * Finishes enrollment and cancels the current auth token.
     * @hide
     */
    @RequiresPermission(MANAGE_FACE_AUTHENTICATION)
    public int postEnroll() { }
    /**
     * Sets the active user. This is meant to be used to select the current profile for enrollment
     * to allow separate enrolled faces for a work profile
     * @param userId
     * @hide
     */
    @RequiresPermission(MANAGE_FACE_AUTHENTICATION)
    public void setActiveUser(int userId) {
        if (mService != null) try {
            mService.setActiveUser(userId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
    /**
     * Remove given face template from face hardware and/or protected storage.
     * @param face the face item to remove
     * @param userId the user who this face belongs to
     * @param callback an optional callback to verify that face templates have been
     * successfully removed. May be null if no callback is required.
     *
     * @hide
     */
    @RequiresPermission(MANAGE_FACE_AUTHENTICATION)
    public void remove(Face face, int userId, RemovalCallback callback) { }

    /**
     * Enumerate all face templates stored in hardware and/or protected storage.
     * @param userId the user who this face belongs to
     * @param callback an optional callback to verify that face templates have been
     * successfully removed. May be null if no callback is required.
     *
     * @hide
     */
    @RequiresPermission(MANAGE_FACE_AUTHENTICATION)
    public void enumerate(int userId, @NonNull EnumerateCallback callback) { }

    /**
     * Renames the given face template
     * @param faceId the face id
     * @param userId the user who this face belongs to
     * @param newName the new name
     *
     * @hide
     */
    @RequiresPermission(MANAGE_FACE_AUTHENTICATION)
    public void rename(int faceId, int userId, String newName) { }
    /**
     * Obtain the list of enrolled face templates.
     * @return list of current face items
     *
     * @hide
     */
    @RequiresPermission(USE_FACE_AUTHENTICATION)
    public List<Face> getEnrolledFaces(int userId) {
    
    /**
     * Obtain the list of enrolled face templates.
     * @return list of current face items
     *
     * @hide
     */
    @RequiresPermission(USE_FACE_AUTHENTICATION)
    public List<Face> getEnrolledFaces() { }

    /**
     * Determine if there is at least one face enrolled.
     *
     * @return true if at least one face is enrolled, false otherwise
     */
    @RequiresPermission(USE_FACE_AUTHENTICATION)
    public boolean hasEnrolledFaces() { }
    /**
     * @hide
     */
    @RequiresPermission(allOf = {
            USE_FACE_AUTHENTICATION,
            INTERACT_ACROSS_USERS})
    public boolean hasEnrolledFaces(int userId) { }

   /**
     * Determine if face authentication camera hardware is present and functional.
     *
     * @return true if hardware is present and functional, false otherwise.
     */
    @RequiresPermission(USE_FACE_AUTHENTICATION)
    public boolean isHardwareDetected() { }
    /**
     * Retrieves the authenticator token for binding keys to the lifecycle
     * of the calling user's face. Used only by internal clients.
     *
     * @hide
     */
    public long getAuthenticatorId() { }
};

