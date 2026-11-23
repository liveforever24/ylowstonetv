package com.ylowstone.tv.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.ylowstone.tv.data.IptvRepository
import com.ylowstone.tv.i18n.AppStrings
import com.ylowstone.tv.model.ChannelItem
import com.ylowstone.tv.ui.theme.LocalYlowColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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
 * - Idle state: 100% clean video inside rounded corners (NO visible buttons!)
 * - Single tap: reveals Prev, Play/Pause, Next, Fullscreen, System PiP, and Close (X)
 * - Auto-hides cleanly after 3 seconds
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
    // Starts CLEAN with ZERO buttons visible (pure YouTube PiP!)
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

        // Controls ONLY appear on single tap!
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.48f))
                    .padding(8.dp)
            ) {
                // Top Bar: Fullscreen & Close (X)
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

                // Center YouTube PiP Controls: Previous | Play/Pause | Next
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
                            tint = Color.White,
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

                // Subtle Bottom Channel Title
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
    onMinimizeToFloat: () -> Unit,
    onEnterSystemPip: () -> Unit,
    onTracksModified: () -> Unit
) {
    val c = LocalYlowColors.current
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val currentIndex = remember(currentChannel, channelList) {
        channelList.indexOfFirst { it.id == currentChannel.id && it.streamUrl == currentChannel.streamUrl }.coerceAtLeast(0)
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var showChannelDrawer by remember { mutableStateOf(false) }
    var showTrackDrawer by remember { mutableStateOf(false) }

    // In-Landscape Channel Drawer Search State (Search without rotating back to Portrait!)
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

        // Tap & Optional Gestures
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

        // Fullscreen Controls Overlay
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
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onMinimizeToFloat,
                            modifier = Modifier.size(38.dp).clip(CircleShape).background(c.accent)
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = strings.floatPlayer, tint = Color.White)
                        }
                        Column {
                            Text(currentChannel.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(currentChannel.category, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Direct Search-in-Landscape button!
                        IconButton(
                            onClick = { showChannelDrawer = true },
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f))
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = strings.search, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = onEnterSystemPip,
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f))
                        ) {
                            Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = strings.pipMode, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(currentChannel.streamUrl), "video/*")
                                }
                                runCatching { context.startActivity(Intent.createChooser(intent, strings.externalPlayer)) }
                            },
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f))
                        ) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = strings.externalPlayer, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { isScreenLocked = true; controlsVisible = true },
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f))
                        ) {
                            Icon(Icons.Rounded.LockOpen, contentDescription = strings.screenLocked, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { showTrackDrawer = true },
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(c.accent)
                        ) {
                            Icon(Icons.Rounded.Settings, contentDescription = strings.tracks, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Center Transport Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (channelList.size > 1) {
                                val prev = if (currentIndex > 0) currentIndex - 1 else channelList.lastIndex
                                onSelectChannel(channelList[prev])
                            }
                        },
                        modifier = Modifier.size(46.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                    ) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(28.dp))
                    }

                    IconButton(
                        onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                        modifier = Modifier.size(62.dp).clip(CircleShape).background(c.accent)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "PlayPause",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (channelList.size > 1) {
                                val next = (currentIndex + 1) % channelList.size
                                onSelectChannel(channelList[next])
                            }
                        },
                        modifier = Modifier.size(46.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                    ) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(28.dp))
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
                    PlayerBottomPill(Icons.Rounded.AspectRatio, aspectModes[aspectIndex].second) {
                        aspectIndex = (aspectIndex + 1) % aspectModes.size
                    }
                    PlayerBottomPill(Icons.Rounded.Tune, strings.tracks) {
                        showTrackDrawer = true
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

        // Left Channel Drawer WITH BUILT-IN SEARCH (stays Landscape, never flips to Portrait!)
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
                    .background(Color(0xF0101016))
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

                // Landscape In-Player Channel Search Field
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
                                .background(if (active) c.accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.04f))
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

        // Right Track Selection Drawer
        AnimatedVisibility(
            visible = showTrackDrawer,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            val tracks = remember(tracksVersion) { exoPlayer.currentTracks }
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .background(Color(0xF0101016))
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
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (highlight) c.accent else Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(17.dp))
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
