// Copyright (C) 2026 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! Rust conversion of `android_view_MotionEvent.cpp`.
//!
//! `android.view.MotionEvent` keeps the C++ `android::MotionEvent` (libinput)
//! as the single source of truth: the Java event's `mNativePtr` always holds
//! a C++-`new`-allocated `MotionEvent*`, reached through the cxx bridge in
//! [`crate::motion_event_ffi`]. Rust never boxes one — events are allocated
//! by `motion_event_new` (dropping the `UniquePtr`, or `motion_event_destroy`
//! for raw pointers, is the only way one is freed), and
//! `nativeInitialize`/`nativeCopy`/`nativeSplit`/`nativeReadFromParcel` reuse
//! a non-zero incoming pointer instead of reallocating. `recycle()` is a
//! Java-pool concern only: the native event is retained and reinitialized in
//! place when the Java object is reused.
//!
//! The helpers the C++ file exposed to the rest of libandroid_runtime survive
//! as the `rs_motionEvent_*` C exports below: `core/jni/
//! android_view_MotionEvent.cpp` is now a permanent facade keeping the old
//! mangled signatures (libandroid's `AMotionEvent_fromJava` and friends need
//! real `android::MotionEvent` C++ objects), with events crossing the
//! language boundary as raw `MotionEvent*`.
//!
//! Java `PointerCoords` marshalling splits each pointer's axes into the
//! eleven axes stored as Java float fields and the packed remainder carried
//! by `mPackedAxisBits`/`mPackedAxisValues`; the pure packing logic lives in
//! [`crate::motion_event_compat`], and this module only moves the values
//! across JNI (with `GetPrimitiveArrayCritical` access to the packed array,
//! released with `JNI_ABORT` when it was only read).

use crate::motion_event_compat::{
    packed_axis_values_capacity, BitSet64, PointerCoords, PointerProperties, FIELD_BACKED_AXES,
    HISTORY_CURRENT,
};
use crate::motion_event_ffi::{ffi, MotionEvent};
use jni::objects::{
    JFieldID, JFloatArray, JObject, JObjectArray, JStaticMethodID, JValue, ReleaseMode,
};
use jni::signature::{Primitive, ReturnType};
use jni::sys::{jfieldID, jint, jlong, jmethodID, jobject};
use jni_support::JniError;
use std::pin::Pin;

/// libinput's cap on pointers per event (input.h `MAX_POINTERS`); sizes the
/// stack buffer of `nativeAddBatch`.
const MAX_POINTERS: usize = 16;

/// Cached JNI IDs for `android.view.MotionEvent`, the Rust
/// `gMotionEventClassInfo`. The C++ also cached `recycle`, but its only
/// consumer (`android_view_MotionEvent_recycle`) had no callers and was
/// deleted with the conversion.
#[jni_macros::java_class("android/view/MotionEvent")]
pub struct MotionEventClassInfo {
    #[static_method("() -> android.view.MotionEvent")]
    obtain: jmethodID,
    #[field("long", name = "mNativePtr")]
    m_native_ptr: jfieldID,
}

/// Cached JNI IDs for `android.view.MotionEvent$PointerCoords`, the Rust
/// `gPointerCoordsClassInfo`.
#[jni_macros::java_class("android/view/MotionEvent$PointerCoords")]
pub struct PointerCoordsClassInfo {
    #[field("long", name = "mPackedAxisBits")]
    m_packed_axis_bits: jfieldID,
    #[field("float[]", name = "mPackedAxisValues")]
    m_packed_axis_values: jfieldID,
    #[field("float")]
    x: jfieldID,
    #[field("float")]
    y: jfieldID,
    #[field("float")]
    pressure: jfieldID,
    #[field("float")]
    size: jfieldID,
    #[field("float", name = "touchMajor")]
    touch_major: jfieldID,
    #[field("float", name = "touchMinor")]
    touch_minor: jfieldID,
    #[field("float", name = "toolMajor")]
    tool_major: jfieldID,
    #[field("float", name = "toolMinor")]
    tool_minor: jfieldID,
    #[field("float")]
    orientation: jfieldID,
    #[field("float", name = "relativeX")]
    relative_x: jfieldID,
    #[field("float", name = "relativeY")]
    relative_y: jfieldID,
    #[field("boolean", name = "isResampled")]
    is_resampled: jfieldID,
}

/// Cached JNI IDs for `android.view.MotionEvent$PointerProperties`, the Rust
/// `gPointerPropertiesClassInfo`.
#[jni_macros::java_class("android/view/MotionEvent$PointerProperties")]
pub struct PointerPropertiesClassInfo {
    #[field("int")]
    id: jfieldID,
    #[field("int", name = "toolType")]
    tool_type: jfieldID,
}

/// Borrows the `MotionEvent` behind a Java `mNativePtr`.
///
/// # Safety
///
/// `native_ptr` must be a live `MotionEvent*` from this module's allocation
/// paths (`nativeInitialize`/`nativeCopy`/`nativeSplit`/`nativeReadFromParcel`
/// or `rs_motionEvent_obtainFromNative`), with no mutable borrow live for the
/// call. Java upholds this: `mNativePtr` is only handed to natives after it
/// was initialized, and only freed by `nativeDispose` in `finalize()`.
unsafe fn event_ref<'a>(native_ptr: jlong) -> &'a MotionEvent {
    // SAFETY: Per this function's contract, the pointer is a live event.
    unsafe { &*(native_ptr as *const MotionEvent) }
}

/// Mutably borrows the `MotionEvent` behind a Java `mNativePtr`, pinned as
/// the cxx bridge requires for the C++ object.
///
/// # Safety
///
/// As for [`event_ref`], and no other borrow may be live for the call.
unsafe fn event_mut<'a>(native_ptr: jlong) -> Pin<&'a mut MotionEvent> {
    // SAFETY: Per this function's contract, the pointer is a live event with
    // no aliasing borrow. The event stays where C++ new allocated it, so
    // pinning holds.
    unsafe { Pin::new_unchecked(&mut *(native_ptr as *mut MotionEvent)) }
}

/// Reads a `float` field through a cached field ID. Infallible for the IDs
/// the class-info structs resolved, like the C++ GetFloatField.
fn float_field(env: &mut jni::JNIEnv<'_>, obj: &JObject<'_>, field: jfieldID) -> f32 {
    // SAFETY: `field` is a float field ID resolved by a class-info init, and
    // `obj` is an instance of that class.
    unsafe {
        env.get_field_unchecked(
            obj,
            JFieldID::from_raw(field),
            ReturnType::Primitive(Primitive::Float),
        )
    }
    .and_then(|value| value.f())
    .expect("GetFloatField on a cached field ID cannot fail")
}

/// Writes a `float` field through a cached field ID.
fn set_float_field(env: &mut jni::JNIEnv<'_>, obj: &JObject<'_>, field: jfieldID, value: f32) {
    // SAFETY: `field` is a float field ID resolved by a class-info init.
    let field = unsafe { JFieldID::from_raw(field) };
    env.set_field_unchecked(obj, field, JValue::Float(value))
        .expect("SetFloatField on a cached field ID cannot fail");
}

/// Reads an `int` field through a cached field ID.
fn int_field(env: &mut jni::JNIEnv<'_>, obj: &JObject<'_>, field: jfieldID) -> i32 {
    // SAFETY: `field` is an int field ID resolved by a class-info init.
    unsafe {
        env.get_field_unchecked(
            obj,
            JFieldID::from_raw(field),
            ReturnType::Primitive(Primitive::Int),
        )
    }
    .and_then(|value| value.i())
    .expect("GetIntField on a cached field ID cannot fail")
}

/// Writes an `int` field through a cached field ID.
fn set_int_field(env: &mut jni::JNIEnv<'_>, obj: &JObject<'_>, field: jfieldID, value: i32) {
    // SAFETY: `field` is an int field ID resolved by a class-info init.
    let field = unsafe { JFieldID::from_raw(field) };
    env.set_field_unchecked(obj, field, JValue::Int(value))
        .expect("SetIntField on a cached field ID cannot fail");
}

/// Reads a `long` field through a cached field ID.
fn long_field(env: &mut jni::JNIEnv<'_>, obj: &JObject<'_>, field: jfieldID) -> i64 {
    // SAFETY: `field` is a long field ID resolved by a class-info init.
    unsafe {
        env.get_field_unchecked(
            obj,
            JFieldID::from_raw(field),
            ReturnType::Primitive(Primitive::Long),
        )
    }
    .and_then(|value| value.j())
    .expect("GetLongField on a cached field ID cannot fail")
}

/// Writes a `long` field through a cached field ID.
fn set_long_field(env: &mut jni::JNIEnv<'_>, obj: &JObject<'_>, field: jfieldID, value: i64) {
    // SAFETY: `field` is a long field ID resolved by a class-info init.
    let field = unsafe { JFieldID::from_raw(field) };
    env.set_field_unchecked(obj, field, JValue::Long(value))
        .expect("SetLongField on a cached field ID cannot fail");
}

/// Reads a `boolean` field through a cached field ID.
fn bool_field(env: &mut jni::JNIEnv<'_>, obj: &JObject<'_>, field: jfieldID) -> bool {
    // SAFETY: `field` is a boolean field ID resolved by a class-info init.
    unsafe {
        env.get_field_unchecked(
            obj,
            JFieldID::from_raw(field),
            ReturnType::Primitive(Primitive::Boolean),
        )
    }
    .and_then(|value| value.z())
    .expect("GetBooleanField on a cached field ID cannot fail")
}

/// Writes a `boolean` field through a cached field ID.
fn set_bool_field(env: &mut jni::JNIEnv<'_>, obj: &JObject<'_>, field: jfieldID, value: bool) {
    // SAFETY: `field` is a boolean field ID resolved by a class-info init.
    let field = unsafe { JFieldID::from_raw(field) };
    env.set_field_unchecked(obj, field, JValue::Bool(value as jni::sys::jboolean))
        .expect("SetBooleanField on a cached field ID cannot fail");
}

/// Reads an object field through a cached field ID, returning a new local
/// reference (null if the field is null).
fn object_field<'local>(
    env: &mut jni::JNIEnv<'local>,
    obj: &JObject<'_>,
    field: jfieldID,
) -> JObject<'local> {
    // SAFETY: `field` is an object field ID resolved by a class-info init.
    unsafe { env.get_field_unchecked(obj, JFieldID::from_raw(field), ReturnType::Object) }
        .and_then(|value| value.l())
        .expect("GetObjectField on a cached field ID cannot fail")
}

/// Writes an object field through a cached field ID.
fn set_object_field(
    env: &mut jni::JNIEnv<'_>,
    obj: &JObject<'_>,
    field: jfieldID,
    value: &JObject<'_>,
) {
    // SAFETY: `field` is an object field ID resolved by a class-info init,
    // and `value` is null or an instance of the field's type.
    let field = unsafe { JFieldID::from_raw(field) };
    env.set_field_unchecked(obj, field, JValue::Object(value))
        .expect("SetObjectField on a cached field ID cannot fail");
}

fn validate_pointer_count(env: &mut jni::JNIEnv<'_>, pointer_count: jint) -> bool {
    if pointer_count < 1 {
        JniError::IllegalArgument("pointerCount must be at least 1".to_string()).throw_on(env);
        return false;
    }
    true
}

fn validate_pointer_properties_array(
    env: &mut jni::JNIEnv<'_>,
    array: &JObjectArray<'_>,
    pointer_count: usize,
) -> bool {
    if array.is_null() {
        JniError::IllegalArgument("pointerProperties array must not be null".to_string())
            .throw_on(env);
        return false;
    }
    let length = env.get_array_length(array).expect("GetArrayLength cannot fail");
    if (length as usize) < pointer_count {
        JniError::IllegalArgument(
            "pointerProperties array must be large enough to hold all pointers".to_string(),
        )
        .throw_on(env);
        return false;
    }
    true
}

fn validate_pointer_coords_obj_array(
    env: &mut jni::JNIEnv<'_>,
    array: &JObjectArray<'_>,
    pointer_count: usize,
) -> bool {
    if array.is_null() {
        JniError::IllegalArgument("pointerCoords array must not be null".to_string()).throw_on(env);
        return false;
    }
    let length = env.get_array_length(array).expect("GetArrayLength cannot fail");
    if (length as usize) < pointer_count {
        JniError::IllegalArgument(
            "pointerCoords array must be large enough to hold all pointers".to_string(),
        )
        .throw_on(env);
        return false;
    }
    true
}

fn validate_pointer_index(
    env: &mut jni::JNIEnv<'_>,
    pointer_index: jint,
    event: &MotionEvent,
) -> bool {
    if pointer_index < 0 || pointer_index as usize >= event.pointer_count() {
        // The event dump is built on the error path only, matching the C++'s
        // operator<< stream.
        let message = format!(
            "invalid pointerIndex {pointer_index} for {}",
            ffi::motion_event_to_string(event)
        );
        JniError::IllegalArgument(message).throw_on(env);
        return false;
    }
    true
}

fn validate_history_pos(env: &mut jni::JNIEnv<'_>, history_pos: jint, event: &MotionEvent) -> bool {
    if history_pos < 0 || history_pos as usize >= event.history_size() {
        let message = format!(
            "historyPos {history_pos} out of range for {}",
            ffi::motion_event_to_string(event)
        );
        JniError::IllegalArgument(message).throw_on(env);
        return false;
    }
    true
}

fn validate_pointer_coords(env: &mut jni::JNIEnv<'_>, pointer_coords_obj: &JObject<'_>) -> bool {
    if pointer_coords_obj.is_null() {
        JniError::IllegalArgument("pointerCoords must not be null".to_string()).throw_on(env);
        return false;
    }
    true
}

fn validate_pointer_properties(
    env: &mut jni::JNIEnv<'_>,
    pointer_properties_obj: &JObject<'_>,
) -> bool {
    if pointer_properties_obj.is_null() {
        JniError::IllegalArgument("pointerProperties must not be null".to_string()).throw_on(env);
        return false;
    }
    true
}

/// The eleven axes `MotionEvent$PointerCoords` stores as Java float fields
/// (every other axis travels packed), paired with their field IDs.
fn axes_from_motion_event(info: &PointerCoordsClassInfo) -> [(i32, jfieldID); 11] {
    let fields = [
        info.x,
        info.y,
        info.pressure,
        info.size,
        info.touch_major,
        info.touch_minor,
        info.tool_major,
        info.tool_minor,
        info.orientation,
        info.relative_x,
        info.relative_y,
    ];
    std::array::from_fn(|i| (FIELD_BACKED_AXES[i], fields[i]))
}

/// Reads a Java `PointerCoords` into a native one: the eleven field-backed
/// axes, the resampled flag, then the packed remainder (read with a critical
/// array section, released with `JNI_ABORT` since it is read-only).
fn pointer_coords_to_native(
    env: &mut jni::JNIEnv<'_>,
    pointer_coords_obj: &JObject<'_>,
) -> PointerCoords {
    let info = PointerCoordsClassInfo::get();
    let mut out = PointerCoords::cleared();
    // The C++ ignored setAxisValue's status here; the only reachable failure
    // is running out of packed-value slots, which drops the axis exactly as
    // the C++ did.
    for (axis, field) in axes_from_motion_event(info) {
        let _ = out.set_axis_value(axis, float_field(env, pointer_coords_obj, field));
    }
    out.is_resampled = bool_field(env, pointer_coords_obj, info.is_resampled);

    let bits = long_field(env, pointer_coords_obj, info.m_packed_axis_bits) as u64;
    if !BitSet64(bits).is_empty() {
        let values_obj = object_field(env, pointer_coords_obj, info.m_packed_axis_values);
        if !values_obj.is_null() {
            let values_array = JFloatArray::from(values_obj);
            {
                // A failed GetPrimitiveArrayCritical reads as "no packed
                // values" (the C++ assumed it never fails: UB when it did).
                // SAFETY: The critical section is released (JNI_ABORT via
                // NoCopyBack) when `values` drops at the end of this scope,
                // and no other JNI call happens while it is held.
                let values = unsafe {
                    env.get_array_elements_critical(&values_array, ReleaseMode::NoCopyBack)
                };
                if let Ok(values) = values {
                    out.set_packed_axes(bits, &values);
                }
            }
            let _ = env.delete_local_ref(values_array);
        }
    }
    out
}

/// Returns `mPackedAxisValues` if it can hold `min_size` values, growing it
/// (powers of two from 8) otherwise. `None` means the allocation failed with
/// an exception pending — and, like the C++, the failed null result has
/// already been stored into the field.
fn obtain_packed_axis_values_array<'local>(
    env: &mut jni::JNIEnv<'local>,
    min_size: u32,
    out_pointer_coords_obj: &JObject<'_>,
) -> Option<JFloatArray<'local>> {
    let info = PointerCoordsClassInfo::get();
    let existing = object_field(env, out_pointer_coords_obj, info.m_packed_axis_values);
    if !existing.is_null() {
        let existing = JFloatArray::from(existing);
        let size = env.get_array_length(&existing).expect("GetArrayLength cannot fail") as u32;
        if min_size <= size {
            return Some(existing);
        }
        let _ = env.delete_local_ref(existing);
    }
    match env.new_float_array(packed_axis_values_capacity(min_size) as jint) {
        Ok(array) => {
            set_object_field(env, out_pointer_coords_obj, info.m_packed_axis_values, &array);
            Some(array)
        }
        Err(_) => {
            // OOM: the C++ stored NewFloatArray's null result before its
            // caller bailed out; preserve that observable field state.
            set_object_field(
                env,
                out_pointer_coords_obj,
                info.m_packed_axis_values,
                &JObject::null(),
            );
            None
        }
    }
}

/// Writes the packed remainder of a native `PointerCoords` into a Java one:
/// the values of `axes_to_copy` go into `mPackedAxisValues` (grown on
/// demand) and the presence word into `mPackedAxisBits`. On allocation
/// failure the bits word is left untouched, like the C++'s OOM path.
fn pointer_coords_from_native(
    env: &mut jni::JNIEnv<'_>,
    raw_pointer_coords: &PointerCoords,
    axes_to_copy: BitSet64,
    out_pointer_coords_obj: &JObject<'_>,
) {
    let info = PointerCoordsClassInfo::get();
    let mut out_bits = 0u64;
    if !axes_to_copy.is_empty() {
        let Some(out_values_array) =
            obtain_packed_axis_values_array(env, axes_to_copy.count(), out_pointer_coords_obj)
        else {
            return; // OOM.
        };
        // SAFETY: The critical section is released (with copy-back, the
        // values were written) when `values` drops, and no other JNI call
        // happens while it is held.
        match unsafe { env.get_array_elements_critical(&out_values_array, ReleaseMode::CopyBack) } {
            Ok(mut values) => {
                out_bits = raw_pointer_coords.export_packed_axes(axes_to_copy, &mut values);
            }
            // The C++ assumed GetPrimitiveArrayCritical never fails (UB if
            // it did); bail out with the pending exception instead.
            Err(_) => return,
        }
        let _ = env.delete_local_ref(out_values_array);
    }
    set_long_field(env, out_pointer_coords_obj, info.m_packed_axis_bits, out_bits as i64);
}

/// Reads a Java `PointerProperties` into a native one.
fn pointer_properties_to_native(
    env: &mut jni::JNIEnv<'_>,
    pointer_properties_obj: &JObject<'_>,
) -> PointerProperties {
    let info = PointerPropertiesClassInfo::get();
    PointerProperties {
        id: int_field(env, pointer_properties_obj, info.id),
        tool_type: int_field(env, pointer_properties_obj, info.tool_type),
    }
}

/// Writes a native `PointerProperties` into a Java one.
fn pointer_properties_from_native(
    env: &mut jni::JNIEnv<'_>,
    pointer_properties: &PointerProperties,
    out_pointer_properties_obj: &JObject<'_>,
) {
    let info = PointerPropertiesClassInfo::get();
    set_int_field(env, out_pointer_properties_obj, info.id, pointer_properties.id);
    set_int_field(env, out_pointer_properties_obj, info.tool_type, pointer_properties.tool_type);
}

/// Reads a Java event's `mNativePtr`.
fn get_native_ptr(env: &mut jni::JNIEnv<'_>, event_obj: &JObject<'_>) -> *mut MotionEvent {
    long_field(env, event_obj, MotionEventClassInfo::get().m_native_ptr) as *mut MotionEvent
}

/// Obtains a Java `MotionEvent` from the Java pool and hands it ownership of
/// `event`, deleting whatever native event the pooled object still carried.
/// Returns a new local reference. Aborts — the C++ `LOG_ALWAYS_FATAL` — if
/// `MotionEvent.obtain()` throws or returns null.
///
/// # Safety
///
/// `event` must be a live `MotionEvent*` allocated via the bridge, not used
/// again by the caller: ownership moves to the returned Java object.
unsafe fn obtain_from_native(env: &mut jni::JNIEnv<'_>, event: *mut MotionEvent) -> jobject {
    let info = MotionEventClassInfo::get();
    // SAFETY: `obtain` is MotionEvent.obtain with signature
    // ()Landroid/view/MotionEvent;, resolved by MotionEventClassInfo::init;
    // it takes no arguments.
    let obtained = unsafe {
        env.call_static_method_unchecked(
            &info.class,
            JStaticMethodID::from_raw(info.obtain),
            ReturnType::Object,
            &[],
        )
    };
    let event_obj = match obtained.and_then(|value| value.l()) {
        Ok(event_obj) if !event_obj.is_null() => event_obj,
        _ => {
            // The C++ logged the pending exception and LOG_ALWAYS_FATAL'd;
            // the panic aborts the same way under the platform panic=abort.
            let _ = env.exception_describe();
            panic!("An exception occurred while obtaining a Java motion event.");
        }
    };
    let old_event = get_native_ptr(env, &event_obj);
    // SAFETY: A pooled event's mNativePtr is null or a live event owned by
    // the Java object; it is replaced below and never referenced again.
    unsafe { ffi::motion_event_destroy(old_event) };
    set_long_field(env, &event_obj, info.m_native_ptr, event as jlong);
    event_obj.into_raw()
}

/// C export behind the facade's `android_view_MotionEvent_getNativePtr`:
/// reads the `MotionEvent*` out of a Java event's `mNativePtr`. Returns null
/// for a null `event_obj` or an uninitialized event.
///
/// # Safety
///
/// `env` must be a valid `JNIEnv` pointer for the current thread, and
/// `event_obj` must be null or a live `android.view.MotionEvent` reference.
#[no_mangle]
pub unsafe extern "C" fn rs_motionEvent_getNativePtr(
    env: *mut jni::sys::JNIEnv,
    event_obj: jobject,
) -> *mut MotionEvent {
    if event_obj.is_null() {
        return std::ptr::null_mut();
    }
    // SAFETY: The facade passes the live JNIEnv of the calling thread.
    let mut env = unsafe { jni::JNIEnv::from_raw(env) }.expect("null JNIEnv");
    // SAFETY: Per this function's contract, `event_obj` is a live MotionEvent
    // reference; the JObject wrapper borrows it without deleting it.
    let event_obj = unsafe { JObject::from_raw(event_obj) };
    get_native_ptr(&mut env, &event_obj)
}

/// C export behind the facade's `android_view_MotionEvent_obtainFromNative`:
/// obtains a Java `MotionEvent` owning `event` and returns a new local
/// reference. A null `event` returns null; a failing `MotionEvent.obtain()`
/// aborts (the C++ `LOG_ALWAYS_FATAL`).
///
/// # Safety
///
/// `env` must be a valid `JNIEnv` pointer for the current thread, and
/// `event` must be null or a live `MotionEvent*` (C++-new-allocated) whose
/// ownership transfers to the returned Java object.
#[no_mangle]
pub unsafe extern "C" fn rs_motionEvent_obtainFromNative(
    env: *mut jni::sys::JNIEnv,
    event: *mut MotionEvent,
) -> jobject {
    if event.is_null() {
        return std::ptr::null_mut();
    }
    // SAFETY: The facade passes the live JNIEnv of the calling thread.
    let mut env = unsafe { jni::JNIEnv::from_raw(env) }.expect("null JNIEnv");
    // SAFETY: Per this function's contract, `event` is live and owned by the
    // caller until this call, which transfers it to the Java object.
    unsafe { obtain_from_native(&mut env, event) }
}

/// Registers `android.view.MotionEvent`'s native methods and caches its (and
/// its inner classes') JNI IDs. Call during JNI startup; panics if the class
/// or any ID is missing, matching the C++ `RegisterMethodsOrDie` semantics.
pub fn register(env: &mut jni::JNIEnv<'_>) {
    MotionEventClassInfo::init(env);
    PointerCoordsClassInfo::init(env);
    PointerPropertiesClassInfo::init(env);
    motion_event::register(env);
}

/// The `android.view.MotionEvent` native methods: 9 regular, 7 @FastNative,
/// and 36 @CriticalNative, matching the annotations in MotionEvent.java.
#[jni_macros::jni_module("android/view/MotionEvent")]
pub mod motion_event {
    use super::{
        axes_from_motion_event, event_mut, event_ref, ffi, pointer_coords_from_native,
        pointer_coords_to_native, pointer_properties_from_native, pointer_properties_to_native,
        validate_history_pos, validate_pointer_coords, validate_pointer_coords_obj_array,
        validate_pointer_count, validate_pointer_index, validate_pointer_properties,
        validate_pointer_properties_array, JniError, MotionEvent, PointerCoords,
        PointerCoordsClassInfo, HISTORY_CURRENT, MAX_POINTERS,
    };
    use crate::input_event_ffi;
    use crate::motion_event_ffi::{self, MotionEventInitArgs};
    use jni::objects::{JObject, JObjectArray};
    use jni::strings::JavaStr;
    use jni::sys::{jclass, jfloat, jint, jlong, jobject, jstring};

    /// Regular native:
    /// "(JIIIIIIIIIFFFFJJI[Landroid/view/MotionEvent$PointerProperties;[Landroid/view/MotionEvent$PointerCoords;)J"
    ///
    /// The validation exceptions keep the C++ order: the pointer count and
    /// both arrays are checked (IllegalArgumentException) before any array
    /// element is touched; only a null `pointerCoords` element then throws
    /// NullPointerException, while a null `pointerProperties` element
    /// silently returns 0 — and once validation has passed, a non-zero
    /// incoming event is owned, so those element failures delete it.
    #[allow(clippy::too_many_arguments)]
    #[jni_method]
    fn nativeInitialize(
        env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        native_ptr: jlong,
        device_id: jint,
        source: jint,
        display_id: jint,
        action: jint,
        flags: jint,
        edge_flags: jint,
        meta_state: jint,
        button_state: jint,
        classification: jint,
        x_offset: jfloat,
        y_offset: jfloat,
        x_precision: jfloat,
        y_precision: jfloat,
        down_time_nanos: jlong,
        event_time_nanos: jlong,
        pointer_count: jint,
        #[class = "android/view/MotionEvent$PointerProperties"]
        pointer_properties_obj_array: &JObjectArray,
        #[class = "android/view/MotionEvent$PointerCoords"] pointer_coords_obj_array: &JObjectArray,
    ) -> jlong {
        if !validate_pointer_count(env, pointer_count)
            || !validate_pointer_properties_array(
                env,
                pointer_properties_obj_array,
                pointer_count as usize,
            )
            || !validate_pointer_coords_obj_array(
                env,
                pointer_coords_obj_array,
                pointer_count as usize,
            )
        {
            return 0;
        }

        // From here the event is owned (the C++ unique_ptr): every early
        // return below deletes it, reused incoming pointer included.
        let mut event = if native_ptr != 0 {
            // SAFETY: A non-zero mNativePtr is a live event owned by the Java
            // object, which expects this native to consume and replace it.
            unsafe { cxx::UniquePtr::from_raw(native_ptr as *mut MotionEvent) }
        } else {
            ffi::motion_event_new()
        };

        let mut pointer_properties = Vec::with_capacity(pointer_count as usize);
        let mut raw_pointer_coords = Vec::with_capacity(pointer_count as usize);
        for i in 0..pointer_count {
            let pointer_properties_obj = env
                .get_object_array_element(pointer_properties_obj_array, i)
                .expect("GetObjectArrayElement within a validated range cannot fail");
            if pointer_properties_obj.is_null() {
                // The C++ returned 0 without an exception for this case.
                return 0;
            }
            pointer_properties.push(pointer_properties_to_native(env, &pointer_properties_obj));
            let _ = env.delete_local_ref(pointer_properties_obj);

            let pointer_coords_obj = env
                .get_object_array_element(pointer_coords_obj_array, i)
                .expect("GetObjectArrayElement within a validated range cannot fail");
            if pointer_coords_obj.is_null() {
                JniError::NullPointer("pointerCoords").throw_on(env);
                return 0;
            }
            raw_pointer_coords.push(pointer_coords_to_native(env, &pointer_coords_obj));
            let _ = env.delete_local_ref(pointer_coords_obj);
        }

        let args = MotionEventInitArgs {
            down_time: down_time_nanos,
            event_time: event_time_nanos,
            id: input_event_ffi::next_input_event_id(),
            device_id,
            source: source as u32,
            display_id,
            action,
            action_button: 0,
            flags,
            edge_flags,
            meta_state,
            button_state,
            classification,
            x_offset,
            y_offset,
            x_precision,
            y_precision,
        };
        // The bridge owns the rest of the old nativeInitialize: orientation
        // private-flag promotion, the window transform, and the in-place
        // inverse mapping of the raw coordinates.
        ffi::motion_event_initialize(
            event.pin_mut(),
            &args,
            &pointer_properties,
            &mut raw_pointer_coords,
        );
        event.into_raw() as jlong
    }

    /// Regular native: "(J)V"
    #[jni_method]
    fn nativeDispose(_env: &mut jni::JNIEnv<'_>, _clazz: jclass, native_ptr: jlong) {
        // SAFETY: mNativePtr is null or a live event owned by the Java
        // object; finalize() zeroes the field after this call.
        unsafe { ffi::motion_event_destroy(native_ptr as *mut MotionEvent) };
    }

    /// Regular native: "(JJ[Landroid/view/MotionEvent$PointerCoords;I)V"
    #[jni_method]
    fn nativeAddBatch(
        env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        native_ptr: jlong,
        event_time_nanos: jlong,
        #[class = "android/view/MotionEvent$PointerCoords"] pointer_coords_obj_array: &JObjectArray,
        meta_state: jint,
    ) {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        let pointer_count = unsafe { event_ref(native_ptr) }.pointer_count();
        if !validate_pointer_coords_obj_array(env, pointer_coords_obj_array, pointer_count) {
            return;
        }

        // Events are capped at MAX_POINTERS everywhere in libinput, so the
        // hot path fills a stack buffer where the C++ used a std::vector;
        // the heap fallback keeps its tolerance of oversized Java events.
        let mut stack_coords = [PointerCoords::cleared(); MAX_POINTERS];
        let mut heap_coords;
        let raw_pointer_coords: &mut [PointerCoords] = if pointer_count <= MAX_POINTERS {
            &mut stack_coords[..pointer_count]
        } else {
            heap_coords = vec![PointerCoords::cleared(); pointer_count];
            &mut heap_coords
        };
        for (i, raw_coords) in raw_pointer_coords.iter_mut().enumerate() {
            let pointer_coords_obj = env
                .get_object_array_element(pointer_coords_obj_array, i as jint)
                .expect("GetObjectArrayElement within a validated range cannot fail");
            if pointer_coords_obj.is_null() {
                JniError::NullPointer("pointerCoords").throw_on(env);
                return;
            }
            *raw_coords = pointer_coords_to_native(env, &pointer_coords_obj);
            let _ = env.delete_local_ref(pointer_coords_obj);
        }

        // SAFETY: mNativePtr always holds a live event, and the shared
        // borrow above ended; see event_mut.
        let mut event = unsafe { event_mut(native_ptr) };
        // The bridge maps the coordinates through the inverse event
        // transform in place and appends the sample under the current id.
        ffi::motion_event_add_sample_transformed(
            event.as_mut(),
            event_time_nanos,
            raw_pointer_coords,
        );
        let meta_state = event.meta_state() | meta_state;
        event.set_meta_state(meta_state);
    }

    /// Regular native: "(JIILandroid/view/MotionEvent$PointerCoords;)V"
    #[jni_method]
    fn nativeGetPointerCoords(
        env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        native_ptr: jlong,
        pointer_index: jint,
        history_pos: jint,
        #[class = "android/view/MotionEvent$PointerCoords"] out_pointer_coords_obj: &JObject,
    ) {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        let event = unsafe { event_ref(native_ptr) };
        if !validate_pointer_index(env, pointer_index, event)
            || !validate_pointer_coords(env, out_pointer_coords_obj)
        {
            return;
        }
        if history_pos != HISTORY_CURRENT && !validate_history_pos(env, history_pos, event) {
            return;
        }
        let info = PointerCoordsClassInfo::get();
        let index = pointer_index as usize;

        // The eleven field-backed axes come from the MotionEvent itself
        // (transformed values), not from the raw PointerCoords.
        for (axis, field) in axes_from_motion_event(info) {
            let value = if history_pos == HISTORY_CURRENT {
                event.axis_value(axis, index)
            } else {
                event.historical_axis_value(axis, index, history_pos as usize)
            };
            super::set_float_field(env, out_pointer_coords_obj, field, value);
        }

        let raw_pointer_coords = if history_pos == HISTORY_CURRENT {
            ffi::motion_event_raw_pointer_coords(event, index)
        } else {
            ffi::motion_event_historical_raw_pointer_coords(event, index, history_pos as usize)
        };
        // Everything but the field-backed axes travels through the packed words.
        let axes_to_copy = raw_pointer_coords.packed_remainder_bits();
        pointer_coords_from_native(env, raw_pointer_coords, axes_to_copy, out_pointer_coords_obj);

        let is_resampled = if history_pos == HISTORY_CURRENT {
            event.is_resampled(index, event.history_size())
        } else {
            event.is_resampled(index, history_pos as usize)
        };
        super::set_bool_field(env, out_pointer_coords_obj, info.is_resampled, is_resampled);
    }

    /// Regular native: "(JILandroid/view/MotionEvent$PointerProperties;)V"
    #[jni_method]
    fn nativeGetPointerProperties(
        env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        native_ptr: jlong,
        pointer_index: jint,
        #[class = "android/view/MotionEvent$PointerProperties"]
        out_pointer_properties_obj: &JObject,
    ) {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        let event = unsafe { event_ref(native_ptr) };
        if !validate_pointer_index(env, pointer_index, event)
            || !validate_pointer_properties(env, out_pointer_properties_obj)
        {
            return;
        }
        let pointer_properties =
            ffi::motion_event_pointer_properties(event, pointer_index as usize);
        pointer_properties_from_native(env, pointer_properties, out_pointer_properties_obj);
    }

    /// Regular native: "(JLandroid/os/Parcel;)J"
    ///
    /// Reuses a non-zero incoming event; allocates otherwise, and on a failed
    /// read deletes the event only if this call allocated it, then throws
    /// RuntimeException.
    #[jni_method]
    fn nativeReadFromParcel(
        env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        native_ptr: jlong,
        #[class = "android/os/Parcel"] parcel_obj: jobject,
    ) -> jlong {
        let allocated = native_ptr == 0;
        let event = if allocated {
            ffi::motion_event_new().into_raw()
        } else {
            native_ptr as *mut MotionEvent
        };
        // SAFETY: `event` is live, `env` is the current thread's JNIEnv, and
        // `parcel_obj` is the caller's android.os.Parcel.
        let status = unsafe {
            motion_event_ffi::android_runtime_motion_event_read_from_parcel(
                event,
                env.get_raw(),
                parcel_obj,
            )
        };
        if status != 0 {
            if allocated {
                // SAFETY: The event was allocated above and escapes nowhere.
                unsafe { ffi::motion_event_destroy(event) };
            }
            JniError::Runtime("Failed to read MotionEvent parcel.".to_string()).throw_on(env);
            return 0;
        }
        event as jlong
    }

    /// Regular native: "(JLandroid/os/Parcel;)V"
    #[jni_method]
    fn nativeWriteToParcel(
        env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        native_ptr: jlong,
        #[class = "android/os/Parcel"] parcel_obj: jobject,
    ) {
        // SAFETY: mNativePtr always holds a live event, `env` is the current
        // thread's JNIEnv, and `parcel_obj` is the caller's Parcel.
        let status = unsafe {
            motion_event_ffi::android_runtime_motion_event_write_to_parcel(
                native_ptr as *const MotionEvent,
                env.get_raw(),
                parcel_obj,
            )
        };
        if status != 0 {
            JniError::Runtime("Failed to write MotionEvent parcel.".to_string()).throw_on(env);
        }
    }

    /// Regular native: "(I)Ljava/lang/String;"
    ///
    /// Axes without a label return null, preserving the C++'s
    /// `NewStringUTF(nullptr)` behavior. Labels are ASCII, so the UTF-8 →
    /// Modified UTF-8 conversion in `new_string` is the identity.
    #[jni_method]
    fn nativeAxisToString(env: &mut jni::JNIEnv<'_>, _clazz: jclass, axis: jint) -> jstring {
        let Some(label) = motion_event_ffi::axis_label(axis) else {
            return std::ptr::null_mut();
        };
        match env.new_string(label.to_string_lossy()) {
            Ok(label) => label.into_raw(),
            // Allocation failed; the exception is pending for Java, as it
            // would have been after NewStringUTF.
            Err(_) => std::ptr::null_mut(),
        }
    }

    /// Regular native: "(Ljava/lang/String;)I"
    ///
    /// The label reaches libinput as the same NUL-terminated Modified UTF-8
    /// bytes ScopedUtfChars produced for the C++. A null label throws
    /// NullPointerException and returns 0 (the C++ threw the same NPE, then
    /// kept going and crashed reading the null string).
    #[jni_method]
    fn nativeAxisFromString(_env: &mut jni::JNIEnv<'_>, _clazz: jclass, label: &JavaStr) -> jint {
        motion_event_ffi::axis_from_label(label)
    }

    /// @FastNative: "(JI)I"
    #[jni_method(fast)]
    fn nativeGetPointerId(
        env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        native_ptr: jlong,
        pointer_index: jint,
    ) -> jint {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        let event = unsafe { event_ref(native_ptr) };
        if !validate_pointer_index(env, pointer_index, event) {
            return -1;
        }
        // SAFETY: `pointer_index` was validated against the pointer count.
        unsafe { event.pointer_id(pointer_index as usize) }
    }

    /// @FastNative: "(JI)I"
    #[jni_method(fast)]
    fn nativeGetToolType(
        env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        native_ptr: jlong,
        pointer_index: jint,
    ) -> jint {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        let event = unsafe { event_ref(native_ptr) };
        if !validate_pointer_index(env, pointer_index, event) {
            return -1;
        }
        ffi::motion_event_get_tool_type(event, pointer_index as usize)
    }

    /// @FastNative: "(JI)J"
    #[jni_method(fast)]
    fn nativeGetEventTimeNanos(
        env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        native_ptr: jlong,
        history_pos: jint,
    ) -> jlong {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        let event = unsafe { event_ref(native_ptr) };
        if history_pos == HISTORY_CURRENT {
            // SAFETY: Java never exposes an uninitialized event, so it holds
            // at least one sample.
            unsafe { event.event_time() }
        } else {
            if !validate_history_pos(env, history_pos, event) {
                return 0;
            }
            // SAFETY: `history_pos` was validated against the history size.
            unsafe { event.historical_event_time(history_pos as usize) }
        }
    }

    /// @FastNative: "(JIII)F"
    #[jni_method(fast)]
    fn nativeGetRawAxisValue(
        env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        native_ptr: jlong,
        axis: jint,
        pointer_index: jint,
        history_pos: jint,
    ) -> jfloat {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        let event = unsafe { event_ref(native_ptr) };
        if !validate_pointer_index(env, pointer_index, event) {
            return 0.0;
        }
        if history_pos == HISTORY_CURRENT {
            event.raw_axis_value(axis, pointer_index as usize)
        } else {
            if !validate_history_pos(env, history_pos, event) {
                return 0.0;
            }
            event.historical_raw_axis_value(axis, pointer_index as usize, history_pos as usize)
        }
    }

    /// @FastNative: "(JIII)F"
    #[jni_method(fast)]
    fn nativeGetAxisValue(
        env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        native_ptr: jlong,
        axis: jint,
        pointer_index: jint,
        history_pos: jint,
    ) -> jfloat {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        let event = unsafe { event_ref(native_ptr) };
        if !validate_pointer_index(env, pointer_index, event) {
            return 0.0;
        }
        if history_pos == HISTORY_CURRENT {
            event.axis_value(axis, pointer_index as usize)
        } else {
            if !validate_history_pos(env, history_pos, event) {
                return 0.0;
            }
            event.historical_axis_value(axis, pointer_index as usize, history_pos as usize)
        }
    }

    /// @FastNative: "(JLandroid/graphics/Matrix;)V"
    #[jni_method(fast)]
    fn nativeTransform(
        env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        native_ptr: jlong,
        #[class = "android/graphics/Matrix"] matrix_obj: jobject,
    ) {
        let mut matrix = [0.0f32; 9];
        // SAFETY: `env` is the current thread's JNIEnv, `matrix_obj` is the
        // caller's android.graphics.Matrix, and the buffer holds the nine
        // floats AMatrix_getContents writes.
        unsafe { super::AMatrix_getContents(env.get_raw(), matrix_obj, matrix.as_mut_ptr()) };
        // SAFETY: mNativePtr always holds a live event; see event_mut.
        unsafe { event_mut(native_ptr) }.transform(&matrix);
    }

    /// @FastNative: "(JLandroid/graphics/Matrix;)V"
    #[jni_method(fast)]
    fn nativeApplyTransform(
        env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        native_ptr: jlong,
        #[class = "android/graphics/Matrix"] matrix_obj: jobject,
    ) {
        let mut matrix = [0.0f32; 9];
        // SAFETY: As in nativeTransform.
        unsafe { super::AMatrix_getContents(env.get_raw(), matrix_obj, matrix.as_mut_ptr()) };
        // SAFETY: mNativePtr always holds a live event; see event_mut.
        unsafe { event_mut(native_ptr) }.apply_transform(&matrix);
    }

    /// @CriticalNative: "(JJZ)J"
    ///
    /// Reuses a non-zero destination event; allocates otherwise.
    #[jni_method(critical)]
    fn nativeCopy(dest_native_ptr: jlong, source_native_ptr: jlong, keep_history: bool) -> jlong {
        let dest_native_ptr = if dest_native_ptr != 0 {
            dest_native_ptr
        } else {
            ffi::motion_event_new().into_raw() as jlong
        };
        // SAFETY: `dest_native_ptr` is the caller's live event or was just
        // allocated; the source's mNativePtr always holds a live, distinct
        // event; see event_mut/event_ref.
        unsafe {
            ffi::motion_event_copy_from(
                event_mut(dest_native_ptr),
                event_ref(source_native_ptr),
                keep_history,
            );
        }
        dest_native_ptr
    }

    /// @CriticalNative: "(JJI)J"
    ///
    /// Reuses a non-zero destination event; allocates otherwise. Bit `n` of
    /// `id_bits` selects pointer id `n`, as in the C++ bitset conversion.
    #[jni_method(critical)]
    fn nativeSplit(dest_native_ptr: jlong, source_native_ptr: jlong, id_bits: jint) -> jlong {
        let dest_native_ptr = if dest_native_ptr != 0 {
            dest_native_ptr
        } else {
            ffi::motion_event_new().into_raw() as jlong
        };
        // SAFETY: As in nativeCopy.
        unsafe {
            ffi::motion_event_split_from(
                event_mut(dest_native_ptr),
                event_ref(source_native_ptr),
                id_bits as u32,
                input_event_ffi::next_input_event_id(),
            );
        }
        dest_native_ptr
    }

    /// @CriticalNative: "(J)I"
    #[jni_method(critical)]
    fn nativeGetId(native_ptr: jlong) -> jint {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        unsafe { event_ref(native_ptr) }.id()
    }

    /// @CriticalNative: "(J)I"
    #[jni_method(critical)]
    fn nativeGetDeviceId(native_ptr: jlong) -> jint {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        unsafe { event_ref(native_ptr) }.device_id()
    }

    /// @CriticalNative: "(J)I"
    #[jni_method(critical)]
    fn nativeGetSource(native_ptr: jlong) -> jint {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        unsafe { event_ref(native_ptr) }.source() as jint
    }

    /// @CriticalNative: "(JI)V"
    #[jni_method(critical)]
    fn nativeSetSource(native_ptr: jlong, source: jint) {
        // SAFETY: mNativePtr always holds a live event; see event_mut.
        unsafe { event_mut(native_ptr) }.set_source(source as u32);
    }

    /// @CriticalNative: "(J)I"
    #[jni_method(critical)]
    fn nativeGetDisplayId(native_ptr: jlong) -> jint {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        ffi::motion_event_get_display_id(unsafe { event_ref(native_ptr) })
    }

    /// @CriticalNative: "(JI)V"
    #[jni_method(critical)]
    fn nativeSetDisplayId(native_ptr: jlong, display_id: jint) {
        // SAFETY: mNativePtr always holds a live event; see event_mut.
        ffi::motion_event_set_display_id(unsafe { event_mut(native_ptr) }, display_id);
    }

    /// @CriticalNative: "(J)I"
    #[jni_method(critical)]
    fn nativeGetAction(native_ptr: jlong) -> jint {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        unsafe { event_ref(native_ptr) }.action()
    }

    /// @CriticalNative: "(JI)V"
    #[jni_method(critical)]
    fn nativeSetAction(native_ptr: jlong, action: jint) {
        // SAFETY: mNativePtr always holds a live event; see event_mut.
        unsafe { event_mut(native_ptr) }.set_action(action);
    }

    /// @CriticalNative: "(J)I"
    #[jni_method(critical)]
    fn nativeGetActionButton(native_ptr: jlong) -> jint {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        unsafe { event_ref(native_ptr) }.action_button()
    }

    /// @CriticalNative: "(JI)V"
    #[jni_method(critical)]
    fn nativeSetActionButton(native_ptr: jlong, button: jint) {
        // SAFETY: mNativePtr always holds a live event; see event_mut.
        unsafe { event_mut(native_ptr) }.set_action_button(button);
    }

    /// @CriticalNative: "(J)Z"
    #[jni_method(critical)]
    fn nativeIsTouchEvent(native_ptr: jlong) -> bool {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        unsafe { event_ref(native_ptr) }.is_touch_event()
    }

    /// @CriticalNative: "(J)I"
    ///
    /// Private flags are masked out so they never reach Java.
    #[jni_method(critical)]
    fn nativeGetFlags(native_ptr: jlong) -> jint {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        let flags = ffi::motion_event_get_flags(unsafe { event_ref(native_ptr) });
        flags & !ffi::motion_event_private_flag_mask()
    }

    /// @CriticalNative: "(JI)V"
    ///
    /// The event's private flags are preserved and Java-supplied private
    /// bits discarded, so they cannot be set from Java.
    #[jni_method(critical)]
    fn nativeSetFlags(native_ptr: jlong, flags: jint) {
        let mask = ffi::motion_event_private_flag_mask();
        // SAFETY: mNativePtr always holds a live event; see event_mut.
        let mut event = unsafe { event_mut(native_ptr) };
        let private_flags = ffi::motion_event_get_flags(event.as_ref().get_ref()) & mask;
        ffi::motion_event_set_flags(event.as_mut(), (flags & !mask) | private_flags);
    }

    /// @CriticalNative: "(J)I"
    #[jni_method(critical)]
    fn nativeGetEdgeFlags(native_ptr: jlong) -> jint {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        unsafe { event_ref(native_ptr) }.edge_flags()
    }

    /// @CriticalNative: "(JI)V"
    #[jni_method(critical)]
    fn nativeSetEdgeFlags(native_ptr: jlong, edge_flags: jint) {
        // SAFETY: mNativePtr always holds a live event; see event_mut.
        unsafe { event_mut(native_ptr) }.set_edge_flags(edge_flags);
    }

    /// @CriticalNative: "(J)I"
    #[jni_method(critical)]
    fn nativeGetMetaState(native_ptr: jlong) -> jint {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        unsafe { event_ref(native_ptr) }.meta_state()
    }

    /// @CriticalNative: "(J)I"
    #[jni_method(critical)]
    fn nativeGetButtonState(native_ptr: jlong) -> jint {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        unsafe { event_ref(native_ptr) }.button_state()
    }

    /// @CriticalNative: "(JI)V"
    #[jni_method(critical)]
    fn nativeSetButtonState(native_ptr: jlong, button_state: jint) {
        // SAFETY: mNativePtr always holds a live event; see event_mut.
        unsafe { event_mut(native_ptr) }.set_button_state(button_state);
    }

    /// @CriticalNative: "(J)I"
    #[jni_method(critical)]
    fn nativeGetClassification(native_ptr: jlong) -> jint {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        ffi::motion_event_get_classification(unsafe { event_ref(native_ptr) })
    }

    /// @CriticalNative: "(JFF)V"
    #[jni_method(critical)]
    fn nativeOffsetLocation(native_ptr: jlong, delta_x: jfloat, delta_y: jfloat) {
        // SAFETY: mNativePtr always holds a live event; see event_mut.
        unsafe { event_mut(native_ptr) }.offset_location(delta_x, delta_y);
    }

    /// @CriticalNative: "(J)F"
    #[jni_method(critical)]
    fn nativeGetRawXOffset(native_ptr: jlong) -> jfloat {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        unsafe { event_ref(native_ptr) }.raw_x_offset()
    }

    /// @CriticalNative: "(J)F"
    #[jni_method(critical)]
    fn nativeGetRawYOffset(native_ptr: jlong) -> jfloat {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        unsafe { event_ref(native_ptr) }.raw_y_offset()
    }

    /// @CriticalNative: "(J)F"
    #[jni_method(critical)]
    fn nativeGetXPrecision(native_ptr: jlong) -> jfloat {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        unsafe { event_ref(native_ptr) }.x_precision()
    }

    /// @CriticalNative: "(J)F"
    #[jni_method(critical)]
    fn nativeGetYPrecision(native_ptr: jlong) -> jfloat {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        unsafe { event_ref(native_ptr) }.y_precision()
    }

    /// @CriticalNative: "(J)F"
    #[jni_method(critical)]
    fn nativeGetXCursorPosition(native_ptr: jlong) -> jfloat {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        unsafe { event_ref(native_ptr) }.x_cursor_position()
    }

    /// @CriticalNative: "(J)F"
    #[jni_method(critical)]
    fn nativeGetYCursorPosition(native_ptr: jlong) -> jfloat {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        unsafe { event_ref(native_ptr) }.y_cursor_position()
    }

    /// @CriticalNative: "(JFF)V"
    #[jni_method(critical)]
    fn nativeSetCursorPosition(native_ptr: jlong, x: jfloat, y: jfloat) {
        // SAFETY: mNativePtr always holds a live event; see event_mut.
        unsafe { event_mut(native_ptr) }.set_cursor_position(x, y);
    }

    /// @CriticalNative: "(J)J"
    #[jni_method(critical)]
    fn nativeGetDownTimeNanos(native_ptr: jlong) -> jlong {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        unsafe { event_ref(native_ptr) }.down_time()
    }

    /// @CriticalNative: "(JJ)V"
    #[jni_method(critical)]
    fn nativeSetDownTimeNanos(native_ptr: jlong, down_time_nanos: jlong) {
        // SAFETY: mNativePtr always holds a live event; see event_mut.
        unsafe { event_mut(native_ptr) }.set_down_time(down_time_nanos);
    }

    /// @CriticalNative: "(J)I"
    #[jni_method(critical)]
    fn nativeGetPointerCount(native_ptr: jlong) -> jint {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        unsafe { event_ref(native_ptr) }.pointer_count() as jint
    }

    /// @CriticalNative: "(JI)I"
    #[jni_method(critical)]
    fn nativeFindPointerIndex(native_ptr: jlong, pointer_id: jint) -> jint {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        unsafe { event_ref(native_ptr) }.find_pointer_index(pointer_id) as jint
    }

    /// @CriticalNative: "(J)I"
    #[jni_method(critical)]
    fn nativeGetHistorySize(native_ptr: jlong) -> jint {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        unsafe { event_ref(native_ptr) }.history_size() as jint
    }

    /// @CriticalNative: "(JF)V"
    #[jni_method(critical)]
    fn nativeScale(native_ptr: jlong, scale: jfloat) {
        // SAFETY: mNativePtr always holds a live event; see event_mut.
        unsafe { event_mut(native_ptr) }.scale(scale);
    }

    /// @CriticalNative: "(J)I"
    ///
    /// Returns -1 when the transform is not a pure surface rotation.
    #[jni_method(critical)]
    fn nativeGetSurfaceRotation(native_ptr: jlong) -> jint {
        // SAFETY: mNativePtr always holds a live event; see event_ref.
        ffi::motion_event_get_surface_rotation(unsafe { event_ref(native_ptr) })
    }
}

extern "C" {
    /// hwui's matrix helper (android/graphics/matrix.h): copies the Java
    /// `android.graphics.Matrix`'s nine values (row-major, SkMatrix layout)
    /// into `values`. Already `extern "C"`, so no cxx bridge is needed.
    fn AMatrix_getContents(
        env: *mut jni::sys::JNIEnv,
        matrix_obj: jobject,
        values: *mut f32,
    ) -> bool;
}
