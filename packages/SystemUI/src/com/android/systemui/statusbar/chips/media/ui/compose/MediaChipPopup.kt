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

package com.android.systemui.statusbar.chips.media.ui.compose

import android.app.PendingIntent
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.media.controls.shared.model.MediaAction
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.res.R
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** Compact media controls: artwork, title, seek bar and previous / play-pause / next buttons. */
@Composable
fun MediaChipPopup(
    mediaData: StateFlow<MediaData?>,
    bgDispatcher: CoroutineDispatcher,
    onOpenApp: (PendingIntent) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val data by mediaData.collectAsStateWithLifecycle()
    val current = data
    if (current == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, top = 20.dp, end = 20.dp),
    ) {
        Header(current, bgDispatcher, onOpenApp)
        SeekBar(current, bgDispatcher)
        Controls(current)
    }
}

@Composable
private fun Header(
    data: MediaData,
    bgDispatcher: CoroutineDispatcher,
    onOpenApp: (PendingIntent) -> Unit,
) {
    val context = LocalContext.current
    val artwork by
        produceState<Drawable?>(initialValue = null, data.artwork) {
            value = withContext(bgDispatcher) { data.artwork?.loadDrawable(context) }
        }
    val clickIntent = data.clickIntent

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (clickIntent != null) {
                        Modifier.clickable { onOpenApp(clickIntent) }
                    } else {
                        Modifier
                    }
                ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier.size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            val art = artwork
            if (art != null) {
                Image(
                    painter = rememberDrawablePainter(art),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_music_note),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            data.app?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = data.song?.toString().orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            data.artist?.let {
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private data class PlaybackPosition(val positionMs: Long, val durationMs: Long, val canSeek: Boolean)

@Composable
private fun SeekBar(data: MediaData, bgDispatcher: CoroutineDispatcher) {
    val context = LocalContext.current
    val controller = remember(data.token) { data.token?.let { MediaController(context, it) } }
    var position by remember(controller) { mutableStateOf<PlaybackPosition?>(null) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(controller) {
        if (controller == null) return@LaunchedEffect
        while (true) {
            position = withContext(bgDispatcher) { controller.currentPosition() }
            delay(POSITION_POLL_INTERVAL_MS)
        }
    }

    val current = position ?: return
    val fraction = dragFraction ?: (current.positionMs.toFloat() / current.durationMs)
    val elapsedText = formatTime((fraction * current.durationMs).toLong())
    val totalText = formatTime(current.durationMs)
    val description =
        stringResource(R.string.controls_media_seekbar_description, elapsedText, totalText)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TimeText(elapsedText)
        Slider(
            value = fraction.coerceIn(0f, 1f),
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let {
                    val target = (it * current.durationMs).toLong()
                    controller?.transportControls?.seekTo(target)
                    position = current.copy(positionMs = target)
                }
                dragFraction = null
            },
            enabled = current.canSeek,
            modifier = Modifier.weight(1f).semantics { stateDescription = description },
        )
        TimeText(totalText)
    }
}

@Composable
private fun TimeText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.widthIn(min = 36.dp),
    )
}

@Composable
private fun Controls(data: MediaData) {
    val actions = data.semanticActions ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        actions.prevOrCustom?.let { ActionButton(it) }
        actions.playOrPause?.let { action ->
            Surface(
                onClick = { action.action?.run() },
                enabled = action.action != null,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(width = 64.dp, height = 48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) { ActionIcon(action) }
            }
        }
        actions.nextOrCustom?.let { ActionButton(it) }
    }
}

@Composable
private fun ActionButton(action: MediaAction) {
    IconButton(
        onClick = { action.action?.run() },
        enabled = action.action != null,
        modifier = Modifier.size(48.dp),
    ) {
        ActionIcon(action)
    }
}

@Composable
private fun ActionIcon(action: MediaAction) {
    val icon = action.icon ?: return
    // The media card draws the same drawable, so use a separate copy to keep its callback intact.
    val copy = remember(icon) { icon.constantState?.newDrawable()?.mutate() ?: icon }
    Icon(
        painter = rememberDrawablePainter(copy),
        contentDescription = action.contentDescription?.toString(),
        modifier = Modifier.size(24.dp),
    )
}

private fun MediaController.currentPosition(): PlaybackPosition? {
    val state = playbackState ?: return null
    val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
    if (duration <= 0L) return null
    var position = state.position
    if (state.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0L) {
        position +=
            (state.playbackSpeed * (SystemClock.elapsedRealtime() - state.lastPositionUpdateTime))
                .toLong()
    }
    return PlaybackPosition(
        positionMs = position.coerceIn(0L, duration),
        durationMs = duration,
        canSeek = (state.actions and PlaybackState.ACTION_SEEK_TO) != 0L,
    )
}

private fun formatTime(millis: Long): String = DateUtils.formatElapsedTime(millis / 1000)

private const val POSITION_POLL_INTERVAL_MS = 1000L
