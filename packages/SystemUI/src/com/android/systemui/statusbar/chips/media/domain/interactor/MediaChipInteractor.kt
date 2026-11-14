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

package com.android.systemui.statusbar.chips.media.domain.interactor

import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.media.NotificationMediaManager.isConnectingState
import com.android.systemui.media.NotificationMediaManager.isPlayingState
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.controls.util.MediaControllerFactory
import com.android.systemui.media.remedia.data.repository.MediaPipelineRepository
import com.android.systemui.media.remedia.shared.flag.MediaControlsInComposeFlag
import com.android.systemui.statusbar.quickactions.media.domain.interactor.MediaControlChipInteractor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The active media for the status bar chip.
 *
 * @property data the first active media entry, which picks the session and gives the app name,
 *   icons and the fallback values below.
 * @property controller a controller for the session in [data], or null until it is connected.
 * @property metadata the latest session metadata, which can be newer than [data].
 * @property playbackState the latest session playback state, which can be newer than [data].
 */
data class MediaChipSessionModel(
    val data: MediaData,
    val controller: MediaController? = null,
    val metadata: MediaMetadata? = null,
    val playbackState: PlaybackState? = null,
) {
    val title: String?
        get() =
            metadata?.text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: metadata?.text(MediaMetadata.METADATA_KEY_TITLE)
                ?: data.song?.toString()

    val artist: String?
        get() = metadata?.text(MediaMetadata.METADATA_KEY_ARTIST) ?: data.artist?.toString()

    val artworkBitmap: Bitmap?
        get() =
            metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)

    val durationMs: Long
        get() = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

    /** True while playing or buffering, so that skipping to another song doesn't read as paused. */
    val isPlaying: Boolean?
        get() =
            playbackState?.let { isPlayingState(it.state) || isConnectingState(it.state) }
                ?: data.isPlaying
}

private fun MediaMetadata.text(key: String): String? =
    getText(key)?.toString()?.takeIf { it.isNotBlank() }

/**
 * Approximates the order of the media carousel: playing before paused, active sessions before
 * resumable ones, then the most recently active first.
 */
private val carouselOrder: Comparator<MediaData> =
    compareBy<MediaData> { it.isPlaying != true }
        .thenBy { it.resumption }
        .thenByDescending { it.lastActive }

/**
 * Follows the media session behind the first active media entry: the first player in the media
 * carousel with the legacy media UI, or the first active entry of the media pipeline when
 * [MediaControlsInComposeFlag] is enabled, since the legacy carousel is not running then.
 *
 * The carousel only learns about a new song or play state once the app's media notification has
 * gone through the notification pipeline and its artwork has loaded, so the title and playback
 * state are read from the session callbacks instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SysUISingleton
class MediaChipInteractor
@Inject
constructor(
    @Application scope: CoroutineScope,
    @Background private val bgDispatcher: CoroutineDispatcher,
    @Background private val bgHandler: Handler,
    private val mediaControllerFactory: MediaControllerFactory,
    mediaControlChipInteractor: MediaControlChipInteractor,
    mediaPipelineRepository: MediaPipelineRepository,
) {
    private data class SessionState(
        val token: MediaSession.Token,
        val controller: MediaController,
        val metadata: MediaMetadata?,
        val playbackState: PlaybackState?,
    )

    private val activeMediaData: Flow<MediaData?> =
        if (MediaControlsInComposeFlag.isEnabled) {
            // The legacy carousel doesn't run with media controls in compose, so pick the active
            // entry from the pipeline that feeds the new media UI.
            mediaPipelineRepository.currentUserEntries.map { entries ->
                entries.values.filter { it.active }.minWithOrNull(carouselOrder)
            }
        } else {
            mediaControlChipInteractor.activeMediaDataLegacy
        }

    private val sessionState: Flow<SessionState?> =
        activeMediaData
            .map { it?.token }
            .distinctUntilChanged()
            .flatMapLatest { token -> if (token == null) flowOf(null) else sessionUpdates(token) }

    /** The active media, or null if there is none. */
    val session: StateFlow<MediaChipSessionModel?> =
        combine(activeMediaData, sessionState) { data, state ->
            data?.let {
                val current = state?.takeIf { it.token == data.token }
                MediaChipSessionModel(
                    data = data,
                    controller = current?.controller,
                    metadata = current?.metadata,
                    playbackState = current?.playbackState,
                )
            }
        }
            .stateIn(scope, SharingStarted.WhileSubscribed(), null)

    private fun sessionUpdates(token: MediaSession.Token): Flow<SessionState?> =
        callbackFlow {
            val controller = mediaControllerFactory.create(token)
            // Only touched on the bgHandler thread, which delivers the callbacks too.
            var state: SessionState? = null
            fun update(newState: SessionState?) {
                state = newState
                trySend(newState)
            }
            val callback =
                object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        state?.let { update(it.copy(metadata = metadata)) }
                    }

                    override fun onPlaybackStateChanged(playbackState: PlaybackState?) {
                        state?.let { update(it.copy(playbackState = playbackState)) }
                    }

                    override fun onSessionDestroyed() = update(null)
                }
            controller.registerCallback(callback, bgHandler)
            bgHandler.post {
                update(
                    SessionState(
                        token = token,
                        controller = controller,
                        metadata = controller.metadata,
                        playbackState = controller.playbackState,
                    )
                )
            }
            awaitClose { controller.unregisterCallback(callback) }
        }
            .flowOn(bgDispatcher)
}
