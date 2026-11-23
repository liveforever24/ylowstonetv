package com.ylowstone.tv.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.ylowstone.tv.data.AppPrefs
import com.ylowstone.tv.data.IptvRepository
import com.ylowstone.tv.i18n.stringsFor
import com.ylowstone.tv.model.*
import com.ylowstone.tv.ui.components.MainTab
import com.ylowstone.tv.ui.components.MorphingLiquidGlassNavbar
import com.ylowstone.tv.ui.components.ProfileQuickDropdown
import com.ylowstone.tv.ui.screens.*
import com.ylowstone.tv.ui.theme.LocalYlowColors
import com.ylowstone.tv.ui.theme.YlowstoneTheme
import kotlinx.coroutines.launch

private enum class AppStage {
    SPLASH,
    MODE_SELECT,
    PLAYLISTS,
    SETTINGS,
    WORKSPACE
}

@OptIn(UnstableApi::class)
@Composable
fun YlowstoneApp(
    prefs: AppPrefs,
    repo: IptvRepository,
    isInPipMode: Boolean,
    pipCommandNonce: Int,
    pipCommandAction: String,
    onRequestLandscape: (Boolean) -> Unit,
    onRequestImmersive: (Boolean) -> Unit,
    onPipStateChanged: (eligible: Boolean, playing: Boolean) -> Unit,
    onEnterSystemPip: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(prefs.loadSettings()) }
    val strings = remember(settings.language) { stringsFor(settings.language) }
    val isTvMode = settings.deviceMode == DeviceMode.TV

    var stage by remember { mutableStateOf(AppStage.SPLASH) }
    var returnStageAfterSettings by remember { mutableStateOf(AppStage.WORKSPACE) }
    var playlists by remember { mutableStateOf(repo.db.getAllPlaylists()) }
    var activePlaylist by remember { mutableStateOf<PlaylistProfile?>(null) }
    var activeCounts by remember { mutableStateOf<Map<MediaType, Int>>(emptyMap()) }
    var activeTab by remember { mutableStateOf(MainTab.HOME) }
    var lockedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
    var homeDataVersion by remember { mutableIntStateOf(0) }

    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }

    // Top-right Quick Profile Dropdown & Quick Update Check Dialog
    var showProfileDropdown by remember { mutableStateOf(false) }
    var quickUpdateDialogInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    // Scroll-driven Morphing Navbar state (BitChord style!)
    var isNavbarCompact by remember { mutableStateOf(false) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -14f) isNavbarCompact = true
                else if (available.y > 14f) isNavbarCompact = false
                return Offset.Zero
            }
        }
    }

    // Shared Persistent Media3 Player
    var activeChannel by remember { mutableStateOf<ChannelItem?>(null) }
    var activeQueue by remember { mutableStateOf<List<ChannelItem>>(emptyList()) }
    var playerMode by remember { mutableStateOf(PlayerMode.NONE) }
    var isLandscapeFullscreen by remember { mutableStateOf(false) }

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var tracksVersion by remember { mutableIntStateOf(0) }

    val sharedPlayer = remember {
        val dsFactory = OkHttpDataSource.Factory(repo.httpClient).setUserAgent("YlowstoneTV/2.0")
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dsFactory))
            .build()
    }

    DisposableEffect(sharedPlayer) {
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
        sharedPlayer.addListener(listener)
        onDispose {
            sharedPlayer.removeListener(listener)
            sharedPlayer.release()
        }
    }

    fun playStream(ch: ChannelItem, queue: List<ChannelItem>) {
        val sameStream = activeChannel?.streamUrl == ch.streamUrl
        activeChannel = ch
        activeQueue = queue.ifEmpty { listOf(ch) }
        if (ch.id > 0L) {
            repo.db.markWatched(ch.id)
            homeDataVersion++
        }
        if (!sameStream) {
            sharedPlayer.setMediaItem(MediaItem.fromUri(ch.streamUrl))
            sharedPlayer.prepare()
            sharedPlayer.playWhenReady = true
        } else if (!sharedPlayer.isPlaying) {
            sharedPlayer.play()
        }
    }

    fun playRelativeChannel(delta: Int) {
        val cur = activeChannel ?: return
        if (activeQueue.size <= 1) return
        val idx = activeQueue.indexOfFirst { it.id == cur.id && it.streamUrl == cur.streamUrl }.coerceAtLeast(0)
        val nextIdx = (idx + delta + activeQueue.size) % activeQueue.size
        playStream(activeQueue[nextIdx], activeQueue)
    }

    fun stopPlaybackCompletely() {
        sharedPlayer.stop()
        sharedPlayer.clearMediaItems()
        activeChannel = null
        playerMode = PlayerMode.NONE
        isLandscapeFullscreen = false
    }

    fun launchPlayer(ch: ChannelItem, queue: List<ChannelItem>, enterLandscape: Boolean) {
        if (settings.preferExternalPlayer) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(ch.streamUrl), "video/*")
            }
            val launched = runCatching { context.startActivity(Intent.createChooser(intent, strings.externalPlayer)) }.isSuccess
            if (launched) return
        }
        playStream(ch, queue)
        playerMode = PlayerMode.FULLSCREEN
        isLandscapeFullscreen = isTvMode || enterLandscape
    }

    // Handle System PiP RemoteAction commands (Prev / Play-Pause / Next)
    LaunchedEffect(pipCommandNonce) {
        if (pipCommandNonce == 0) return@LaunchedEffect
        when (pipCommandAction) {
            "PREV" -> playRelativeChannel(-1)
            "NEXT" -> playRelativeChannel(1)
            "TOGGLE" -> if (sharedPlayer.isPlaying) sharedPlayer.pause() else sharedPlayer.play()
        }
    }

    // Manual Fullscreen Orientation Rule
    LaunchedEffect(isTvMode, playerMode, isLandscapeFullscreen, isInPipMode) {
        val wantLandscape = when {
            isInPipMode -> false
            isTvMode -> true
            playerMode == PlayerMode.FULLSCREEN && isLandscapeFullscreen -> true
            else -> false
        }
        onRequestLandscape(wantLandscape)
        onRequestImmersive(playerMode == PlayerMode.FULLSCREEN && isLandscapeFullscreen && !isInPipMode)
    }

    // Sync System PiP eligibility + playing state for RemoteActions
    LaunchedEffect(activeChannel, isPlaying) {
        onPipStateChanged(activeChannel != null, isPlaying)
    }

    fun openWorkspace(profile: PlaylistProfile) {
        activePlaylist = profile
        activeCounts = repo.db.getCountsByMediaType(profile.id)
        lockedCategories = prefs.getLockedCategories(profile.id)
        activeTab = MainTab.HOME
        isNavbarCompact = false
        stage = AppStage.WORKSPACE
        homeDataVersion++
    }

    fun runQuickUpdateCheck() {
        Toast.makeText(context, strings.checkingUpdate, Toast.LENGTH_SHORT).show()
        scope.launch {
            repo.checkGitHubUpdate()
                .onSuccess { info ->
                    if (info.hasUpdate) quickUpdateDialogInfo = info
                    else Toast.makeText(context, strings.upToDate, Toast.LENGTH_SHORT).show()
                }
                .onFailure { err ->
                    Toast.makeText(context, "Error: ${err.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    fun syncAndEnter(profile: PlaylistProfile, localUri: Uri? = null, enterAfter: Boolean = true) {
        isLoading = true
        loadingMessage = strings.loadingPlaylist
        scope.launch {
            val res = repo.syncPlaylist(profile, settings.streamFormat, localUri) { msg ->
                loadingMessage = msg
            }
            isLoading = false
            playlists = repo.db.getAllPlaylists()
            res.onSuccess { count ->
                Toast.makeText(context, "${strings.syncComplete} ($count)", Toast.LENGTH_SHORT).show()
                val fresh = playlists.firstOrNull { it.id == profile.id } ?: profile
                if (enterAfter) {
                    openWorkspace(fresh)
                } else if (activePlaylist?.id == fresh.id) {
                    activePlaylist = fresh
                    activeCounts = repo.db.getCountsByMediaType(fresh.id)
                    homeDataVersion++
                }
            }.onFailure { err ->
                Toast.makeText(context, "${strings.syncFailed}: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val allLockedMap = remember(playlists, lockedCategories, homeDataVersion) {
        prefs.getAllLockedMap(playlists.map { it.id })
    }
    val homeFavourites = remember(allLockedMap, homeDataVersion) {
        repo.db.getAllFavoritesAcrossPlaylists(allLockedMap, limit = 60)
    }
    val homeHistory = remember(allLockedMap, homeDataVersion) {
        repo.db.getRecentHistoryAcrossPlaylists(allLockedMap, limit = 30)
    }

    BackHandler(enabled = playerMode == PlayerMode.FULLSCREEN || (stage != AppStage.SPLASH && stage != AppStage.PLAYLISTS && stage != AppStage.MODE_SELECT)) {
        when {
            playerMode == PlayerMode.FULLSCREEN -> {
                playerMode = PlayerMode.FLOATING
                isLandscapeFullscreen = false
            }
            stage == AppStage.SETTINGS -> stage = returnStageAfterSettings
            stage == AppStage.WORKSPACE -> {
                if (activeTab != MainTab.HOME) {
                    if (activeTab == MainTab.LIVE && activeChannel != null && playerMode == PlayerMode.NONE && isPlaying) {
                        playerMode = PlayerMode.FLOATING
                    }
                    activeTab = MainTab.HOME
                    isNavbarCompact = false
                } else {
                    if (activeChannel != null && playerMode == PlayerMode.NONE && isPlaying) {
                        playerMode = PlayerMode.FLOATING
                    }
                    stage = AppStage.PLAYLISTS
                }
            }
        }
    }

    YlowstoneTheme(themeMode = settings.themeMode, stylePreset = settings.stylePreset) {
        if (isInPipMode && activeChannel != null) {
            PipVideoSurface(exoPlayer = sharedPlayer, isPlaying = isPlaying)
            return@YlowstoneTheme
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (stage) {
                AppStage.SPLASH -> {
                    SplashOverlayScreen {
                        stage = if (!settings.hasSelectedMode) {
                            AppStage.MODE_SELECT
                        } else if (playlists.isNotEmpty()) {
                            openWorkspace(playlists.first())
                            AppStage.WORKSPACE
                        } else {
                            AppStage.PLAYLISTS
                        }
                    }
                }

                AppStage.MODE_SELECT -> {
                    DeviceModeSelectionScreen(
                        currentMode = settings.deviceMode,
                        appIcon = settings.appIcon,
                        strings = strings,
                        onSelectMode = { chosen ->
                            val updated = settings.copy(deviceMode = chosen, hasSelectedMode = true)
                            settings = updated
                            prefs.saveSettings(updated)
                            stage = AppStage.PLAYLISTS
                        }
                    )
                }

                AppStage.PLAYLISTS -> {
                    PlaylistManagerScreen(
                        playlists = playlists,
                        streamFormat = settings.streamFormat,
                        strings = strings,
                        isTvMode = isTvMode,
                        isLoading = isLoading,
                        loadingMessage = loadingMessage,
                        onOpenPlaylist = { profile ->
                            val counts = repo.db.getCountsByMediaType(profile.id)
                            if (counts.values.sum() == 0) syncAndEnter(profile, null, enterAfter = true)
                            else openWorkspace(profile)
                        },
                        onSavePlaylist = { profile, uri ->
                            val savedId = repo.db.upsertPlaylist(profile)
                            val savedProfile = profile.copy(id = savedId)
                            playlists = repo.db.getAllPlaylists()
                            syncAndEnter(savedProfile, uri, enterAfter = true)
                        },
                        onRefreshPlaylist = { profile -> syncAndEnter(profile, null, enterAfter = false) },
                        onDeletePlaylist = { profile ->
                            repo.db.deletePlaylist(profile.id)
                            playlists = repo.db.getAllPlaylists()
                            if (activePlaylist?.id == profile.id) activePlaylist = playlists.firstOrNull()
                            homeDataVersion++
                        },
                        onOpenSettings = {
                            returnStageAfterSettings = AppStage.PLAYLISTS
                            showProfileDropdown = true
                        }
                    )
                }

                AppStage.SETTINGS -> {
                    SettingsScreen(
                        settings = settings,
                        strings = strings,
                        repo = repo,
                        onBack = { stage = returnStageAfterSettings },
                        onUpdateSettings = { updated ->
                            settings = updated
                            prefs.saveSettings(updated)
                        }
                    )
                }

                AppStage.WORKSPACE -> {
                    val pl = activePlaylist
                    if (pl == null) {
                        stage = AppStage.PLAYLISTS
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (activeTab) {
                                MainTab.HOME -> HomeScreen(
                                    activePlaylist = pl,
                                    allPlaylists = playlists,
                                    counts = activeCounts,
                                    favourites = homeFavourites,
                                    history = homeHistory,
                                    strings = strings,
                                    isTvMode = isTvMode,
                                    nestedScrollConnection = nestedScrollConnection,
                                    onSwitchPlaylist = { picked ->
                                        val counts = repo.db.getCountsByMediaType(picked.id)
                                        if (counts.values.sum() == 0) syncAndEnter(picked, null, enterAfter = true)
                                        else openWorkspace(picked)
                                    },
                                    onOpenPlaylistManager = {
                                        if (activeChannel != null && playerMode == PlayerMode.NONE && isPlaying) {
                                            playerMode = PlayerMode.FLOATING
                                        }
                                        stage = AppStage.PLAYLISTS
                                    },
                                    onOpenSection = { type ->
                                        isNavbarCompact = false
                                        activeTab = when (type) {
                                            MediaType.LIVE -> MainTab.LIVE
                                            MediaType.MOVIE -> MainTab.MOVIES
                                            MediaType.SERIES -> MainTab.SERIES
                                        }
                                    },
                                    onRefreshActive = { syncAndEnter(pl, null, enterAfter = false) },
                                    onPlayTaggedChannel = { tagged, list ->
                                        launchPlayer(tagged.channel, list.map { it.channel }, isTvMode)
                                    },
                                    onOpenProfileMenu = {
                                        returnStageAfterSettings = AppStage.WORKSPACE
                                        showProfileDropdown = true
                                    }
                                )

                                MainTab.LIVE -> BrowseScreen(
                                    playlist = pl,
                                    mediaType = MediaType.LIVE,
                                    repo = repo,
                                    sharedPlayer = sharedPlayer,
                                    activeChannel = activeChannel,
                                    playerMode = playerMode,
                                    lockedCategories = lockedCategories,
                                    pinCode = settings.pinCode,
                                    strings = strings,
                                    isTvMode = isTvMode,
                                    nestedScrollConnection = nestedScrollConnection,
                                    onToggleLockCategory = { cat ->
                                        val next = if (lockedCategories.contains(cat)) lockedCategories - cat else lockedCategories + cat
                                        lockedCategories = next
                                        prefs.setLockedCategories(pl.id, next)
                                        homeDataVersion++
                                    },
                                    onToggleFavorite = { ch ->
                                        repo.db.toggleFavorite(ch.id, !ch.isFavorite)
                                        homeDataVersion++
                                    },
                                    onSelectChannelInline = { ch, list ->
                                        playerMode = PlayerMode.NONE
                                        playStream(ch, list)
                                    },
                                    onLaunchPlayer = { ch, list, landscape -> launchPlayer(ch, list, landscape) },
                                    onFloatCurrent = { playerMode = PlayerMode.FLOATING },
                                    onOpenProfileMenu = {
                                        returnStageAfterSettings = AppStage.WORKSPACE
                                        showProfileDropdown = true
                                    }
                                )

                                MainTab.MOVIES -> BrowseScreen(
                                    playlist = pl,
                                    mediaType = MediaType.MOVIE,
                                    repo = repo,
                                    sharedPlayer = sharedPlayer,
                                    activeChannel = activeChannel,
                                    playerMode = playerMode,
                                    lockedCategories = lockedCategories,
                                    pinCode = settings.pinCode,
                                    strings = strings,
                                    isTvMode = isTvMode,
                                    nestedScrollConnection = nestedScrollConnection,
                                    onToggleLockCategory = { cat ->
                                        val next = if (lockedCategories.contains(cat)) lockedCategories - cat else lockedCategories + cat
                                        lockedCategories = next
                                        prefs.setLockedCategories(pl.id, next)
                                        homeDataVersion++
                                    },
                                    onToggleFavorite = { ch ->
                                        repo.db.toggleFavorite(ch.id, !ch.isFavorite)
                                        homeDataVersion++
                                    },
                                    onSelectChannelInline = { ch, list -> launchPlayer(ch, list, isTvMode) },
                                    onLaunchPlayer = { ch, list, landscape -> launchPlayer(ch, list, landscape) },
                                    onFloatCurrent = { playerMode = PlayerMode.FLOATING },
                                    onOpenProfileMenu = {
                                        returnStageAfterSettings = AppStage.WORKSPACE
                                        showProfileDropdown = true
                                    }
                                )

                                MainTab.SERIES -> BrowseScreen(
                                    playlist = pl,
                                    mediaType = MediaType.SERIES,
                                    repo = repo,
                                    sharedPlayer = sharedPlayer,
                                    activeChannel = activeChannel,
                                    playerMode = playerMode,
                                    lockedCategories = lockedCategories,
                                    pinCode = settings.pinCode,
                                    strings = strings,
                                    isTvMode = isTvMode,
                                    nestedScrollConnection = nestedScrollConnection,
                                    onToggleLockCategory = { cat ->
                                        val next = if (lockedCategories.contains(cat)) lockedCategories - cat else lockedCategories + cat
                                        lockedCategories = next
                                        prefs.setLockedCategories(pl.id, next)
                                        homeDataVersion++
                                    },
                                    onToggleFavorite = { ch ->
                                        repo.db.toggleFavorite(ch.id, !ch.isFavorite)
                                        homeDataVersion++
                                    },
                                    onSelectChannelInline = { ch, list -> launchPlayer(ch, list, isTvMode) },
                                    onLaunchPlayer = { ch, list, landscape -> launchPlayer(ch, list, landscape) },
                                    onFloatCurrent = { playerMode = PlayerMode.FLOATING },
                                    onOpenProfileMenu = {
                                        returnStageAfterSettings = AppStage.WORKSPACE
                                        showProfileDropdown = true
                                    }
                                )

                                MainTab.SEARCH -> SearchScreen(
                                    activePlaylist = pl,
                                    lockedByPlaylist = allLockedMap,
                                    repo = repo,
                                    strings = strings,
                                    isTvMode = isTvMode,
                                    nestedScrollConnection = nestedScrollConnection,
                                    onPlayTagged = { tagged, list ->
                                        launchPlayer(tagged.channel, list.map { it.channel }, isTvMode)
                                    },
                                    onToggleFavorite = { ch ->
                                        repo.db.toggleFavorite(ch.id, !ch.isFavorite)
                                        homeDataVersion++
                                    },
                                    onOpenProfileMenu = {
                                        returnStageAfterSettings = AppStage.WORKSPACE
                                        showProfileDropdown = true
                                    }
                                )
                            }

                            // Morphing Liquid Glass Bottom Navigation Bar!
                            MorphingLiquidGlassNavbar(
                                selectedTab = activeTab,
                                isCompact = isNavbarCompact,
                                activeChannel = activeChannel,
                                activePlaylistName = pl.name,
                                isPlaying = isPlaying,
                                strings = strings,
                                isTvMode = isTvMode,
                                onSelectTab = { nextTab ->
                                    if (activeTab == MainTab.LIVE && nextTab != MainTab.LIVE && activeChannel != null && playerMode == PlayerMode.NONE && isPlaying) {
                                        playerMode = PlayerMode.FLOATING
                                    }
                                    activeTab = nextTab
                                    isNavbarCompact = false
                                },
                                onTogglePlayPause = {
                                    if (sharedPlayer.isPlaying) sharedPlayer.pause() else sharedPlayer.play()
                                },
                                onOpenNowPlaying = {
                                    if (activeChannel != null) {
                                        playerMode = PlayerMode.FULLSCREEN
                                        isLandscapeFullscreen = isTvMode
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(bottom = 12.dp)
                            )
                        }
                    }
                }
            }

            // Top-Right Anchored Quick Profile Dropdown (BitChord position!)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .systemBarsPadding()
                    .padding(top = 54.dp, end = 16.dp)
            ) {
                ProfileQuickDropdown(
                    expanded = showProfileDropdown,
                    activePlaylistName = activePlaylist?.name,
                    deviceMode = settings.deviceMode,
                    strings = strings,
                    onDismiss = { showProfileDropdown = false },
                    onOpenSettings = {
                        if (stage != AppStage.SETTINGS) returnStageAfterSettings = stage
                        stage = AppStage.SETTINGS
                    },
                    onSwitchDeviceMode = { mode ->
                        val updated = settings.copy(deviceMode = mode, hasSelectedMode = true)
                        settings = updated
                        prefs.saveSettings(updated)
                    },
                    onManagePlaylists = {
                        if (activeChannel != null && playerMode == PlayerMode.NONE && isPlaying) {
                            playerMode = PlayerMode.FLOATING
                        }
                        stage = AppStage.PLAYLISTS
                    },
                    onCheckUpdates = { runQuickUpdateCheck() }
                )
            }

            // In-App Draggable Rounded Floating Player (Pure YouTube PiP!)
            if (playerMode == PlayerMode.FLOATING && activeChannel != null && stage != AppStage.SPLASH) {
                DraggableFloatingPlayer(
                    exoPlayer = sharedPlayer,
                    channel = activeChannel!!,
                    isPlaying = isPlaying,
                    onPrev = { playRelativeChannel(-1) },
                    onNext = { playRelativeChannel(1) },
                    onExpandFullscreen = {
                        playerMode = PlayerMode.FULLSCREEN
                        isLandscapeFullscreen = true
                    },
                    onEnterSystemPip = onEnterSystemPip,
                    onClose = { stopPlaybackCompletely() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 14.dp, bottom = 84.dp)
                )
            }

            // Fullscreen Player Overlay (Manual orientation + In-Landscape Search!)
            if (playerMode == PlayerMode.FULLSCREEN && activeChannel != null) {
                FullscreenPlayerScreen(
                    exoPlayer = sharedPlayer,
                    repo = repo,
                    lockedCategories = allLockedMap[activeChannel!!.playlistId] ?: emptySet(),
                    currentChannel = activeChannel!!,
                    channelList = activeQueue.ifEmpty { listOf(activeChannel!!) },
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    tracksVersion = tracksVersion,
                    isLandscapeFullscreen = isLandscapeFullscreen,
                    isTvMode = isTvMode,
                    showGestures = settings.showPlayerGestures,
                    strings = strings,
                    onSelectChannel = { nextCh -> playStream(nextCh, activeQueue) },
                    onToggleFullscreenOrientation = { isLandscapeFullscreen = !isLandscapeFullscreen },
                    onMinimizeToFloat = {
                        playerMode = PlayerMode.FLOATING
                        isLandscapeFullscreen = false
                    },
                    onEnterSystemPip = onEnterSystemPip,
                    onTracksModified = { tracksVersion++ }
                )
            }
        }

        // Quick Update Available Dialog (triggered from top-right Profile Dropdown)
        quickUpdateDialogInfo?.let { info ->
            val c = LocalYlowColors.current
            AlertDialog(
                onDismissRequest = { quickUpdateDialogInfo = null },
                containerColor = c.surface,
                title = { Text("${strings.updateAvailable} v${info.latestVersion}", color = c.textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = info.releaseNotes.ifBlank { "YlowstoneTV v${info.latestVersion} is ready to download." },
                        color = c.textSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val url = info.apkDownloadUrl.ifBlank { info.htmlUrl }
                            quickUpdateDialogInfo = null
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent)
                    ) {
                        Text(strings.downloadUpdateBrowser, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { quickUpdateDialogInfo = null }) {
                        Text(strings.cancel, color = c.textSecondary)
                    }
                }
            )
        }
    }
}
