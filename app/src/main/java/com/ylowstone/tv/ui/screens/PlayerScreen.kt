package com.ylowstone.tv.ui.screens

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ylowstone.tv.data.ActiveSubtitleSession
import com.ylowstone.tv.data.IptvRepository
import com.ylowstone.tv.data.SrtCue
import com.ylowstone.tv.data.SubtitleCandidate
import com.ylowstone.tv.data.SubtitleFetcher
import com.ylowstone.tv.i18n.AppStrings
import com.ylowstone.tv.model.ChannelItem
import com.ylowstone.tv.model.MediaType
import com.ylowstone.tv.ui.theme.LocalYlowColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private data class TrackOption(
    val label: String,
    val group: Tracks.Group?,
    val trackIndex: Int,
    val selected: Boolean,
    val isAuto: Boolean = false,
    val isDisable: Boolean = false
)

private fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0L) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

@OptIn(UnstableApi::class)
@Composable
fun PipVideoSurface(exoPlayer: ExoPlayer, isPlaying: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clip(RoundedCornerShape(18.dp))
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    keepScreenOn = isPlaying
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = {
                it.player = exoPlayer
                it.keepScreenOn = isPlaying
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Pure YouTube PiP-style Floating Mini-Player:
 * - Idle: 100% clean video inside rounded corners (ZERO buttons visible)
 * - Single tap: reveals Prev, Play/Pause, Next, Fullscreen, System PiP, and Close (X)
 */
@OptIn(UnstableApi::class)
@Composable
fun DraggableFloatingPlayer(
    exoPlayer: ExoPlayer,
    channel: ChannelItem,
    isPlaying: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onExpandFullscreen: () -> Unit,
    onEnterSystemPip: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalYlowColors.current
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var controlsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(3200)
            controlsVisible = false
        }
    }

    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .width(232.dp)
            .height(132.dp)
            .shadow(20.dp, shape, ambientColor = Color.Black.copy(alpha = 0.7f))
            .clip(shape)
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX = (offsetX + dragAmount.x).coerceIn(-680f, 680f)
                    offsetY = (offsetY + dragAmount.y).coerceIn(-1300f, 320f)
                }
            }
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                controlsVisible = !controlsVisible
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    keepScreenOn = isPlaying
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = {
                it.player = exoPlayer
                it.keepScreenOn = isPlaying
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.50f))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.62f))
                            .clickable { controlsVisible = false; onExpandFullscreen() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Fullscreen, contentDescription = "Fullscreen", tint = Color.White, modifier = Modifier.size(17.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.62f))
                                .clickable { controlsVisible = false; onEnterSystemPip() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White, modifier = Modifier.size(15.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.62f))
                                .clickable(onClick = onClose),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable(onClick = onPrev),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(21.dp))
                    }

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(c.accent)
                            .clickable { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "PlayPause",
                            tint = c.onAccent,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable(onClick = onNext),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(21.dp))
                    }
                }

                Text(
                    text = channel.name,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 4.dp)
                )
            }
        }
    }
}

/**
 * Custom Drag-Only Seekbar (Anti-Accidental-Tap!):
 * - Tapping anywhere on the track does NOTHING so accidental touches never jump the movie!
 * - Responds strictly to intentional horizontal dragging (detectHorizontalDragGestures).
 * - Seeks ExoPlayer smoothly on drag release.
 */
@Composable
private fun DragOnlySeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeekCommitted: (Long) -> Unit,
    onDraggingStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalYlowColors.current
    val safeDuration = durationMs.coerceAtLeast(1L)
    var barWidthPx by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val displayFraction = if (isDragging) {
        dragFraction
    } else {
        (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    }
    val displayPosMs = if (isDragging) (dragFraction * safeDuration).toLong() else positionMs

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDurationMs(displayPosMs),
                color = if (isDragging) c.accent else Color.White.copy(alpha = 0.92f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = formatDurationMs(durationMs),
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Pure Drag-Only Touch Area (NO clickable, NO tap-to-jump!)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isDragging = true
                            dragFraction = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                            onDraggingStateChanged(true)
                        },
                        onDragEnd = {
                            val target = (dragFraction * safeDuration).toLong().coerceIn(0L, safeDuration)
                            isDragging = false
                            onDraggingStateChanged(false)
                            onSeekCommitted(target)
                        },
                        onDragCancel = {
                            isDragging = false
                            onDraggingStateChanged(false)
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        dragFraction = (dragFraction + (dragAmount / barWidthPx)).coerceIn(0f, 1f)
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // Track background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isDragging) 6.dp else 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.28f))
            )
            // Filled progress track
            Box(
                modifier = Modifier
                    .fillMaxWidth(displayFraction)
                    .height(if (isDragging) 6.dp else 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(c.accent)
            )
            // Thumb knob positioned along width
            Box(
                modifier = Modifier
                    .offset {
                        val thumbPx = if (isDragging) 18.dp.toPx() else 14.dp.toPx()
                        val x = (displayFraction * (barWidthPx - thumbPx)).roundToInt()
                        IntOffset(x, 0)
                    }
                    .size(if (isDragging) 18.dp else 14.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(c.accent)
                    .border(2.dp, Color.White, CircleShape)
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun FullscreenPlayerScreen(
    exoPlayer: ExoPlayer,
    repo: IptvRepository,
    lockedCategories: Set<String>,
    currentChannel: ChannelItem,
    channelList: List<ChannelItem>,
    isPlaying: Boolean,
    isBuffering: Boolean,
    tracksVersion: Int,
    isLandscapeFullscreen: Boolean,
    isTvMode: Boolean,
    showGestures: Boolean,
    strings: AppStrings,
    onSelectChannel: (ChannelItem) -> Unit,
    onToggleFullscreenOrientation: () -> Unit,
    subtitleSession: ActiveSubtitleSession,
    onUpdateSubtitleSession: (ActiveSubtitleSession) -> Unit,
    onMinimizeToFloat: () -> Unit,
    onTracksModified: () -> Unit
) {
    val c = LocalYlowColors.current
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val isVod = currentChannel.mediaType == MediaType.MOVIE || currentChannel.mediaType == MediaType.SERIES
    val currentIndex = remember(currentChannel, channelList) {
        channelList.indexOfFirst { it.id == currentChannel.id && it.streamUrl == currentChannel.streamUrl }.coerceAtLeast(0)
    }

    val coroutineScope = rememberCoroutineScope()
    var controlsVisible by remember { mutableStateOf(true) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var showChannelDrawer by remember { mutableStateOf(false) }
    var showTrackDrawer by remember { mutableStateOf(false) }
    var isSeekDragging by remember { mutableStateOf(false) }

    // Indonesian Subtitle (Hoisted in YlowstoneApp so it survives PiP, Minimize, and App Switching!)
    val currentSubSession = if (subtitleSession.streamUrl == currentChannel.streamUrl) {
        subtitleSession
    } else {
        ActiveSubtitleSession(streamUrl = currentChannel.streamUrl)
    }
    val subCandidates = currentSubSession.candidates
    val activeSubRawFile = currentSubSession.rawFile
    val activeSubCues = currentSubSession.cues
    val activeSubLabel = currentSubSession.label
    val subOffsetMs = currentSubSession.offsetMs

    var subSearching by remember(currentChannel.streamUrl) { mutableStateOf(false) }
    var subDownloadingId by remember(currentChannel.streamUrl) { mutableStateOf<String?>(null) }
    var subError by remember(currentChannel.streamUrl) { mutableStateOf<String?>(null) }

    val parsedSeriesInfo = remember(currentChannel) {
        if (currentChannel.mediaType != MediaType.SERIES) {
            Triple(currentChannel.name, null as Int?, null as Int?)
        } else {
            val m = Regex("""[Ss](\d+)[Ee](\d+)""").find(currentChannel.name)
            val sNum = m?.groupValues?.getOrNull(1)?.toIntOrNull()
            val eNum = m?.groupValues?.getOrNull(2)?.toIntOrNull()
            val titleCandidate = if (sNum != null && currentChannel.category.isNotBlank()) {
                currentChannel.category
            } else {
                currentChannel.name
            }
            Triple(titleCandidate, sNum, eNum)
        }
    }

    fun applyExternalSubtitleFile(rawFile: File, label: String, offsetMs: Long) {
        coroutineScope.launch {
            val cues = SubtitleFetcher.parseSrtCues(rawFile)
            if (cues.isEmpty()) {
                Toast.makeText(context, "Isi file subtitle kosong atau format tidak didukung", Toast.LENGTH_SHORT).show()
                return@launch
            }
            onUpdateSubtitleSession(
                currentSubSession.copy(
                    streamUrl = currentChannel.streamUrl,
                    rawFile = rawFile,
                    cues = cues,
                    label = label,
                    offsetMs = offsetMs
                )
            )
            onTracksModified()
        }
    }

    fun clearExternalSubtitle() {
        onUpdateSubtitleSession(
            currentSubSession.copy(
                rawFile = null,
                cues = emptyList(),
                label = "",
                offsetMs = 0L
            )
        )
        onTracksModified()
    }

    val localSubtitlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                SubtitleFetcher.copyLocalSubtitleToCache(context, uri)
                    .onSuccess { rawFile ->
                        applyExternalSubtitleFile(rawFile, "Subtitle Lokal (ID)", 0L)
                        Toast.makeText(context, "Subtitle lokal aktif", Toast.LENGTH_SHORT).show()
                    }
                    .onFailure { err ->
                        Toast.makeText(context, err.message ?: "Gagal memuat file subtitle", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    // Movie & Series playback + high-precision 150ms subtitle timeline polling
    var positionMs by remember(currentChannel.streamUrl) { mutableLongStateOf(exoPlayer.currentPosition.coerceAtLeast(0L)) }
    var durationMs by remember(currentChannel.streamUrl) { mutableLongStateOf(exoPlayer.duration.coerceAtLeast(0L)) }
    LaunchedEffect(exoPlayer, currentChannel.streamUrl, isVod, activeSubCues.isNotEmpty()) {
        if (!isVod && activeSubCues.isEmpty()) return@LaunchedEffect
        while (true) {
            val dur = exoPlayer.duration
            if (dur > 0L) durationMs = dur
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            delay(150)
        }
    }

    val currentSubtitleText = remember(activeSubCues, positionMs, subOffsetMs) {
        SubtitleFetcher.findCueTextAt(activeSubCues, positionMs + subOffsetMs)
    }

    // Top-Left In-Video Clock & Date Pill: 0-5s Bright (100%) -> 5-10s Dimmed (35%) -> Fade Out Completely
    var dateLine by remember { mutableStateOf("") }
    var timeLine by remember { mutableStateOf("") }
    var clockStage by remember { mutableIntStateOf(2) } // 2 = bright, 1 = dim, 0 = hidden
    var clockTriggerNonce by remember { mutableIntStateOf(0) }
    LaunchedEffect(currentChannel.streamUrl, clockTriggerNonce) {
        val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val now = Date()
        dateLine = dateFmt.format(now)
        timeLine = timeFmt.format(now)
        clockStage = 2
        delay(5000L)
        clockStage = 1
        delay(5000L)
        clockStage = 0
    }

    // Double-Tap ±10s Skip HUD (Movies & Series ONLY)
    var skipHudText by remember { mutableStateOf("") }
    var skipHudNonce by remember { mutableIntStateOf(0) }
    LaunchedEffect(skipHudNonce) {
        if (skipHudText.isNotEmpty()) {
            delay(900)
            skipHudText = ""
        }
    }

    // In-Landscape Channel Drawer Search
    var drawerSearchQuery by remember { mutableStateOf("") }
    var drawerSearchResults by remember { mutableStateOf<List<ChannelItem>>(channelList) }

    LaunchedEffect(drawerSearchQuery, showChannelDrawer, channelList, currentChannel.playlistId) {
        if (!showChannelDrawer) return@LaunchedEffect
        val q = drawerSearchQuery.trim()
        if (q.isEmpty()) {
            drawerSearchResults = channelList
        } else {
            delay(150)
            drawerSearchResults = withContext(Dispatchers.IO) {
                repo.db.queryChannels(
                    playlistId = currentChannel.playlistId,
                    mediaType = currentChannel.mediaType,
                    specialFilter = "ALL",
                    searchQuery = q,
                    lockedCategories = lockedCategories,
                    limit = 150
                )
            }
        }
    }

    val aspectModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Fill",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom",
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH to "16:9"
    )
    var aspectIndex by remember { mutableIntStateOf(0) }

    var brightnessLevel by remember { mutableFloatStateOf(0.5f) }
    var volumeLevel by remember {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        mutableFloatStateOf(cur.toFloat() / max.toFloat())
    }
    var activeHud by remember { mutableStateOf("") }

    LaunchedEffect(controlsVisible, isScreenLocked, showChannelDrawer, showTrackDrawer, isSeekDragging) {
        if (controlsVisible && !showChannelDrawer && !showTrackDrawer && !isSeekDragging) {
            delay(4800)
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
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    keepScreenOn = isPlaying
                    resizeMode = aspectModes[aspectIndex].first
                }
            },
            update = { view ->
                view.player = exoPlayer
                view.keepScreenOn = isPlaying
                view.resizeMode = aspectModes[aspectIndex].first
            },
            modifier = Modifier.fillMaxSize()
        )

        // Direct Indonesian Subtitle Overlay inside video frame
        if (currentSubtitleText.isNotEmpty()) {
            val subBottomPad = when {
                controlsVisible && !isScreenLocked && isVod -> 112.dp
                controlsVisible && !isScreenLocked -> 76.dp
                else -> 26.dp
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 28.dp, end = 28.dp, bottom = subBottomPad)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.74f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentSubtitleText,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 21.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        // Top-Left In-Video Clock & Date Capsule: Bright (5s) -> Dimmed (5s) -> Hidden
        val clockTargetAlpha by animateFloatAsState(
            targetValue = if (clockStage == 2) 1f else 0.34f,
            animationSpec = tween(durationMillis = 550),
            label = "clockDimAlpha"
        )
        val clockTopPad by animateDpAsState(
            targetValue = if (controlsVisible && !isScreenLocked) 68.dp else 14.dp,
            animationSpec = tween(durationMillis = 280),
            label = "clockTopPad"
        )
        AnimatedVisibility(
            visible = clockStage > 0 && timeLine.isNotEmpty() && !showChannelDrawer && !showTrackDrawer,
            enter = fadeIn(tween(320)),
            exit = fadeOut(tween(520)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .systemBarsPadding()
                .padding(start = 18.dp, top = clockTopPad)
        ) {
            Row(
                modifier = Modifier
                    .graphicsLayer { alpha = clockTargetAlpha }
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xAA182028),
                                Color(0x880C1014)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.30f),
                                c.accent.copy(alpha = 0.30f)
                            )
                        ),
                        shape = CircleShape
                    )
                    .padding(horizontal = 13.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(c.accent)
                )
                Text(
                    text = timeLine,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
                Text(
                    text = "•",
                    color = Color.White.copy(alpha = 0.50f),
                    fontSize = 12.sp
                )
                Text(
                    text = dateLine,
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Tap + Double-Tap (±10s for Movies/Series) + Optional Left/Right Vertical Gestures
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Half: Tap toggle controls, Double-Tap -10s (VOD only), Vertical Drag Brightness
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(isVod, isScreenLocked, showChannelDrawer, showTrackDrawer) {
                        detectTapGestures(
                            onTap = {
                                if (showChannelDrawer || showTrackDrawer) {
                                    showChannelDrawer = false
                                    showTrackDrawer = false
                                } else {
                                    controlsVisible = !controlsVisible
                                    clockTriggerNonce++
                                }
                            },
                            onDoubleTap = {
                                if (isVod && !isScreenLocked && !showChannelDrawer && !showTrackDrawer) {
                                    val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                    exoPlayer.seekTo(target)
                                    positionMs = target
                                    skipHudText = "-10s"
                                    skipHudNonce++
                                }
                            }
                        )
                    }
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
            // Right Half: Tap toggle controls, Double-Tap +10s (VOD only), Vertical Drag Volume
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(isVod, isScreenLocked, showChannelDrawer, showTrackDrawer) {
                        detectTapGestures(
                            onTap = {
                                if (showChannelDrawer || showTrackDrawer) {
                                    showChannelDrawer = false
                                    showTrackDrawer = false
                                } else {
                                    controlsVisible = !controlsVisible
                                    clockTriggerNonce++
                                }
                            },
                            onDoubleTap = {
                                if (isVod && !isScreenLocked && !showChannelDrawer && !showTrackDrawer) {
                                    val maxDur = exoPlayer.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                                    val target = (exoPlayer.currentPosition + 10_000L).coerceAtMost(maxDur)
                                    exoPlayer.seekTo(target)
                                    positionMs = target
                                    skipHudText = "+10s"
                                    skipHudNonce++
                                }
                            }
                        )
                    }
                    .then(
                        if (showGestures && !isScreenLocked) {
                            Modifier.pointerInput(Unit) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                    volumeLevel = (volumeLevel - dragAmount / 500f).coerceIn(0f, 1f)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (volumeLevel * maxVol).toInt(), 0)
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

        // Double-Tap ±10s Skip HUD Badge
        AnimatedVisibility(
            visible = skipHudText.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(
                if (skipHudText.startsWith("-")) Alignment.CenterStart else Alignment.CenterEnd
            ).padding(horizontal = 44.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .border(1.dp, c.accent.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    if (skipHudText.startsWith("-")) Icons.Rounded.FastRewind else Icons.Rounded.FastForward,
                    contentDescription = null,
                    tint = c.accent,
                    modifier = Modifier.size(22.dp)
                )
                Text(skipHudText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        // Brightness / Volume Gesture HUD
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
                Icon(if (isBright) Icons.Rounded.Brightness6 else Icons.Rounded.VolumeUp, contentDescription = null, tint = c.accent)
                val pct = ((if (isBright) brightnessLevel else volumeLevel) * 100).toInt()
                Text("$pct%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        // Screen Locked Unlock Pill
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

        // ---- STREAMLINED SMARTERS-PROPORTIONED PLAYER CONTROLS OVERLAY ----
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
                                Color.Black.copy(alpha = 0.74f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.84f)
                            )
                        )
                    )
                    .systemBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                // Top Bar: Left Minimize/Back + Title | Right Search, Lock, Settings
                Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f).padding(end = 12.dp)
                        ) {
                            IconButton(
                                onClick = onMinimizeToFloat,
                                modifier = Modifier.size(44.dp).clip(CircleShape).background(c.accent)
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = strings.floatPlayer, tint = c.onAccent, modifier = Modifier.size(28.dp))
                            }
                            Column {
                                Text(currentChannel.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(currentChannel.category, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        // Clean Right Controls: Search + Lock + Settings/Tracks ONLY
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { showChannelDrawer = true },
                                modifier = Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f))
                            ) {
                                Icon(Icons.Rounded.Search, contentDescription = strings.search, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                            IconButton(
                                onClick = { isScreenLocked = true; controlsVisible = true },
                                modifier = Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f))
                            ) {
                                Icon(Icons.Rounded.LockOpen, contentDescription = strings.screenLocked, tint = Color.White, modifier = Modifier.size(21.dp))
                            }
                            IconButton(
                                onClick = { showTrackDrawer = true },
                                modifier = Modifier.size(44.dp).clip(CircleShape).background(c.accent)
                            ) {
                                Icon(Icons.Rounded.Settings, contentDescription = strings.tracks, tint = c.onAccent, modifier = Modifier.size(23.dp))
                            }
                        }
                    }
                }

                // Larger, Tighter Smarters Pro-style Center Controls (Prev · Play/Pause · Next)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(26.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (channelList.size > 1) {
                                val prev = if (currentIndex > 0) currentIndex - 1 else channelList.lastIndex
                                onSelectChannel(channelList[prev])
                            }
                        },
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.48f))
                    ) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(34.dp))
                    }

                    IconButton(
                        onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                        modifier = Modifier.size(72.dp).clip(CircleShape).background(c.accent)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "PlayPause",
                            tint = c.onAccent,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (channelList.size > 1) {
                                val next = (currentIndex + 1) % channelList.size
                                onSelectChannel(channelList[next])
                            }
                        },
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.48f))
                    ) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                }

                // Bottom Region: Drag-Only Seekbar (Movies & Series ONLY!) + Larger Balanced Bottom Bar
                Column(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isVod && durationMs > 0L) {
                        DragOnlySeekBar(
                            positionMs = positionMs,
                            durationMs = durationMs,
                            onSeekCommitted = { target ->
                                exoPlayer.seekTo(target)
                                positionMs = target
                            },
                            onDraggingStateChanged = { dragging -> isSeekDragging = dragging },
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerBottomPill(Icons.Rounded.FormatListBulleted, strings.channelList) {
                            showChannelDrawer = true
                        }
                        PlayerBottomPill(Icons.Rounded.AspectRatio, aspectModes[aspectIndex].second) {
                            aspectIndex = (aspectIndex + 1) % aspectModes.size
                        }
                        if (!isTvMode) {
                            PlayerBottomPill(
                                if (isLandscapeFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                if (isLandscapeFullscreen) strings.exitFullscreen else strings.fullscreen,
                                highlight = true
                            ) {
                                onToggleFullscreenOrientation()
                            }
                        }
                    }
                }
            }
        }

        // Left Channel Drawer WITH BUILT-IN SEARCH (stays Landscape!)
        AnimatedVisibility(
            visible = showChannelDrawer,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Column(
                modifier = Modifier
                    .width(310.dp)
                    .fillMaxHeight()
                    .background(Color(0xF2101215))
                    .systemBarsPadding()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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

                OutlinedTextField(
                    value = drawerSearchQuery,
                    onValueChange = { drawerSearchQuery = it },
                    placeholder = { Text(strings.searchChannelPlaceholder, fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (drawerSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { drawerSearchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.14f))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    items(drawerSearchResults, key = { "drw_${it.id}_${it.streamUrl}" }) { ch ->
                        val active = ch.id == currentChannel.id && ch.streamUrl == currentChannel.streamUrl
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) c.accentContainer else Color.White.copy(alpha = 0.04f))
                                .clickable {
                                    onSelectChannel(ch)
                                    showChannelDrawer = false
                                }
                                .padding(horizontal = 10.dp, vertical = 9.dp)
                        ) {
                            Text(
                                ch.name,
                                color = if (active) c.accent else Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                ch.category,
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Right Settings & Tracks Drawer (with Indonesian Subtitles + Sync Controls)
        AnimatedVisibility(
            visible = showTrackDrawer,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            val tracks = remember(tracksVersion) { exoPlayer.currentTracks }
            Column(
                modifier = Modifier
                    .width(325.dp)
                    .fillMaxHeight()
                    .background(Color(0xF5101215))
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
                        Icon(Icons.Rounded.ArrowBack, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(18.dp))
                    }
                    Text(strings.settings, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.16f))

                if (isVod) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.ClosedCaption, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                            Text(
                                "Subtitle Bahasa Indonesia",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (!subSearching) {
                                        subSearching = true
                                        subError = null
                                        coroutineScope.launch {
                                            val (qTitle, sNum, eNum) = parsedSeriesInfo
                                            SubtitleFetcher.searchIndoSubtitles(
                                                rawTitle = qTitle,
                                                isSeries = currentChannel.mediaType == MediaType.SERIES,
                                                season = sNum,
                                                episode = eNum
                                            ).onSuccess { list ->
                                                onUpdateSubtitleSession(currentSubSession.copy(streamUrl = currentChannel.streamUrl, candidates = list))
                                                if (list.isEmpty()) {
                                                    subError = "Subtitle Indonesia belum ditemukan"
                                                }
                                            }.onFailure { err ->
                                                subError = err.message ?: "Gagal mencari subtitle"
                                            }
                                            subSearching = false
                                        }
                                    }
                                },
                                enabled = !subSearching,
                                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (subSearching) "Mencari..." else "Cari Sub Indo",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            OutlinedButton(
                                onClick = { localSubtitlePicker.launch(arrayOf("*/*")) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("File .SRT", color = Color.White, fontSize = 12.sp, maxLines = 1)
                            }
                        }

                        if (activeSubRawFile != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(c.accent.copy(alpha = 0.14f))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "Aktif: $activeSubLabel",
                                    color = c.accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val offsetSec = subOffsetMs / 1000.0
                                val offsetLabel = String.format(Locale.US, "%+.1fs", offsetSec)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Sinkron: $offsetLabel", color = Color.White, fontSize = 11.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.White.copy(alpha = 0.15f))
                                                .clickable {
                                                    onUpdateSubtitleSession(currentSubSession.copy(offsetMs = subOffsetMs - 500L))
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("-0.5s", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.White.copy(alpha = 0.15f))
                                                .clickable {
                                                    onUpdateSubtitleSession(currentSubSession.copy(offsetMs = 0L))
                                                }
                                                .padding(horizontal = 7.dp, vertical = 4.dp)
                                        ) {
                                            Text("0s", color = Color.White, fontSize = 11.sp)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.White.copy(alpha = 0.15f))
                                                .clickable {
                                                    onUpdateSubtitleSession(currentSubSession.copy(offsetMs = subOffsetMs + 500L))
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("+0.5s", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Text(
                                    "Lepas Subtitle",
                                    color = Color(0xFFFF8A80),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { clearExternalSubtitle() }
                                )
                            }
                        }

                        if (!subError.isNullOrBlank()) {
                            Text(subError!!, color = Color(0xFFFFAB91), fontSize = 11.sp)
                        }

                        if (subCandidates.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                subCandidates.forEach { cand ->
                                    val isLoadingThis = subDownloadingId == cand.id
                                    val isSelectedThis = activeSubLabel == cand.releaseName
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelectedThis) c.accent.copy(alpha = 0.22f)
                                                else Color.White.copy(alpha = 0.06f)
                                            )
                                            .clickable(enabled = subDownloadingId == null) {
                                                subDownloadingId = cand.id
                                                coroutineScope.launch {
                                                    val (_, sNum, eNum) = parsedSeriesInfo
                                                    SubtitleFetcher.downloadSubtitleToCache(
                                                        context = context,
                                                        zipUrl = cand.zipUrl,
                                                        season = sNum,
                                                        episode = eNum
                                                    ).onSuccess { rawFile ->
                                                        applyExternalSubtitleFile(rawFile, cand.releaseName, 0L)
                                                        Toast.makeText(context, "Subtitle Indo dipasang", Toast.LENGTH_SHORT).show()
                                                    }.onFailure { err ->
                                                        Toast.makeText(context, err.message ?: "Gagal unduh subtitle", Toast.LENGTH_SHORT).show()
                                                    }
                                                    subDownloadingId = null
                                                }
                                            }
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = if (isLoadingThis) "Memasang: ${cand.releaseName}..." else cand.releaseName,
                                            color = if (isSelectedThis) c.accent else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val meta = buildString {
                                            if (cand.fullSeason) append("Paket Full Season")
                                            if (cand.author.isNotBlank()) {
                                                if (isNotEmpty()) append(" • ")
                                                append(cand.author)
                                            }
                                        }
                                        if (meta.isNotBlank()) {
                                            Text(meta, color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.16f))
                }

                TrackGroupSection(strings.videoTrack, Icons.Rounded.Videocam, C.TRACK_TYPE_VIDEO, tracks, exoPlayer, strings, onTracksModified)
                TrackGroupSection(strings.audioTrack, Icons.Rounded.Audiotrack, C.TRACK_TYPE_AUDIO, tracks, exoPlayer, strings, onTracksModified)
                TrackGroupSection(strings.subtitleTrack, Icons.Rounded.Subtitles, C.TRACK_TYPE_TEXT, tracks, exoPlayer, strings, onTracksModified)
            }
        }
    }
}

@Composable
private fun PlayerBottomPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    highlight: Boolean = false,
    onClick: () -> Unit
) {
    val c = LocalYlowColors.current
    val bg = if (highlight) c.accent else Color.White.copy(alpha = 0.17f)
    val fg = if (highlight) c.onAccent else Color.White
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(20.dp))
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
                else -> fmt.label ?: fmt.language ?: "Subtitle ${i + 1}"
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
