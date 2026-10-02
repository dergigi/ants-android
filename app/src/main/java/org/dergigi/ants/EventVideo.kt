package org.dergigi.ants

import android.net.Uri
import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage

internal data class VideoAttachment(val url: String, val poster: String? = null)
private val videoExtensions = setOf("mp4", "m4v", "mov", "webm", "mkv", "3gp", "3gpp", "ts", "m2ts", "mpg", "mpeg", "flv")
internal fun eventVideos(event: Nip01Event): List<VideoAttachment> {
    fun secure(url: String) = runCatching { val uri = Uri.parse(url); uri.scheme == "https" && !uri.host.isNullOrBlank() }.getOrDefault(false)
    fun videoUrl(url: String) = Uri.parse(url).path.orEmpty().substringAfterLast('.').lowercase() in videoExtensions
    val attachments = mutableListOf<VideoAttachment>()
    event.tags.filter { it.firstOrNull() == "imeta" }.forEach { tag ->
        fun field(name: String) = tag.drop(1).firstOrNull { it.startsWith("$name ") }?.substringAfter(' ')
        val url = field("url") ?: return@forEach
        if (secure(url) && (field("m")?.startsWith("video/") == true || videoUrl(url))) {
            attachments += VideoAttachment(url, field("image")?.takeIf(::secure))
        }
    }
    event.tags.filter { it.firstOrNull() == "url" }.mapNotNull { it.getOrNull(1) }.forEach { url ->
        if (secure(url) && (event.kind in listOf(21, 22) || event.tagValue("m")?.startsWith("video/") == true || videoUrl(url))) {
            attachments += VideoAttachment(url, event.tagValue("image")?.takeIf(::secure))
        }
    }
    webLinks(event.content).map { it.text }.filter { secure(it) && videoUrl(it) }.forEach { attachments += VideoAttachment(it) }
    return attachments.distinctBy { it.url }.take(100)
}

// One audible video at a time, including when a result also appears in details.
private object VideoPlayback {
    private var current: ExoPlayer? = null
    fun claim(player: ExoPlayer) { if (current !== player) current?.pause(); current = player }
    fun forget(player: ExoPlayer) { if (current === player) current = null }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun EventVideo(video: VideoAttachment) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var player by remember(video.url) { mutableStateOf<ExoPlayer?>(null) }
    var failed by remember(video.url) { mutableStateOf(false) }
    var playing by remember(video.url) { mutableStateOf(false) }
    var ratio by remember(video.url) { mutableFloatStateOf(16f / 9f) }
    DisposableEffect(player, lifecycle) {
        val active = player
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                if (isPlaying && active != null) VideoPlayback.claim(active)
            }
            override fun onPlayerError(error: PlaybackException) { failed = true }
            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) ratio = (size.width * size.pixelWidthHeightRatio / size.height).coerceIn(9f / 16f, 2.4f)
            }
        }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) active?.pause() }
        active?.addListener(listener)
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            active?.removeListener(listener)
            if (active != null) { VideoPlayback.forget(active); active.release() }
        }
    }
    Box(Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(6.dp)).background(Color.Black)
        .onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            if (bounds.height <= 0 || bounds.bottom <= 0 || bounds.top >= view.height) player?.pause()
        }, contentAlignment = Alignment.Center) {
        val active = player
        if (active == null) {
            video.poster?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            IconButton(onClick = {
                player = ExoPlayer.Builder(context.applicationContext).build().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
                    setHandleAudioBecomingNoisy(true)
                    setMediaItem(MediaItem.fromUri(video.url))
                    prepare()
                    play()
                }
            }, modifier = Modifier.size(64.dp)) { Icon(Icons.Outlined.PlayCircleOutline, "Play video", Modifier.size(48.dp), tint = Color.White) }
        } else {
            AndroidView(factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.inline_video, null) as PlayerView).apply {
                    player = active
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                }
            }, update = { it.player = active; it.keepScreenOn = playing }, onRelease = { it.player = null; it.keepScreenOn = false }, modifier = Modifier.fillMaxSize())
        }
        if (failed) {
            Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.85f)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Video unavailable or unsupported", color = Color.White, style = MaterialTheme.typography.bodySmall)
                Row {
                    ActionIcon(Icons.Outlined.Refresh, "Retry video", { failed = false; player?.prepare(); player?.play() })
                    ActionIcon(Icons.Outlined.OpenInBrowser, "Open video in browser", { openUrl(context, video.url) })
                }
            }
        } else {
            IconButton(onClick = { player?.pause(); openUrl(context, video.url) }, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Outlined.OpenInBrowser, "Open video in browser", tint = Color.White)
            }
        }
    }
}
