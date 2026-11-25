package com.ylowstone.tv.ui.screens

import androidx.annotation.OptIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
import kotlinx.coroutines.delay
import java.util.Locale

private fun formatSplitTimeMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0L) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

@kotlin.OptIn(ExperimentalFoundationApi::class)
@OptIn(UnstableApi::class)
@Composable
fun SplitPlayerScreen(
    exoPlayer: ExoPlayer,
    currentChannel: ChannelItem,
    channelList: List<ChannelItem>,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isTvMode: Boolean,
    subtitleSession: ActiveSubtitleSession,
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
    val isVod = currentChannel.mediaType == MediaType.MOVIE || currentChannel.mediaType == MediaType.SERIES
    val currentIndex = remember(currentChannel, channelList) {
        channelList.indexOfFirst { it.id == currentChannel.id && it.streamUrl == currentChannel.streamUrl }.coerceAtLeast(0)
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var queueSearchQuery by remember { mutableStateOf("") }
    val filteredQueue = remember(channelList, queueSearchQuery) {
        val q = queueSearchQuery.trim()
        if (q.isEmpty()) channelList
        else channelList.filter {
            it.name.contains(q, ignoreCase = true) || it.category.contains(q, ignoreCase = true)
        }
    }

    val activeSubCues = if (subtitleSession.streamUrl == currentChannel.streamUrl) subtitleSession.cues else emptyList()
    val subOffsetMs = if (subtitleSession.streamUrl == currentChannel.streamUrl) subtitleSession.offsetMs else 0L

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

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(4000)
            controlsVisible = false
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(currentChannel.streamUrl, filteredQueue.size) {
        val idx = filteredQueue.indexOfFirst { it.id == currentChannel.id && it.streamUrl == currentChannel.streamUrl }
        if (idx >= 0) {
            runCatching { listState.animateScrollToItem((idx - 1).coerceAtLeast(0)) }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .systemBarsPadding()
    ) {
        val useSideBySide = isTvMode || maxWidth >= 600.dp

        if (!useSideBySide) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
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

                    androidx.compose.animation.AnimatedVisibility(
                        visible = controlsVisible,
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
                                        onClick = onEnterSystemPip,
                                        modifier = Modifier.size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                                    ) {
                                        Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = strings.pipMode, tint = Color.White, modifier = Modifier.size(17.dp))
                                    }
                                    IconButton(
                                        onClick = onClosePlayer,
                                        modifier = Modifier.size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                                    ) {
                                        Icon(Icons.Rounded.Close, contentDescription = strings.closeBtn, tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.surface)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChannelLogoBox(currentChannel.logoUrl, 36.dp, corner = 9.dp)
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
                        onClick = { onLongPressChannel(currentChannel) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
                    }
                }

                HorizontalDivider(color = c.glassBorder, thickness = 0.8.dp)

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
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                            .clickable(onClick = onEnterFullscreen),
                        contentAlignment = Alignment.Center
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
                        if (isBuffering) {
                            CircularProgressIndicator(color = c.accent, modifier = Modifier.size(38.dp))
                        }
                        if (currentSubtitleText.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 20.dp, vertical = 14.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.74f))
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = currentSubtitleText,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                            }
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

                        OutlinedButton(
                            onClick = onMinimizeToFloat,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.tvFocusBorder(isTvMode, RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.floatPlayer, fontSize = 11.sp)
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
                text = "$totalCount ${strings.channelsCount}",
                color = c.textSecondary,
                fontSize = 11.sp
            )
        }

        if (totalCount > 6) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text(strings.searchChannelPlaceholder, fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                        }
                    }
                },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filteredQueue, key = { "sq_${it.id}_${it.streamUrl}" }) { ch ->
                val active = ch.id == currentChannel.id && ch.streamUrl == currentChannel.streamUrl
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (active) c.accentContainer else c.surface)
                        .border(1.dp, if (active) c.accent else c.glassBorder, RoundedCornerShape(13.dp))
                        .tvFocusBorder(isTvMode, RoundedCornerShape(13.dp))
                        .glossyPressPill(13f)
                        .combinedClickable(
                            onClick = { onSelectChannel(ch) },
                            onLongClick = { onLongPressChannel(ch) }
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChannelLogoBox(ch.logoUrl, 36.dp, corner = 9.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ch.name,
                            color = if (active) c.accent else c.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
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
                    if (ch.id > 0L) {
                        IconButton(
                            onClick = { onToggleFavorite(ch) },
                            modifier = Modifier.size(30.dp)
                        ) {
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
}
