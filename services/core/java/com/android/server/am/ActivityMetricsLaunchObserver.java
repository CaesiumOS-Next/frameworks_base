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

package com.android.server.am;

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Observe activity manager launch sequences.
 *
 * The activity manager can have at most one launch sequence
 * outstanding at a time (globally).
 *
 * Initially, the launch observer in the {@code *} (idle) state (there is *no* launch sequence).
 * Upon the {@link #onLaunchBegan} callback, it transitions into the {@code BEGAN}
 * state (i.e. there is exactly one launch sequence).
 *
 * It must then transition to either {@code CANCELLED} with {@link #onLaunchCanceled}
 * or into {@code OCCURRED} with {@link #onLaunchOccurred}.
 *
 * The {@code initialActivity} is guaranteed to be the same value whilst within
 * the non-{@code *} states.
 *
 * Upon reaching a terminal state, the launch observer can be considered to automatically transition
 * back to the {@code *} state.
 *
 * Multiple concurrent launch sequences are not allowed.
 *
 * <pre>
 *          ┌··························┐
 *          ▼                          :
 *                  ┌──────────┐     ╔═══════════╗
 *    ··▶   ★   ──▶ │  BEGAN   │ ──▶ ║ CANCELLED ║
 *                  └──────────┘     ╚═══════════╝
 *          ▲         │
 *          :         │
 *          :         ▼
 *          :       ╔══════════╗
 *          └······ ║ OCCURRED ║
 *                  ╚══════════╝
 * </pre>
 *
 * @hide
 */
public abstract class ActivityMetricsLaunchObserver {
    /**
     * The 'temperature' at which a launch sequence had started.
     *
     * The lower the temperature the more work has to be done during start-up.
     * A 'cold' temperature means that a new process has been started and likely
     * nothing is cached.
     *
     * A hot temperature means the existing activity is brought to the foreground.
     * It may need to regenerate some objects as a result of {@code onTrimMemory}.
     *
     * A warm temperature is in the middle; an existing process is used, but the activity
     * has to be created from scratch with {@code #onCreate}.
     *
     * @see https://developer.android.com/topic/performance/vitals/launch-time
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            TEMPERATURE_COLD,
            TEMPERATURE_WARM,
            TEMPERATURE_HOT
    })
    @interface Temperature {}

    /** Cold launch sequence: a new process has started. */
    public static final int TEMPERATURE_COLD = 1;
    /** Warm launch sequence: process reused, but activity has to be created. */
    public static final int TEMPERATURE_WARM = 2;
    /** Hot launch sequence: process reused, activity brought-to-top. */
    public static final int TEMPERATURE_HOT = 3;

    /**
     * Notifies the observer that a new launch sequence has begun.
     *
     * Once a launch sequence begins, it must terminate with either a cancellation or
     * an occurrence. New launch sequences will not begin until the existing one terminates.
     *
     * The initial activity is the first activity to be started as part of a launch sequence:
     * it can be matched by {@code initialActivity} in the other callbacks. However, it isn't
     * necessarily the activity which will be considered as displayed when the activity
     * finishes launching (e.g. {@code finalActivity} in {@link #onLaunchOccurred}).
     */
    public abstract void onLaunchBegan(ActivityRecord initialActivity,
            @Temperature int temperature);

    /**
     * Notifies the observer that the current launch sequence has been aborted.
     *
     * This can happen for many reasons, for example the user switches away to another app
     * prior to the launch sequence completing, or the application being killed.
     *
     * Canceling an activity terminates the current launch sequence.
     *
     * @param initialActivity the activity in {@link #onLaunchBegan}
     * @param abortingActivity the last activity that had the top-most window during abort
     *                         (this can be {@code null} in rare situations its unknown)
     */
    public abstract void onLaunchCanceled(ActivityRecord initialActivity,
            ActivityRecord abortingActivity);

    /**
     * Notifies the observer that the current launch sequence has been successfully finished.
     *
     * Since a launch sequence can have multiple trampolining activities that are coalesced
     * into a single launch sequence, the final activity isn't necessarily the initial activity.
     *
     * Finishing an activity terminates the current launch sequence.
     *
     * @param initialActivity the activity in {@link #onLaunchBegan}
     * @param finalActivity the top-most activity whose windows were first to fully draw
     */
    public abstract void onLaunchOccurred(ActivityRecord initialActivity,
            ActivityRecord finalActivity);
}
