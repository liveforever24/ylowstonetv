package com.ylowstone.tv.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    WORKSPACE,
    PLAYER
}

@Composable
fun YlowstoneApp(
    prefs: AppPrefs,
    repo: IptvRepository,
    onRequestLandscape: (Boolean) -> Unit,
    onRequestImmersive: (Boolean) -> Unit,
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

    var playingChannel by remember { mutableStateOf<ChannelItem?>(null) }
    var playingQueue by remember { mutableStateOf<List<ChannelItem>>(emptyList()) }

    // Smart Orientation Rule:
    // - TV mode -> Always Landscape
    // - Smartphone mode -> Portrait while setting up / picking playlist; Full Landscape once inside a playlist or player!
    LaunchedEffect(stage, settings.deviceMode) {
        val wantLandscape = when {
            isTvMode -> true
            stage == AppStage.WORKSPACE || stage == AppStage.PLAYER -> true
            else -> false
        }
        onRequestLandscape(wantLandscape)
        onRequestImmersive(stage == AppStage.PLAYER)
    }

    fun openWorkspace(profile: PlaylistProfile) {
        activePlaylist = profile
        activeCounts = repo.db.getCountsByMediaType(profile.id)
        lockedCategories = prefs.getLockedCategories(profile.id)
        activeTab = MainTab.HOME
        stage = AppStage.WORKSPACE
    }

    fun launchPlayback(ch: ChannelItem, queue: List<ChannelItem>) {
        if (settings.preferExternalPlayer) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(ch.streamUrl), "video/*")
            }
            val launched = runCatching { context.startActivity(Intent.createChooser(intent, strings.externalPlayer)) }.isSuccess
            if (launched) return
        }
        playingChannel = ch
        playingQueue = queue
        stage = AppStage.PLAYER
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

    BackHandler(enabled = stage != AppStage.SPLASH && stage != AppStage.PLAYLISTS && stage != AppStage.MODE_SELECT) {
        when (stage) {
            AppStage.PLAYER -> stage = AppStage.WORKSPACE
            AppStage.STANDALONE_SETTINGS -> stage = AppStage.PLAYLISTS
            AppStage.WORKSPACE -> {
                if (activeTab != MainTab.HOME) activeTab = MainTab.HOME
                else stage = AppStage.PLAYLISTS
            }
            else -> {}
        }
    }

    YlowstoneTheme(themeMode = settings.themeMode, accent = settings.accent) {
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
                                onSwitchPlaylist = { stage = AppStage.PLAYLISTS },
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
                                onPlayChannel = { ch -> launchPlayback(ch, listOf(ch)) }
                            )

                            MainTab.LIVE -> BrowseScreen(
                                playlist = pl,
                                mediaType = MediaType.LIVE,
                                repo = repo,
                                lockedCategories = lockedCategories,
                                pinCode = settings.pinCode,
                                strings = strings,
                                isTvMode = isTvMode,
                                onToggleLockCategory = { cat ->
                                    val next = if (lockedCategories.contains(cat)) lockedCategories - cat else lockedCategories + cat
                                    lockedCategories = next
                                    prefs.setLockedCategories(pl.id, next)
                                },
                                onLaunchFullscreen = { ch, list -> launchPlayback(ch, list) }
                            )

                            MainTab.MOVIES -> BrowseScreen(
                                playlist = pl,
                                mediaType = MediaType.MOVIE,
                                repo = repo,
                                lockedCategories = lockedCategories,
                                pinCode = settings.pinCode,
                                strings = strings,
                                isTvMode = isTvMode,
                                onToggleLockCategory = { cat ->
                                    val next = if (lockedCategories.contains(cat)) lockedCategories - cat else lockedCategories + cat
                                    lockedCategories = next
                                    prefs.setLockedCategories(pl.id, next)
                                },
                                onLaunchFullscreen = { ch, list -> launchPlayback(ch, list) }
                            )

                            MainTab.SERIES -> BrowseScreen(
                                playlist = pl,
                                mediaType = MediaType.SERIES,
                                repo = repo,
                                lockedCategories = lockedCategories,
                                pinCode = settings.pinCode,
                                strings = strings,
                                isTvMode = isTvMode,
                                onToggleLockCategory = { cat ->
                                    val next = if (lockedCategories.contains(cat)) lockedCategories - cat else lockedCategories + cat
                                    lockedCategories = next
                                    prefs.setLockedCategories(pl.id, next)
                                },
                                onLaunchFullscreen = { ch, list -> launchPlayback(ch, list) }
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

                        // Floating Translucent Glass Navbar (Twitter/Instagram/Apple Music vibe)
                        FloatingGlassNavbar(
                            selectedTab = activeTab,
                            onSelect = { activeTab = it },
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

            AppStage.PLAYER -> {
                val ch = playingChannel
                val pl = activePlaylist
                if (ch == null) {
                    stage = AppStage.WORKSPACE
                } else {
                    FullscreenPlayerScreen(
                        initialChannel = ch,
                        channelList = playingQueue.ifEmpty { listOf(ch) },
                        userAgent = pl?.userAgent ?: "",
                        httpClient = repo.httpClient,
                        showGestures = settings.showPlayerGestures,
                        strings = strings,
                        onChannelChanged = { watched ->
                            if (watched.id > 0L) repo.db.markWatched(watched.id)
                        },
                        onBack = { stage = AppStage.WORKSPACE }
                    )
                }
            }
        }
    }
}
