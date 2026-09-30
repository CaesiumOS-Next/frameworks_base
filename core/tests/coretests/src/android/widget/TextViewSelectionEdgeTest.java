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

package android.widget;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.RootMatchers.withDecorView;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.textclassifier.TextClassifier;

import androidx.test.espresso.action.GeneralSwipeAction;
import androidx.test.espresso.action.Press;
import androidx.test.espresso.action.MotionEvents;
import androidx.test.espresso.action.Swiper;
import java.util.ArrayList;
import java.util.List;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.frameworks.coretests.R;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Regression tests for selection-handle dragging in an offset, scrolling TextView. */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class TextViewSelectionEdgeTest {
    private static final String LONG_TEXT = "abcdefghijklmnopqrstuvwxabc";

    @Rule
    public final ActivityTestRule<TextViewActivity> mActivityRule =
            new ActivityTestRule<>(TextViewActivity.class);

    @Test
    public void startHandle_singleLineOverflow_movesInward() throws Throwable {
        checkInwardDrag(false, false, true);
    }

    @Test
    public void startHandle_singleLineOverflow_offsetView_movesInward() throws Throwable {
        checkInwardDrag(true, false, true);
    }

    @Test
    public void startHandle_shortText_offsetView_movesInward() throws Throwable {
        checkInwardDrag(true, true, true);
    }

    @Test
    public void endHandle_singleLineOverflow_offsetView_movesInward() throws Throwable {
        checkInwardDrag(true, false, false);
    }

    private void checkInwardDrag(boolean offsetView, boolean shortText, boolean startHandle)
            throws Throwable {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final EditText editText = mActivityRule.getActivity().findViewById(R.id.textview);
        final String text = shortText ? "abcde" : LONG_TEXT;
        mActivityRule.runOnUiThread(() -> {
            final View parent = (View) editText.getParent();
            final int width = Math.min(492, parent.getWidth() / 2 - 16);
            assertTrue("The test needs a laid-out Activity", width > 96);
            final ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) editText.getLayoutParams();
            lp.width = width;
            lp.height = Math.max(140, (int) (80 * editText.getResources()
                    .getDisplayMetrics().density));
            lp.leftMargin = offsetView ? width : 16;
            lp.topMargin = 96;
            editText.setLayoutParams(lp);
            editText.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editText.setSingleLine(true);
            editText.setTextDirection(View.TEXT_DIRECTION_LTR);
            editText.setGravity(Gravity.TOP | Gravity.LEFT);
            editText.setTextClassifier(TextClassifier.NO_OP);
            editText.setShowSoftInputOnFocus(false);
            editText.setPadding(8, 16, 8, 16);
            editText.setTextSize(34);
            // Slight natural overflow keeps both selection endpoints visible. Do not force
            // scrollX: exercise the same layout/scroll behavior as an ordinary single-line app.
            editText.setTextScaleX(1);
            if (!shortText) {
                editText.setTextScaleX((width - 16 + 4) / editText.getPaint().measureText(text));
            }
            editText.setText(text);
            editText.requestFocus();
            editText.setSelection(text.length());
        });
        instrumentation.waitForIdleSync();
        onView(withId(R.id.textview)).perform(longClick());
        mActivityRule.runOnUiThread(() ->
                editText.onTextContextMenuItem(android.R.id.selectAll));
        instrumentation.waitForIdleSync();

        final int[] location = new int[2];
        mActivityRule.runOnUiThread(() -> {
            editText.getLocationOnScreen(location);
            final android.os.Bundle debug = new android.os.Bundle();
            debug.putString("stream", "PRECONDITION x=" + location[0] + " scrollX="
                    + editText.getScrollX() + " width=" + editText.getWidth() + " line="
                    + editText.getLayout().getLineWidth(0) + " selection="
                    + editText.getSelectionStart() + "," + editText.getSelectionEnd() + "\n");
            instrumentation.sendStatus(2, debug);
            assertEquals(0, editText.getSelectionStart());
            assertEquals(text.length(), editText.getSelectionEnd());
            if (shortText) {
                assertEquals(0, editText.getScrollX());
            } else {
                assertTrue("Natural overflow must enter the horizontal-scroll path",
                        editText.getScrollX() != 0);
            }
            Log.i("SelectionEdgeTest", "before x=" + location[0] + " scrollX="
                    + editText.getScrollX() + " width=" + editText.getWidth());
        });

        // Look up the framework handle ID so this same behavioral test can also be built
        // as a standalone test APK against the public SDK.
        final int handleId = editText.getResources().getIdentifier(
                startHandle ? "selection_start_handle" : "selection_end_handle", "id", "android");
        assertTrue("Selection handle resource must exist", handleId != 0);
        final float delta = (shortText ? editText.getLayout().getLineWidth(0) / 2f
                : editText.getWidth() / 3f)
                * (startHandle ? 1 : -1);
        onView(withId(handleId))
                .inRoot(allOf(isPlatformPopup(), withDecorView(hasDescendant(withId(handleId)))))
                .check(matches(isDisplayed()))
                .perform(new GeneralSwipeAction(TOUCH_DRAG,
                        view -> handlePoint(view, 0),
                        view -> handlePoint(view, delta), Press.FINGER));
        mActivityRule.runOnUiThread(() -> {
            Log.i("SelectionEdgeTest", "after selection=" + editText.getSelectionStart()
                    + "," + editText.getSelectionEnd());
            final android.os.Bundle result = new android.os.Bundle();
            result.putString("stream", "RESULT selection=" + editText.getSelectionStart()
                    + "," + editText.getSelectionEnd() + " scrollX=" + editText.getScrollX()
                    + " delta=" + delta + "\n");
            instrumentation.sendStatus(2, result);
            if (startHandle) {
                assertTrue("Dragging inward must advance the start offset",
                        editText.getSelectionStart() > 0);
                assertEquals(text.length(), editText.getSelectionEnd());
            } else {
                assertEquals(0, editText.getSelectionStart());
                assertTrue("Dragging inward must reduce the end offset",
                        editText.getSelectionEnd() < text.length());
            }
            assertTrue(editText.getSelectionStart() < editText.getSelectionEnd());
        });
    }

    private static final Swiper TOUCH_DRAG = (controller, start, end, precision) -> {
        final List<MotionEvent> events = new ArrayList<>();
        final MotionEvent down = MotionEvents.obtainDownEvent(
                start, precision, InputDevice.SOURCE_TOUCHSCREEN, 0);
        events.add(down);
        // Preserve a real touchscreen source and intermediate MOVE events. A sparse
        // generic swipe can follow different selection/scroll transitions.
        for (int step = 1; step <= 100; step++) {
            final float[] point = {start[0] + (end[0] - start[0]) * step / 100f,
                    start[1] + (end[1] - start[1]) * step / 100f};
            events.add(MotionEvents.obtainMovement(down, down.getDownTime() + step * 50, point));
        }
        events.add(MotionEvents.obtainUpEvent(down, down.getDownTime() + 5050, end));
        try {
            controller.injectMotionEventSequence(events);
            return Swiper.Status.SUCCESS;
        } catch (Exception e) {
            throw new AssertionError("Touchscreen drag injection failed", e);
        } finally {
            for (MotionEvent event : events) {
                event.recycle();
            }
        }
    };

    private static float[] handlePoint(View view, float delta) {
        final int[] location = new int[2];
        view.getLocationOnScreen(location);
        return new float[] {location[0] + view.getWidth() / 2f + delta,
                location[1] + Math.min(12, view.getHeight() / 2f)};
    }
}
