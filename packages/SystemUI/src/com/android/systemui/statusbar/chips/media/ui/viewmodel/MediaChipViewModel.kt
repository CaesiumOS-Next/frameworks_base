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

package com.android.systemui.statusbar.chips.media.ui.viewmodel

import android.content.Context
import androidx.compose.ui.graphics.RectangleShape
import com.android.internal.jank.Cuj
import com.android.internal.util.ContrastColorUtil
import com.android.systemui.animation.DialogCuj
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.log.LogBuffer
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.res.R
import com.android.systemui.statusbar.chips.StatusBarChipLogTags.pad
import com.android.systemui.statusbar.chips.StatusBarChipsLog
import com.android.systemui.statusbar.chips.media.ui.view.MediaChipDialogDelegate
import com.android.systemui.statusbar.chips.ui.model.ColorsModel
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipViewModel
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipViewModel.Companion.createDialogLaunchOnClickCallback
import com.android.systemui.statusbar.chips.uievents.StatusBarChipsUiEventLogger
import com.android.systemui.statusbar.quickactions.media.domain.interactor.MediaControlChipInteractor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** View model for the media chip shown in the status bar while a media session is active. */
@SysUISingleton
class MediaChipViewModel
@Inject
constructor(
    @Main private val context: Context,
    @Application private val scope: CoroutineScope,
    mediaControlChipInteractor: MediaControlChipInteractor,
    private val dialogDelegateFactory: MediaChipDialogDelegate.Factory,
    private val dialogTransitionAnimator: DialogTransitionAnimator,
    @StatusBarChipsLog private val logger: LogBuffer,
    private val uiEventLogger: StatusBarChipsUiEventLogger,
) : OngoingActivityChipViewModel {
    private val instanceId = uiEventLogger.createNewInstanceId()

    private data class MediaChipState(
        val packageName: String,
        val appName: String?,
        val appIcon: android.graphics.drawable.Icon?,
        val song: String?,
        val isPaused: Boolean,
    )

    override val chip: StateFlow<OngoingActivityChipModel> =
        mediaControlChipInteractor.activeMediaDataLegacy
            .map { it?.toChipState() }
            .distinctUntilChanged()
            .map { it?.toChipModel() ?: OngoingActivityChipModel.Inactive() }
            // See b/347726238 for [SharingStarted.Lazily] reasoning.
            .stateIn(scope, SharingStarted.Lazily, OngoingActivityChipModel.Inactive())

    private fun MediaData.toChipState() =
        MediaChipState(
            packageName = packageName,
            appName = app,
            appIcon = appIcon,
            song = song?.toString(),
            isPaused = isPlaying == false,
        )

    private fun MediaChipState.toChipModel(): OngoingActivityChipModel.Active {
        val contentDescription = appName?.let { ContentDescription.Loaded(it) }
        // Full color app icons don't read well once tinted, so fall back to a plain music note.
        val drawable =
            appIcon?.loadDrawable(context)?.takeIf {
                ContrastColorUtil.getInstance(context).isGrayscaleIcon(it)
            }
        val icon =
            if (drawable != null) {
                Icon.Loaded(drawable, contentDescription)
            } else {
                Icon.Resource(R.drawable.ic_music_note, contentDescription)
            }
        return OngoingActivityChipModel.Active(
            key = KEY,
            managingPackageName = packageName,
            icon = OngoingActivityChipModel.ChipIcon.SingleColorIcon(icon),
            content =
                if (isPaused || song.isNullOrBlank()) {
                    OngoingActivityChipModel.Content.IconOnly
                } else {
                    OngoingActivityChipModel.Content.Text(song, clipAtTimerWidth = true)
                },
            colors = if (isPaused) ColorsModel.SystemThemed else ColorsModel.AccentThemed,
            decorativeIcon =
                if (isPaused) {
                    OngoingActivityChipModel.DecorativeIcon(
                        icon = Icon.Resource(R.drawable.ic_media_chip_paused, null),
                        backgroundShape = RectangleShape,
                        colors = ColorsModel.SystemThemed,
                    )
                } else {
                    null
                },
            clickBehavior =
                OngoingActivityChipModel.ClickBehavior.ExpandAction(
                    createDialogLaunchOnClickCallback(
                        dialogDelegateCreator = { context -> dialogDelegateFactory.create(context) },
                        dialogTransitionAnimator = dialogTransitionAnimator,
                        DIALOG_CUJ,
                        key = KEY,
                        instanceId = instanceId,
                        uiEventLogger = uiEventLogger,
                        logger = logger,
                        tag = TAG,
                    )
                ),
            instanceId = instanceId,
        )
    }

    companion object {
        const val KEY = "Media"
        private val DIALOG_CUJ =
            DialogCuj(Cuj.CUJ_STATUS_BAR_LAUNCH_DIALOG_FROM_CHIP, tag = "Media")
        private val TAG = "MediaChipVM".pad()
    }
}
