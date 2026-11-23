package com.ylowstone.tv.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ylowstone.tv.i18n.AppStrings
import com.ylowstone.tv.model.ChannelItem
import com.ylowstone.tv.ui.theme.LocalYlowColors
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

private data class TrackOption(
    val label: String,
    val group: Tracks.Group?,
    val trackIndex: Int,
    val selected: Boolean,
    val isAuto: Boolean = false,
    val isDisable: Boolean = false
)

@OptIn(UnstableApi::class)
@Composable
fun FullscreenPlayerScreen(
    initialChannel: ChannelItem,
    channelList: List<ChannelItem>,
    userAgent: String,
    httpClient: OkHttpClient,
    showGestures: Boolean,
    strings: AppStrings,
    onChannelChanged: (ChannelItem) -> Unit,
    onBack: () -> Unit
) {
    val c = LocalYlowColors.current
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var currentIndex by remember {
        val idx = channelList.indexOfFirst { it.id == initialChannel.id && it.streamUrl == initialChannel.streamUrl }
        mutableIntStateOf(if (idx >= 0) idx else 0)
    }
    val currentChannel = channelList.getOrNull(currentIndex) ?: initialChannel

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var showChannelDrawer by remember { mutableStateOf(false) }
    var showTrackDrawer by remember { mutableStateOf(false) }
    var tracksVersion by remember { mutableIntStateOf(0) }

    val aspectModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Fill",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom",
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH to "16:9"
    )
    var aspectIndex by remember { mutableIntStateOf(0) }

    // Brightness & Volume gesture HUD state
    var brightnessLevel by remember { mutableFloatStateOf(0.5f) }
    var volumeLevel by remember {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        mutableFloatStateOf(cur.toFloat() / max.toFloat())
    }
    var activeHud by remember { mutableStateOf("") } // "BRIGHT" or "VOL" or ""

    val exoPlayer = remember {
        val dsFactory = OkHttpDataSource.Factory(httpClient)
            .setUserAgent(userAgent.ifBlank { "YlowstoneTV/1.0" })
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dsFactory))
            .build()
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onTracksChanged(tracks: Tracks) {
                tracksVersion++
            }
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(context, "Stream error: ${error.errorCodeName}", Toast.LENGTH_SHORT).show()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(currentChannel.streamUrl) {
        onChannelChanged(currentChannel)
        exoPlayer.setMediaItem(MediaItem.fromUri(currentChannel.streamUrl))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    // Auto-hide controls
    LaunchedEffect(controlsVisible, isScreenLocked, showChannelDrawer, showTrackDrawer) {
        if (controlsVisible && !showChannelDrawer && !showTrackDrawer) {
            delay(4500)
            controlsVisible = false
        }
    }

    LaunchedEffect(activeHud) {
        if (activeHud.isNotEmpty()) {
            delay(1100)
            activeHud = ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Native ExoPlayer View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    keepScreenOn = true
                    resizeMode = aspectModes[aspectIndex].first
                }
            },
            update = { view ->
                view.resizeMode = aspectModes[aspectIndex].first
            },
            modifier = Modifier.fillMaxSize()
        )

        // Tap & Optional Left/Right Gesture Layer
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) {
                    if (showChannelDrawer || showTrackDrawer) {
                        showChannelDrawer = false
                        showTrackDrawer = false
                    } else {
                        controlsVisible = !controlsVisible
                    }
                }
        ) {
            // Left half -> Brightness gesture
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (showGestures && !isScreenLocked) {
                            Modifier.pointerInput(Unit) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    brightnessLevel = (brightnessLevel - dragAmount / 500f).coerceIn(0.05f, 1f)
                                    activity?.window?.let { w ->
                                        val lp = w.attributes
                                        lp.screenBrightness = brightnessLevel
                                        w.attributes = lp
                                    }
                                    activeHud = "BRIGHT"
                                }
                            }
                        } else Modifier
                    )
            )
            // Right half -> Volume gesture
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (showGestures && !isScreenLocked) {
                            Modifier.pointerInput(Unit) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                    volumeLevel = (volumeLevel - dragAmount / 500f).coerceIn(0f, 1f)
                                    audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        (volumeLevel * maxVol).toInt(),
                                        0
                                    )
                                    activeHud = "VOL"
                                }
                            }
                        } else Modifier
                    )
            )
        }

        if (isBuffering) {
            CircularProgressIndicator(color = c.accent, modifier = Modifier.align(Alignment.Center))
        }

        // Gesture HUD Pill
        AnimatedVisibility(
            visible = activeHud.isNotEmpty() && showGestures,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isBright = activeHud == "BRIGHT"
                Icon(
                    if (isBright) Icons.Rounded.Brightness6 else Icons.Rounded.VolumeUp,
                    contentDescription = null,
                    tint = c.accent
                )
                val pct = ((if (isBright) brightnessLevel else volumeLevel) * 100).toInt()
                Text("$pct%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        // Screen Locked Minimal Overlay
        if (isScreenLocked && controlsVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .systemBarsPadding()
                    .padding(20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.dp, c.accent, RoundedCornerShape(16.dp))
                    .clickable { isScreenLocked = false }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                    Text(strings.tapToUnlock, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Full Controls Overlay (when unlocked)
        AnimatedVisibility(
            visible = controlsVisible && !isScreenLocked && !showChannelDrawer && !showTrackDrawer,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.72f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.80f)
                            )
                        )
                    )
                    .systemBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(38.dp).clip(CircleShape).background(c.accent)
                        ) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Column {
                            Text(currentChannel.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(currentChannel.category, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Open in External Player
                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(currentChannel.streamUrl), "video/*")
                                }
                                runCatching { context.startActivity(Intent.createChooser(intent, strings.externalPlayer)) }
                            },
                            modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f))
                        ) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = strings.externalPlayer, tint = Color.White, modifier = Modifier.size(19.dp))
                        }
                        // Lock Screen
                        IconButton(
                            onClick = { isScreenLocked = true; controlsVisible = true },
                            modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f))
                        ) {
                            Icon(Icons.Rounded.LockOpen, contentDescription = strings.screenLocked, tint = Color.White, modifier = Modifier.size(19.dp))
                        }
                        // Tracks Settings Drawer
                        IconButton(
                            onClick = { showTrackDrawer = true },
                            modifier = Modifier.size(38.dp).clip(CircleShape).background(c.accent)
                        ) {
                            Icon(Icons.Rounded.Settings, contentDescription = strings.tracks, tint = Color.White, modifier = Modifier.size(19.dp))
                        }
                    }
                }

                // Center Transport Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(34.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (channelList.size > 1) {
                                currentIndex = if (currentIndex > 0) currentIndex - 1 else channelList.lastIndex
                            }
                        },
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                    ) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(30.dp))
                    }

                    IconButton(
                        onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                        modifier = Modifier.size(64.dp).clip(CircleShape).background(c.accent)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "PlayPause",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (channelList.size > 1) {
                                currentIndex = (currentIndex + 1) % channelList.size
                            }
                        },
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                    ) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }

                // Bottom Bar Actions
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerBottomPill(Icons.Rounded.FormatListBulleted, strings.channelList) {
                        showChannelDrawer = true
                    }
                    PlayerBottomPill(Icons.Rounded.AspectRatio, "${strings.aspectRatio}: ${aspectModes[aspectIndex].second}") {
                        aspectIndex = (aspectIndex + 1) % aspectModes.size
                    }
                    PlayerBottomPill(Icons.Rounded.Tune, strings.tracks) {
                        showTrackDrawer = true
                    }
                }
            }
        }

        // Left Channel List Quick Drawer
        AnimatedVisibility(
            visible = showChannelDrawer,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Column(
                modifier = Modifier
                    .width(290.dp)
                    .fillMaxHeight()
                    .background(Color(0xEB101016))
                    .systemBarsPadding()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.channelList, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    IconButton(onClick = { showChannelDrawer = false }) {
                        Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.White)
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(channelList) { idx, ch ->
                        val active = idx == currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) c.accent.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable {
                                    currentIndex = idx
                                    showChannelDrawer = false
                                }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                ch.name,
                                color = if (active) c.accent else Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Right Track Selection Drawer (Video / Audio / Subtitles like Smarters Pro!)
        AnimatedVisibility(
            visible = showTrackDrawer,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            val tracks = remember(tracksVersion) { exoPlayer.currentTracks }
            Column(
                modifier = Modifier
                    .width(310.dp)
                    .fillMaxHeight()
                    .background(Color(0xEE101016))
                    .systemBarsPadding()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconButton(
                        onClick = { showTrackDrawer = false },
                        modifier = Modifier.size(34.dp).clip(CircleShape).background(c.accent)
                    ) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Text(strings.tracks, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.16f))

                TrackGroupSection(
                    title = strings.videoTrack,
                    icon = Icons.Rounded.Videocam,
                    trackType = C.TRACK_TYPE_VIDEO,
                    tracks = tracks,
                    player = exoPlayer,
                    strings = strings,
                    onChanged = { tracksVersion++ }
                )

                TrackGroupSection(
                    title = strings.audioTrack,
                    icon = Icons.Rounded.Audiotrack,
                    trackType = C.TRACK_TYPE_AUDIO,
                    tracks = tracks,
                    player = exoPlayer,
                    strings = strings,
                    onChanged = { tracksVersion++ }
                )

                TrackGroupSection(
                    title = strings.subtitleTrack,
                    icon = Icons.Rounded.Subtitles,
                    trackType = C.TRACK_TYPE_TEXT,
                    tracks = tracks,
                    player = exoPlayer,
                    strings = strings,
                    onChanged = { tracksVersion++ }
                )
            }
        }
    }
}

@Composable
private fun PlayerBottomPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun TrackGroupSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    trackType: Int,
    tracks: Tracks,
    player: ExoPlayer,
    strings: AppStrings,
    onChanged: () -> Unit
) {
    val c = LocalYlowColors.current
    val options = mutableListOf<TrackOption>()
    val disabled = player.trackSelectionParameters.disabledTrackTypes.contains(trackType)

    options.add(TrackOption(strings.disableTrack, null, -1, disabled, isDisable = true))
    options.add(
        TrackOption(
            strings.autoTrack,
            null,
            -1,
            !disabled && player.trackSelectionParameters.overrides.values.none { it.type == trackType },
            isAuto = true
        )
    )

    for (group in tracks.groups) {
        if (group.type != trackType) continue
        for (i in 0 until group.length) {
            val fmt = group.getTrackFormat(i)
            val label = when (trackType) {
                C.TRACK_TYPE_VIDEO -> {
                    val res = if (fmt.width > 0 && fmt.height > 0) "${fmt.width}x${fmt.height}" else "Video"
                    val codec = fmt.sampleMimeType?.substringAfter("/") ?: "video"
                    val br = if (fmt.bitrate > 0) " • ${fmt.bitrate / 1000} kb/s" else ""
                    "$res ($codec)$br"
                }
                C.TRACK_TYPE_AUDIO -> {
                    val lang = fmt.language ?: "und"
                    val codec = fmt.sampleMimeType?.substringAfter("/") ?: "audio"
                    val br = if (fmt.bitrate > 0) " • ${fmt.bitrate / 1000} kb/s" else ""
                    "$codec • $lang$br"
                }
                else -> {
                    fmt.label ?: fmt.language ?: "Subtitle ${i + 1}"
                }
            }
            options.add(TrackOption(label, group, i, !disabled && group.isTrackSelected(i)))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        if (options.size <= 2) {
            Text(strings.noTracksFound, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.padding(start = 26.dp))
        } else {
            options.forEach { opt ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(trackType)
                            when {
                                opt.isDisable -> builder.setTrackTypeDisabled(trackType, true)
                                opt.isAuto -> builder.setTrackTypeDisabled(trackType, false)
                                opt.group != null -> {
                                    builder.setTrackTypeDisabled(trackType, false)
                                    builder.addOverride(TrackSelectionOverride(opt.group.mediaTrackGroup, opt.trackIndex))
                                }
                            }
                            player.trackSelectionParameters = builder.build()
                            onChanged()
                        }
                        .padding(vertical = 4.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(
                        selected = opt.selected,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = c.accent, unselectedColor = Color.White.copy(alpha = 0.6f))
                    )
                    Text(opt.label, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
