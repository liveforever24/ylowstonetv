package com.ylowstone.tv.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ylowstone.tv.data.ActiveSubtitleSession
import com.ylowstone.tv.data.SubtitleFetcher
import com.ylowstone.tv.i18n.AppStrings
import com.ylowstone.tv.model.ChannelItem
import com.ylowstone.tv.model.MediaType
import com.ylowstone.tv.ui.components.ChannelLogoBox
import com.ylowstone.tv.ui.components.glossyPressPill
import com.ylowstone.tv.ui.components.tvFocusBorder
import com.ylowstone.tv.ui.theme.LocalYlowColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private fun formatSplitTimeMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0L) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

/**
 * Player setengah layar (Split-Screen) responsif v2.4.6:
 * - Mode Ponsel (Portrait): Gestur seret video ke bawah (Swipe-Down to PiP) halus + tombol Pengaturan Bahasa (Audio & Subtitle)
 * - Ikon PiP dihilangkan dari overlay pemutar; seret video ke bawah langsung masuk Mini-Player / PiP
 * - Mode TV / Landscape: Video besar di kiri + Panel Daftar Putar di kanan (D-Pad friendly)
 */
@kotlin.OptIn(ExperimentalFoundationApi::class)
@OptIn(UnstableApi::class)
@Composable
fun SplitPlayerScreen(
    exoPlayer: ExoPlayer,
    currentChannel: ChannelItem,
    channelList: List<ChannelItem>,
    isPlaying: Boolean,
    isBuffering: Boolean,
    tracksVersion: Int,
    isTvMode: Boolean,
    subtitleSession: ActiveSubtitleSession,
    onUpdateSubtitleSession: (ActiveSubtitleSession) -> Unit,
    onTracksModified: () -> Unit,
    onSaveSeriesSubtitleOffset: (String, Long) -> Unit = { _, _ -> },
    strings: AppStrings,
    onSelectChannel: (ChannelItem) -> Unit,
    onLongPressChannel: (ChannelItem) -> Unit,
    onToggleFavorite: (ChannelItem) -> Unit,
    onEnterFullscreen: () -> Unit,
    onMinimizeToFloat: () -> Unit,
    onEnterSystemPip: () -> Unit,
    onClosePlayer: () -> Unit
) {
    val c = LocalYlowColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val isSeriesEffective = remember(currentChannel) {
        val urlPathLower = currentChannel.streamUrl.lowercase(Locale.US).substringBefore('?')
        currentChannel.mediaType == MediaType.SERIES ||
            urlPathLower.contains("/series/") ||
            Regex("""[Ss](\d+)[Ee](\d+)""").containsMatchIn(currentChannel.name)
    }
    val isVod = isSeriesEffective ||
        currentChannel.mediaType == MediaType.MOVIE ||
        currentChannel.streamUrl.lowercase(Locale.US).substringBefore('?').let {
            it.contains("/movie/") || it.endsWith(".mp4") || it.endsWith(".mkv") || it.endsWith(".webm")
        }

    val parsedSeriesInfo = remember(currentChannel, isSeriesEffective) {
        if (!isSeriesEffective) {
            Triple(currentChannel.name, null as Int?, null as Int?)
        } else {
            val m = Regex("""[Ss](\d+)[Ee](\d+)""").find(currentChannel.name)
            val sNum = m?.groupValues?.getOrNull(1)?.toIntOrNull()
            val eNum = m?.groupValues?.getOrNull(2)?.toIntOrNull()
            val nameBeforeSe = m?.let { currentChannel.name.substring(0, it.range.first).trim(' ', '-', ':', '|', '.', '_') } ?: ""
            val titleCandidate = when {
                nameBeforeSe.length >= 2 -> nameBeforeSe
                sNum != null && currentChannel.category.isNotBlank() -> currentChannel.category
                else -> currentChannel.name
            }
            Triple(titleCandidate, sNum, eNum)
        }
    }

    val currentIndex = remember(currentChannel, channelList) {
        channelList.indexOfFirst { it.id == currentChannel.id && it.streamUrl == currentChannel.streamUrl }.coerceAtLeast(0)
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var showTrackSheet by remember { mutableStateOf(false) }
    var queueSearchQuery by remember { mutableStateOf("") }
    val filteredQueue = remember(channelList, queueSearchQuery) {
        val q = queueSearchQuery.trim()
        if (q.isEmpty()) channelList
        else channelList.filter {
            it.name.contains(q, ignoreCase = true) || it.category.contains(q, ignoreCase = true)
        }
    }

    // State gestur seret video ke bawah menuju PiP
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isDraggingDown by remember { mutableStateOf(false) }
    val animatedDragY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "splitSwipeDownY"
    )
    val effectiveDragY = if (isDraggingDown) dragOffsetY else animatedDragY
    val pipThresholdPx = remember(density) { with(density) { 125.dp.toPx() } }
    val dragFraction = (effectiveDragY / (pipThresholdPx * 1.4f)).coerceIn(0f, 1f)

    // Subtitle & Track state (sinkron dengan FullscreenPlayerScreen)
    val currentSubSession = remember(subtitleSession, currentChannel.streamUrl) {
        if (subtitleSession.streamUrl == currentChannel.streamUrl) subtitleSession
        else ActiveSubtitleSession(streamUrl = currentChannel.streamUrl)
    }
    val subCandidates = currentSubSession.candidates
    val activeSubCues = currentSubSession.cues
    val activeSubLabel = currentSubSession.label
    val subOffsetMs = currentSubSession.offsetMs

    var subSearching by remember(currentChannel.streamUrl) { mutableStateOf(false) }
    var subDownloadingId by remember(currentChannel.streamUrl) { mutableStateOf<String?>(null) }
    var subError by remember(currentChannel.streamUrl) { mutableStateOf<String?>(null) }

    var positionMs by remember(currentChannel.streamUrl) { mutableLongStateOf(exoPlayer.currentPosition.coerceAtLeast(0L)) }
    var durationMs by remember(currentChannel.streamUrl) { mutableLongStateOf(exoPlayer.duration.coerceAtLeast(0L)) }

    LaunchedEffect(exoPlayer, currentChannel.streamUrl, isVod, activeSubCues.isNotEmpty()) {
        if (!isVod && activeSubCues.isEmpty()) return@LaunchedEffect
        while (true) {
            val dur = exoPlayer.duration
            if (dur > 0L) durationMs = dur
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            delay(200)
        }
    }

    val currentSubtitleText = remember(activeSubCues, positionMs, subOffsetMs) {
        SubtitleFetcher.findCueTextAt(activeSubCues, positionMs + subOffsetMs)
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
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            onTracksModified()
        }
    }

    fun clearExternalSubtitle() {
        onUpdateSubtitleSession(
            currentSubSession.copy(
                streamUrl = currentChannel.streamUrl,
                rawFile = null,
                cues = emptyList(),
                label = "",
                offsetMs = 0L
            )
        )
    }

    val localSubLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val cached = withContext(Dispatchers.IO) {
                    runCatching {
                        val subDir = File(context.cacheDir, "subtitles").apply { mkdirs() }
                        val target = File(subDir, "local_${System.currentTimeMillis()}.srt")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            target.outputStream().use { out -> input.copyTo(out) }
                        }
                        target
                    }.getOrNull()
                }
                if (cached != null && cached.length() > 10L) {
                    applyExternalSubtitleFile(cached, "File Lokal (.srt)", 0L)
                    Toast.makeText(context, "Subtitle lokal berhasil dipasang", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Gagal membaca file subtitle lokal", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Track extraction (Audio & Embedded Subtitles)
    val audioTracks = remember(tracksVersion, exoPlayer.currentTracks) {
        val list = mutableListOf<Triple<androidx.media3.common.TrackGroup, Int, String>>()
        exoPlayer.currentTracks.groups.forEach { grp ->
            if (grp.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until grp.length) {
                    val fmt = grp.getTrackFormat(i)
                    val lang = fmt.language?.uppercase(Locale.US) ?: "AUD"
                    val label = fmt.label ?: "$lang (${fmt.channelCount}ch)"
                    list.add(Triple(grp.mediaTrackGroup, i, label))
                }
            }
        }
        list
    }

    val textTracks = remember(tracksVersion, exoPlayer.currentTracks) {
        val list = mutableListOf<Triple<androidx.media3.common.TrackGroup, Int, String>>()
        exoPlayer.currentTracks.groups.forEach { grp ->
            if (grp.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until grp.length) {
                    val fmt = grp.getTrackFormat(i)
                    val lang = fmt.language?.uppercase(Locale.US) ?: "SUB"
                    val label = fmt.label ?: lang
                    list.add(Triple(grp.mediaTrackGroup, i, label))
                }
            }
        }
        list
    }

    LaunchedEffect(controlsVisible, showTrackSheet) {
        if (controlsVisible && !showTrackSheet) {
            delay(4000)
            controlsVisible = false
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(currentChannel.streamUrl, filteredQueue.size) {
        val idx = filteredQueue.indexOfFirst { it.id == currentChannel.id && it.streamUrl == currentChannel.streamUrl }
        if (idx >= 0) {
            runCatching { listState.scrollToItem((idx - 2).coerceAtLeast(0)) }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg.copy(alpha = (1f - dragFraction * 0.72f).coerceIn(0.18f, 1f)))
            .systemBarsPadding()
    ) {
        val useSideBySide = isTvMode || maxWidth >= 600.dp

        if (!useSideBySide) {
            // ---- MODE PONSEL (Portrait): Seret Video ke Bawah untuk Langsung PiP ----
            Column(modifier = Modifier.fillMaxSize()) {
                // 16:9 Video Region dengan gestur seret ke bawah (Swipe-Down to PiP)
                val videoCorner = (dragFraction * 18f).dp
                val videoScale = 1f - (dragFraction * 0.14f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .graphicsLayer {
                            translationY = effectiveDragY
                            scaleX = videoScale
                            scaleY = videoScale
                        }
                        .clip(RoundedCornerShape(videoCorner))
                        .background(Color.Black)
                        .pointerInput(pipThresholdPx) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    isDraggingDown = true
                                    controlsVisible = false
                                },
                                onDragCancel = {
                                    isDraggingDown = false
                                    dragOffsetY = 0f
                                },
                                onDragEnd = {
                                    isDraggingDown = false
                                    if (dragOffsetY >= pipThresholdPx) {
                                        dragOffsetY = 0f
                                        onMinimizeToFloat()
                                    } else {
                                        dragOffsetY = 0f
                                    }
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    val next = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                                    if (next > 0f || dragAmount > 0f) {
                                        change.consume()
                                        dragOffsetY = next.coerceAtMost(pipThresholdPx * 2.4f)
                                    }
                                }
                            )
                        }
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { controlsVisible = !controlsVisible }
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

                    if (currentSubtitleText.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp, vertical = if (controlsVisible) 44.dp else 10.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.74f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = currentSubtitleText,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    if (isBuffering) {
                        CircularProgressIndicator(
                            color = c.accent,
                            strokeWidth = 3.dp,
                            modifier = Modifier.align(Alignment.Center).size(34.dp)
                        )
                    }

                    // Indikator tipis saat sedang menyeret ke bawah menuju PiP
                    if (isDraggingDown && dragOffsetY > 18f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 10.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (dragOffsetY >= pipThresholdPx) "Lepas untuk Mini-Player" else "Seret ke bawah untuk PiP",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = controlsVisible && !isDraggingDown,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.72f),
                                            Color.Black.copy(alpha = 0.18f),
                                            Color.Black.copy(alpha = 0.78f)
                                        )
                                    )
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            // Top Bar: Minimize (Arrow Down) + Settings (Bahasa Audio/Sub) + Close (Tanpa ikon PiP)
                            Row(
                                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(
                                    onClick = onMinimizeToFloat,
                                    modifier = Modifier.size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                                ) {
                                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = strings.floatPlayer, tint = Color.White, modifier = Modifier.size(22.dp))
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    IconButton(
                                        onClick = { showTrackSheet = true },
                                        modifier = Modifier.size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                                    ) {
                                        Icon(Icons.Rounded.Settings, contentDescription = strings.tracks, tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = onClosePlayer,
                                        modifier = Modifier.size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                                    ) {
                                        Icon(Icons.Rounded.Close, contentDescription = strings.closeBtn, tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            // Center: Prev · Play/Pause · Next
                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (channelList.size > 1) {
                                            val prev = if (currentIndex > 0) currentIndex - 1 else channelList.lastIndex
                                            onSelectChannel(channelList[prev])
                                        }
                                    },
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.50f))
                                ) {
                                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(24.dp))
                                }

                                IconButton(
                                    onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                                    modifier = Modifier.size(52.dp).clip(CircleShape).background(c.accent)
                                ) {
                                    Icon(
                                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = "PlayPause",
                                        tint = c.onAccent,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (channelList.size > 1) {
                                            val next = (currentIndex + 1) % channelList.size
                                            onSelectChannel(channelList[next])
                                        }
                                    },
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.50f))
                                ) {
                                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                            }

                            // Bottom Bar: VOD time + Fullscreen button
                            Row(
                                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (isVod && durationMs > 0L) {
                                    Text(
                                        text = "${formatSplitTimeMs(positionMs)} / ${formatSplitTimeMs(durationMs)}",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        modifier = Modifier.padding(start = 4.dp)
                                    ) {
                                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                                        Text(strings.liveTv, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(c.accent)
                                        .clickable(onClick = onEnterFullscreen)
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Rounded.Fullscreen, contentDescription = strings.fullscreen, tint = c.onAccent, modifier = Modifier.size(16.dp))
                                    Text(strings.fullscreen, color = c.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Channel Info Strip + Queue (memudar halus saat video diseret turun ke PiP)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .alpha((1f - dragFraction * 1.4f).coerceIn(0f, 1f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(c.surface)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ChannelLogoBox(currentChannel.logoUrl, 38.dp, corner = 10.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentChannel.name,
                                color = c.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentChannel.category,
                                color = c.textSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { onToggleFavorite(currentChannel) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                if (currentChannel.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Fav",
                                tint = if (currentChannel.isFavorite) c.accent else c.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = { onLongPressChannel(currentChannel) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = c.textSecondary, modifier = Modifier.size(20.dp))
                        }
                    }

                    HorizontalDivider(color = c.glassBorder, thickness = 0.6.dp)

                    SplitQueuePanel(
                        currentChannel = currentChannel,
                        filteredQueue = filteredQueue,
                        totalCount = channelList.size,
                        searchQuery = queueSearchQuery,
                        onSearchChange = { queueSearchQuery = it },
                        listState = listState,
                        isTvMode = false,
                        strings = strings,
                        onSelectChannel = onSelectChannel,
                        onLongPressChannel = onLongPressChannel,
                        onToggleFavorite = onToggleFavorite,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
        } else {
            // ---- MODE TV / LANDSCAPE: Video Kiri + Daftar Putar Kanan ----
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.35f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.Black)
                            .border(1.dp, c.glassBorder, RoundedCornerShape(18.dp))
                            .tvFocusBorder(isTvMode, RoundedCornerShape(18.dp))
                            .clickable(onClick = onEnterFullscreen)
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
                        if (currentSubtitleText.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 18.dp, vertical = 12.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.74f))
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(currentSubtitleText, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                            }
                        }
                        if (isBuffering) {
                            CircularProgressIndicator(color = c.accent, modifier = Modifier.align(Alignment.Center))
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(c.surface)
                            .border(1.dp, c.glassBorder, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ChannelLogoBox(currentChannel.logoUrl, 38.dp, corner = 10.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentChannel.name,
                                color = c.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentChannel.category,
                                color = c.textSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(c.surfaceElevated)
                                .tvFocusBorder(isTvMode, CircleShape)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "PlayPause",
                                tint = c.textPrimary
                            )
                        }

                        IconButton(
                            onClick = { showTrackSheet = true },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(c.surfaceElevated)
                                .tvFocusBorder(isTvMode, CircleShape)
                        ) {
                            Icon(Icons.Rounded.Settings, contentDescription = strings.tracks, tint = c.textPrimary, modifier = Modifier.size(19.dp))
                        }

                        Button(
                            onClick = onEnterFullscreen,
                            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.tvFocusBorder(isTvMode, RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Rounded.Fullscreen, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.fullscreen, color = c.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(
                            onClick = onClosePlayer,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(c.surfaceElevated)
                                .tvFocusBorder(isTvMode, CircleShape)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = strings.closeBtn, tint = c.textPrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                SplitQueuePanel(
                    currentChannel = currentChannel,
                    filteredQueue = filteredQueue,
                    totalCount = channelList.size,
                    searchQuery = queueSearchQuery,
                    onSearchChange = { queueSearchQuery = it },
                    listState = listState,
                    isTvMode = isTvMode,
                    strings = strings,
                    onSelectChannel = onSelectChannel,
                    onLongPressChannel = onLongPressChannel,
                    onToggleFavorite = onToggleFavorite,
                    modifier = Modifier
                        .weight(0.65f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .background(c.surface)
                        .border(1.dp, c.glassBorder, RoundedCornerShape(18.dp))
                )
            }
        }
    }

    // ---- Dialog Pengaturan Bahasa (Audio & Subtitle) di Mode Potret (identik dengan Fullscreen) ----
    if (showTrackSheet) {
        AlertDialog(
            onDismissRequest = { showTrackSheet = false },
            containerColor = c.surface,
            shape = RoundedCornerShape(22.dp),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(strings.tracks, color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { showTrackSheet = false }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = strings.closeBtn, tint = c.textSecondary)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Audio Tracks
                    Text(strings.audioTrack, color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (audioTracks.isEmpty()) {
                        Text(strings.noTracksFound, color = c.textSecondary, fontSize = 12.sp)
                    } else {
                        audioTracks.forEach { (group, trackIdx, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(c.surfaceElevated)
                                    .clickable {
                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                                            .setOverrideForType(TrackSelectionOverride(group, trackIdx))
                                            .build()
                                        onTracksModified()
                                        Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(label, color = c.textPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Icon(Icons.Rounded.VolumeUp, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    HorizontalDivider(color = c.glassBorder)

                    // Embedded Subtitles
                    Text(strings.subtitleTrack, color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.surfaceElevated)
                            .clickable {
                                clearExternalSubtitle()
                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                    .build()
                                onTracksModified()
                                Toast.makeText(context, strings.disableTrack, Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.disableTrack, color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    textTracks.forEach { (group, trackIdx, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.surfaceElevated)
                                .clickable {
                                    clearExternalSubtitle()
                                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                        .setOverrideForType(TrackSelectionOverride(group, trackIdx))
                                        .build()
                                    onTracksModified()
                                    Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, color = c.textPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Icon(Icons.Rounded.Subtitles, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
                        }
                    }

                    HorizontalDivider(color = c.glassBorder)

                    // Cari Subtitle Indonesia & Muat Lokal (.srt)
                    Text("Subtitle Indonesia", color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (!subSearching) {
                                    subSearching = true
                                    subError = null
                                    coroutineScope.launch {
                                        val (qTitle, sNum, eNum) = parsedSeriesInfo
                                        SubtitleFetcher.searchIndoSubtitles(
                                            rawTitle = qTitle,
                                            isSeries = isSeriesEffective,
                                            season = sNum,
                                            episode = eNum
                                        ).onSuccess { list ->
                                            onUpdateSubtitleSession(currentSubSession.copy(streamUrl = currentChannel.streamUrl, candidates = list))
                                            if (list.isEmpty()) subError = "Subtitle Indonesia belum ditemukan"
                                        }.onFailure { err ->
                                            subError = err.message ?: "Gagal mencari subtitle"
                                        }
                                        subSearching = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (subSearching) "Mencari..." else "Cari Sub Indo", color = c.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { localSubLauncher.launch(arrayOf("*/*")) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(".SRT Lokal", fontSize = 11.sp)
                        }
                    }

                    if (subError != null) {
                        Text(subError!!, color = Color(0xFFFF8A80), fontSize = 11.sp)
                    }

                    // Kontrol Sinkronisasi Subtitle jika aktif
                    if (activeSubCues.isNotEmpty()) {
                        val offsetLabel = String.format(Locale.US, "%+.1fs", subOffsetMs / 1000.0)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.surfaceElevated)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Aktif: $activeSubLabel", color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Sinkron: $offsetLabel", color = c.textPrimary, fontSize = 11.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        "-0.5s",
                                        color = c.textPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(c.surface)
                                            .clickable {
                                                val nextOff = subOffsetMs - 500L
                                                onUpdateSubtitleSession(currentSubSession.copy(offsetMs = nextOff))
                                                onSaveSeriesSubtitleOffset(parsedSeriesInfo.first, nextOff)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                    Text(
                                        "0s",
                                        color = c.textSecondary,
                                        fontSize = 11.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(c.surface)
                                            .clickable {
                                                onUpdateSubtitleSession(currentSubSession.copy(offsetMs = 0L))
                                                onSaveSeriesSubtitleOffset(parsedSeriesInfo.first, 0L)
                                            }
                                            .padding(horizontal = 7.dp, vertical = 4.dp)
                                    )
                                    Text(
                                        "+0.5s",
                                        color = c.textPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(c.surface)
                                            .clickable {
                                                val nextOff = subOffsetMs + 500L
                                                onUpdateSubtitleSession(currentSubSession.copy(offsetMs = nextOff))
                                                onSaveSeriesSubtitleOffset(parsedSeriesInfo.first, nextOff)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = strings.quickSyncSubBtn,
                                    color = c.accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(c.accentContainer)
                                        .clickable {
                                            val synced = SubtitleFetcher.computeQuickSyncOffsetMs(activeSubCues, positionMs, subOffsetMs)
                                            onUpdateSubtitleSession(currentSubSession.copy(offsetMs = synced))
                                            onSaveSeriesSubtitleOffset(parsedSeriesInfo.first, synced)
                                            Toast.makeText(context, "${strings.quickSyncSubBtn} (${String.format(Locale.US, "%+.1fs", synced / 1000.0)})", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                                Text(
                                    text = "Lepas Subtitle",
                                    color = Color(0xFFFF8A80),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { clearExternalSubtitle() }
                                )
                            }
                        }
                    }

                    // Kandidat Subtitle Indo
                    subCandidates.forEach { cand ->
                        val isSelectedThis = activeSubLabel == cand.releaseName
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelectedThis) c.accentContainer else c.surfaceElevated)
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
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (subDownloadingId == cand.id) "Mengunduh..." else cand.releaseName,
                                color = if (isSelectedThis) c.accent else c.textPrimary,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrackSheet = false }) {
                    Text(strings.closeBtn, color = c.accent, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SplitQueuePanel(
    currentChannel: ChannelItem,
    filteredQueue: List<ChannelItem>,
    totalCount: Int,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    isTvMode: Boolean,
    strings: AppStrings,
    onSelectChannel: (ChannelItem) -> Unit,
    onLongPressChannel: (ChannelItem) -> Unit,
    onToggleFavorite: (ChannelItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalYlowColors.current
    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                Text(
                    text = strings.playlistQueueTitle,
                    color = c.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "$totalCount",
                color = c.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (totalCount > 8) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text(strings.searchChannelPlaceholder, fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                        }
                    }
                },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(filteredQueue, key = { "sq_${it.id}_${it.streamUrl}" }) { ch ->
                val isCurrent = ch.id == currentChannel.id && ch.streamUrl == currentChannel.streamUrl
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isCurrent) c.accentContainer else c.surfaceElevated)
                        .border(
                            width = if (isCurrent) 1.2.dp else 0.7.dp,
                            color = if (isCurrent) c.accent else c.glassBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .tvFocusBorder(isTvMode, RoundedCornerShape(12.dp))
                        .glossyPressPill(12f)
                        .combinedClickable(
                            onClick = { onSelectChannel(ch) },
                            onLongClick = { onLongPressChannel(ch) }
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChannelLogoBox(ch.logoUrl, 34.dp, corner = 8.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ch.name,
                            color = if (isCurrent) c.accent else c.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = ch.category,
                            color = c.textSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onToggleFavorite(ch) }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            if (ch.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Fav",
                            tint = if (ch.isFavorite) c.accent else c.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
