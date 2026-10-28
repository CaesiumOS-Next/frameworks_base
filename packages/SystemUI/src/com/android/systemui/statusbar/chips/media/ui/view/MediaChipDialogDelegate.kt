/*
 * Copyright (C) 2026 Langsdorff
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

package com.android.systemui.statusbar.chips.media.ui.view

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.WindowInsets
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.statusbar.chips.media.ui.compose.MediaChipPopup
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import com.android.systemui.statusbar.quickactions.media.domain.interactor.MediaControlChipInteractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher

/** A compact media controls popup shown under the status bar when the media chip is tapped. */
class MediaChipDialogDelegate
@AssistedInject
constructor(
    @Assisted private val context: Context,
    private val sysuiDialogFactory: SystemUIDialogFactory,
    private val mediaControlChipInteractor: MediaControlChipInteractor,
    private val activityStarter: ActivityStarter,
    @Background private val bgDispatcher: CoroutineDispatcher,
) : SystemUIDialog.Delegate {

    override fun createDialog(): SystemUIDialog {
        val dialog =
            sysuiDialogFactory.create(context = context, dialogDelegate = this) { dialog ->
                MediaChipPopup(
                    mediaData = mediaControlChipInteractor.activeMediaDataLegacy,
                    bgDispatcher = bgDispatcher,
                    onOpenApp = { intent ->
                        dialog.dismiss()
                        activityStarter.postStartActivityDismissingKeyguard(intent)
                    },
                    onDismiss = { dialog.dismiss() },
                )
            }
        dialog.setCanceledOnTouchOutside(true)
        return dialog
    }

    override fun onCreate(dialog: SystemUIDialog, savedInstanceState: Bundle?) {
        // SystemUIDialog stops fitting the status bar, which would put the popup over the chip.
        dialog.window?.apply {
            attributes.fitInsetsTypes = attributes.fitInsetsTypes or WindowInsets.Type.statusBars()
            attributes.receiveInsetsIgnoringZOrder = true
            setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(context: Context): MediaChipDialogDelegate
    }
}
