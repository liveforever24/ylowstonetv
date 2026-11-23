package com.ylowstone.tv.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.ylowstone.tv.ui.components.FloatingGlassNavbar
import com.ylowstone.tv.ui.components.MainTab
import com.ylowstone.tv.ui.screens.*
import com.ylowstone.tv.ui.theme.YlowstoneTheme
import kotlinx.coroutines.launch
import java.io.File

private enum class AppStage {
    SPLASH,
    MODE_SELECT,
    PLAYLISTS,
    STANDALONE_SETTINGS,
    WORKSPACE
}

@OptIn(UnstableApi::class)
@Composable
fun YlowstoneApp(
    prefs: AppPrefs,
    repo: IptvRepository,
    isInPipMode: Boolean,
    onRequestLandscape: (Boolean) -> Unit,
    onRequestImmersive: (Boolean) -> Unit,
    onPipEligibilityChanged: (Boolean) -> Unit,
    onEnterSystemPip: () -> Unit,
    onInstallApk: (File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(prefs.loadSettings()) }
    val strings = remember(settings.language) { stringsFor(settings.language) }
    val isTvMode = settings.deviceMode == DeviceMode.TV

    var stage by remember { mutableStateOf(AppStage.SPLASH) }
    var playlists by remember { mutableStateOf(repo.db.getAllPlaylists()) }
    var activePlaylist by remember { mutableStateOf<PlaylistProfile?>(null) }
    var activeCounts by remember { mutableStateOf<Map<MediaType, Int>>(emptyMap()) }
    var activeTab by remember { mutableStateOf(MainTab.HOME) }
    var lockedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }

    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }

    // Shared Persistent Media3 Player State (zero-reload Fullscreen <-> Floating <-> System PiP!)
    var activeChannel by remember { mutableStateOf<ChannelItem?>(null) }
    var activeQueue by remember { mutableStateOf<List<ChannelItem>>(emptyList()) }
    var playerMode by remember { mutableStateOf(PlayerMode.NONE) }
    var isLandscapeFullscreen by remember { mutableStateOf(false) }

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var tracksVersion by remember { mutableIntStateOf(0) }

    val sharedPlayer = remember {
        val dsFactory = OkHttpDataSource.Factory(repo.httpClient).setUserAgent("YlowstoneTV/1.0")
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
        if (ch.id > 0L) repo.db.markWatched(ch.id)
        if (!sameStream) {
            sharedPlayer.setMediaItem(MediaItem.fromUri(ch.streamUrl))
            sharedPlayer.prepare()
            sharedPlayer.playWhenReady = true
        } else if (!sharedPlayer.isPlaying) {
            sharedPlayer.play()
        }
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

    // Orientation Rule (Manual Fullscreen!):
    // - TV mode -> Always Landscape
    // - Smartphone mode -> Portrait everywhere, Landscape ONLY when user taps Fullscreen!
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

    // System PiP Eligibility: whenever a channel is active (Fullscreen OR Floating OR Inline Preview)
    LaunchedEffect(activeChannel, isPlaying) {
        onPipEligibilityChanged(activeChannel != null && isPlaying)
    }

    fun openWorkspace(profile: PlaylistProfile) {
        activePlaylist = profile
        activeCounts = repo.db.getCountsByMediaType(profile.id)
        lockedCategories = prefs.getLockedCategories(profile.id)
        activeTab = MainTab.HOME
        stage = AppStage.WORKSPACE
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
                }
            }.onFailure { err ->
                Toast.makeText(context, "${strings.syncFailed}: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    BackHandler(enabled = playerMode == PlayerMode.FULLSCREEN || (stage != AppStage.SPLASH && stage != AppStage.PLAYLISTS && stage != AppStage.MODE_SELECT)) {
        when {
            playerMode == PlayerMode.FULLSCREEN -> {
                // Shrink smoothly into Rounded Floating Mini-Player instead of killing playback!
                playerMode = PlayerMode.FLOATING
                isLandscapeFullscreen = false
            }
            stage == AppStage.STANDALONE_SETTINGS -> stage = AppStage.PLAYLISTS
            stage == AppStage.WORKSPACE -> {
                if (activeTab != MainTab.HOME) {
                    // If watching inline Live TV preview and leaving Live tab, float it automatically!
                    if (activeTab == MainTab.LIVE && activeChannel != null && playerMode == PlayerMode.NONE && isPlaying) {
                        playerMode = PlayerMode.FLOATING
                    }
                    activeTab = MainTab.HOME
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
        // When Android System Picture-in-Picture is active -> clean full-bleed rounded video only!
        if (isInPipMode && activeChannel != null) {
            PipVideoSurface(exoPlayer = sharedPlayer)
            return@YlowstoneTheme
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (stage) {
                AppStage.SPLASH -> {
                    SplashOverlayScreen {
                        stage = if (settings.hasSelectedMode) AppStage.PLAYLISTS else AppStage.MODE_SELECT
                    }
                }

                AppStage.MODE_SELECT -> {
                    DeviceModeSelectionScreen(
                        currentMode = settings.deviceMode,
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
                            val total = counts.values.sum()
                            if (total == 0) {
                                syncAndEnter(profile, null, enterAfter = true)
                            } else {
                                openWorkspace(profile)
                            }
                        },
                        onSavePlaylist = { profile, uri ->
                            val savedId = repo.db.upsertPlaylist(profile)
                            val savedProfile = profile.copy(id = savedId)
                            playlists = repo.db.getAllPlaylists()
                            syncAndEnter(savedProfile, uri, enterAfter = true)
                        },
                        onRefreshPlaylist = { profile ->
                            syncAndEnter(profile, null, enterAfter = false)
                        },
                        onDeletePlaylist = { profile ->
                            repo.db.deletePlaylist(profile.id)
                            playlists = repo.db.getAllPlaylists()
                        },
                        onOpenSettings = { stage = AppStage.STANDALONE_SETTINGS }
                    )
                }

                AppStage.STANDALONE_SETTINGS -> {
                    SettingsScreen(
                        settings = settings,
                        strings = strings,
                        repo = repo,
                        showBackButton = true,
                        onBack = { stage = AppStage.PLAYLISTS },
                        onUpdateSettings = { updated ->
                            settings = updated
                            prefs.saveSettings(updated)
                        },
                        onInstallApk = onInstallApk
                    )
                }

                AppStage.WORKSPACE -> {
                    val pl = activePlaylist
                    if (pl == null) {
                        stage = AppStage.PLAYLISTS
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (activeTab) {
                                MainTab.HOME -> DashboardScreen(
                                    playlist = pl,
                                    counts = activeCounts,
                                    strings = strings,
                                    isTvMode = isTvMode,
                                    use24Hour = settings.use24HourClock,
                                    onOpenSection = { type ->
                                        activeTab = when (type) {
                                            MediaType.LIVE -> MainTab.LIVE
                                            MediaType.MOVIE -> MainTab.MOVIES
                                            MediaType.SERIES -> MainTab.SERIES
                                        }
                                    },
                                    onRefresh = { syncAndEnter(pl, null, enterAfter = false) },
                                    onSwitchPlaylist = {
                                        if (activeChannel != null && playerMode == PlayerMode.NONE && isPlaying) {
                                            playerMode = PlayerMode.FLOATING
                                        }
                                        stage = AppStage.PLAYLISTS
                                    },
                                    onSearchQuery = { q ->
                                        repo.db.queryChannels(
                                            playlistId = pl.id,
                                            mediaType = null,
                                            specialFilter = "ALL",
                                            searchQuery = q,
                                            lockedCategories = lockedCategories,
                                            limit = 80
                                        )
                                    },
                                    onPlayChannel = { ch -> launchPlayer(ch, listOf(ch), isTvMode) }
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
                                    onToggleLockCategory = { cat ->
                                        val next = if (lockedCategories.contains(cat)) lockedCategories - cat else lockedCategories + cat
                                        lockedCategories = next
                                        prefs.setLockedCategories(pl.id, next)
                                    },
                                    onSelectChannelInline = { ch, list ->
                                        playerMode = PlayerMode.NONE
                                        playStream(ch, list)
                                    },
                                    onLaunchPlayer = { ch, list, landscape -> launchPlayer(ch, list, landscape) },
                                    onFloatCurrent = { playerMode = PlayerMode.FLOATING }
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
                                    onToggleLockCategory = { cat ->
                                        val next = if (lockedCategories.contains(cat)) lockedCategories - cat else lockedCategories + cat
                                        lockedCategories = next
                                        prefs.setLockedCategories(pl.id, next)
                                    },
                                    onSelectChannelInline = { ch, list -> launchPlayer(ch, list, isTvMode) },
                                    onLaunchPlayer = { ch, list, landscape -> launchPlayer(ch, list, landscape) },
                                    onFloatCurrent = { playerMode = PlayerMode.FLOATING }
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
                                    onToggleLockCategory = { cat ->
                                        val next = if (lockedCategories.contains(cat)) lockedCategories - cat else lockedCategories + cat
                                        lockedCategories = next
                                        prefs.setLockedCategories(pl.id, next)
                                    },
                                    onSelectChannelInline = { ch, list -> launchPlayer(ch, list, isTvMode) },
                                    onLaunchPlayer = { ch, list, landscape -> launchPlayer(ch, list, landscape) },
                                    onFloatCurrent = { playerMode = PlayerMode.FLOATING }
                                )

                                MainTab.SETTINGS -> SettingsScreen(
                                    settings = settings,
                                    strings = strings,
                                    repo = repo,
                                    showBackButton = false,
                                    onBack = { activeTab = MainTab.HOME },
                                    onUpdateSettings = { updated ->
                                        settings = updated
                                        prefs.saveSettings(updated)
                                    },
                                    onInstallApk = onInstallApk
                                )
                            }

                            // Floating Translucent Glass Navbar
                            FloatingGlassNavbar(
                                selectedTab = activeTab,
                                onSelect = { nextTab ->
                                    if (activeTab == MainTab.LIVE && nextTab != MainTab.LIVE && activeChannel != null && playerMode == PlayerMode.NONE && isPlaying) {
                                        playerMode = PlayerMode.FLOATING
                                    }
                                    activeTab = nextTab
                                },
                                strings = strings,
                                isTvMode = isTvMode,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(bottom = 10.dp)
                            )
                        }
                    }
                }
            }

            // In-App Draggable Rounded Floating Mini-Player (works across Home, Playlists, Settings, Browse!)
            if (playerMode == PlayerMode.FLOATING && activeChannel != null && stage != AppStage.SPLASH) {
                DraggableFloatingPlayer(
                    exoPlayer = sharedPlayer,
                    channel = activeChannel!!,
                    isPlaying = isPlaying,
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

            // Fullscreen Player Overlay (shared ExoPlayer, zero reload!)
            if (playerMode == PlayerMode.FULLSCREEN && activeChannel != null) {
                FullscreenPlayerScreen(
                    exoPlayer = sharedPlayer,
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
    }
}
