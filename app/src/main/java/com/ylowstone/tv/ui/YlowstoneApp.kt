package com.ylowstone.tv.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.ylowstone.tv.data.AppPrefs
import com.ylowstone.tv.data.ActiveSubtitleSession
import com.ylowstone.tv.data.IptvRepository
import com.ylowstone.tv.i18n.stringsFor
import com.ylowstone.tv.model.*
import com.ylowstone.tv.ui.components.FotMobBottomNavbar
import com.ylowstone.tv.ui.components.MainTab
import com.ylowstone.tv.ui.components.ProfileQuickDropdown
import com.ylowstone.tv.ui.screens.*
import com.ylowstone.tv.ui.theme.LocalYlowColors
import com.ylowstone.tv.ui.theme.YlowstoneTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class AppStage {
    SPLASH,
    WELCOME_QUOTE,
    ONBOARDING_GUIDE,
    MODE_SELECT,
    ADMIN_LOGIN,
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
    onEnterSystemPip: () -> Unit,
    onExitApp: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(prefs.loadSettings()) }
    val strings = remember(settings.language) { stringsFor(settings.language) }
    val isTvMode = settings.deviceMode == DeviceMode.TV

    var stage by remember { mutableStateOf(AppStage.SPLASH) }
    var returnStageAfterSettings by remember { mutableStateOf(AppStage.WORKSPACE) }
    var returnStageAfterLogin by remember { mutableStateOf<AppStage?>(null) }

    var isAdminLoggedIn by remember { mutableStateOf(prefs.isAdminLoggedIn()) }
    var adminPlaylistIds by remember { mutableStateOf(prefs.getAdminPlaylistIds()) }
    var isVerifyingLogin by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf("") }

    var playlists by remember { mutableStateOf(repo.db.getAllPlaylists()) }
    var activePlaylist by remember { mutableStateOf<PlaylistProfile?>(null) }
    var activeCounts by remember { mutableStateOf<Map<MediaType, Int>>(emptyMap()) }
    var activeTab by remember { mutableStateOf(MainTab.HOME) }
    var lockedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
    var homeDataVersion by remember { mutableIntStateOf(0) }

    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }

    var showProfileDropdown by remember { mutableStateOf(false) }
    var quickUpdateDialogInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    // Shared Persistent Media3 Player
    var activeChannel by remember { mutableStateOf<ChannelItem?>(null) }
    var activeQueue by remember { mutableStateOf<List<ChannelItem>>(emptyList()) }
    var playerMode by remember { mutableStateOf(PlayerMode.NONE) }
    var isLandscapeFullscreen by remember { mutableStateOf(false) }

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var tracksVersion by remember { mutableIntStateOf(0) }
    var subtitleSession by remember { mutableStateOf(ActiveSubtitleSession()) }

    val sharedPlayer = remember {
        val dsFactory = OkHttpDataSource.Factory(repo.httpClient).setUserAgent("YlowstoneTV/2.2.7")
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dsFactory))
            .build()
            .apply {
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setPreferredAudioLanguages("id", "in", "en")
                    .setPreferredTextLanguages("id", "in", "en")
                    .build()
            }
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

    fun saveCurrentVodProgress(refreshHome: Boolean = false) {
        val cur = activeChannel ?: return
        if (cur.mediaType == MediaType.MOVIE || cur.mediaType == MediaType.SERIES) {
            val pos = sharedPlayer.currentPosition.coerceAtLeast(0L)
            val dur = sharedPlayer.duration.coerceAtLeast(0L)
            if (pos > 0L) {
                repo.db.saveWatchProgress(cur.streamUrl, pos, dur)
                if (refreshHome) homeDataVersion++
            }
        }
    }

    fun formatResumeMs(ms: Long): String {
        val sec = (ms / 1000L).coerceAtLeast(0L)
        val h = sec / 3600L
        val m = (sec % 3600L) / 60L
        val s = sec % 60L
        return if (h > 0L) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(java.util.Locale.US, "%02d:%02d", m, s)
    }

    // Periodic watch progress saver for Movies & Series (every 3s while playing, plus immediately on pause)
    LaunchedEffect(activeChannel?.streamUrl, isPlaying) {
        val cur = activeChannel ?: return@LaunchedEffect
        val isVod = cur.mediaType == MediaType.MOVIE || cur.mediaType == MediaType.SERIES
        if (!isVod) return@LaunchedEffect
        if (!isPlaying) {
            saveCurrentVodProgress(refreshHome = true)
            return@LaunchedEffect
        }
        while (isPlaying) {
            delay(3000L)
            saveCurrentVodProgress(refreshHome = false)
        }
    }

    fun playStream(ch: ChannelItem, queue: List<ChannelItem>) {
        val sameStream = activeChannel?.streamUrl == ch.streamUrl
        if (!sameStream) {
            saveCurrentVodProgress(refreshHome = false)
            if (subtitleSession.streamUrl != ch.streamUrl) {
                subtitleSession = ActiveSubtitleSession(streamUrl = ch.streamUrl)
            }
        }
        activeChannel = ch
        activeQueue = queue.ifEmpty { listOf(ch) }
        val plName = playlists.firstOrNull { it.id == ch.playlistId }?.name ?: activePlaylist?.name ?: "YlowstoneTV"
        repo.db.recordWatchHistory(ch, plName)
        homeDataVersion++

        if (!sameStream) {
            val isVod = ch.mediaType == MediaType.MOVIE || ch.mediaType == MediaType.SERIES
            val resumeMs = if (isVod) {
                val saved = repo.db.getWatchProgress(ch.streamUrl)
                if (saved != null && saved.first > 5_000L && (saved.second <= 0L || saved.first < (saved.second * 95L) / 100L)) {
                    saved.first
                } else 0L
            } else 0L

            if (resumeMs > 0L) {
                sharedPlayer.setMediaItem(MediaItem.fromUri(ch.streamUrl), resumeMs)
                Toast.makeText(context, "Melanjutkan dari ${formatResumeMs(resumeMs)}", Toast.LENGTH_SHORT).show()
            } else {
                sharedPlayer.setMediaItem(MediaItem.fromUri(ch.streamUrl))
            }
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
        saveCurrentVodProgress(refreshHome = true)
        sharedPlayer.stop()
        sharedPlayer.clearMediaItems()
        activeChannel = null
        subtitleSession = ActiveSubtitleSession()
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

    LaunchedEffect(pipCommandNonce) {
        if (pipCommandNonce == 0) return@LaunchedEffect
        when (pipCommandAction) {
            "PREV" -> playRelativeChannel(-1)
            "NEXT" -> playRelativeChannel(1)
            "TOGGLE" -> if (sharedPlayer.isPlaying) sharedPlayer.pause() else sharedPlayer.play()
        }
    }

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

    LaunchedEffect(activeChannel, isPlaying) {
        onPipStateChanged(activeChannel != null, isPlaying)
    }

    fun openWorkspace(profile: PlaylistProfile) {
        activePlaylist = profile
        activeCounts = repo.db.getCountsByMediaType(profile.id)
        lockedCategories = prefs.getLockedCategories(profile.id)
        activeTab = MainTab.HOME
        stage = AppStage.WORKSPACE
        homeDataVersion++
    }

    fun hasDefaultServerPlaylist(list: List<PlaylistProfile>, serverIds: Set<Long>): Boolean {
        return list.any { p ->
            serverIds.contains(p.id) ||
                p.name.equals("US/UK/ID", ignoreCase = true) ||
                p.name.equals("USA", ignoreCase = true)
        }
    }

    fun proceedToAppOrLogin() {
        val currentList = repo.db.getAllPlaylists()
        playlists = currentList
        val hasDefault = hasDefaultServerPlaylist(currentList, adminPlaylistIds)
        if (!hasDefault && !prefs.hasPassedLoginGate()) {
            returnStageAfterLogin = null
            loginError = ""
            stage = AppStage.ADMIN_LOGIN
        } else if (currentList.isNotEmpty()) {
            openWorkspace(currentList.first())
        } else {
            returnStageAfterLogin = null
            loginError = ""
            stage = AppStage.ADMIN_LOGIN
        }
    }

    fun proceedAfterWelcome() {
        if (!settings.hasSelectedMode) {
            stage = AppStage.MODE_SELECT
        } else {
            proceedToAppOrLogin()
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
                } else {
                    if (activePlaylist?.id == fresh.id) {
                        activePlaylist = fresh
                        activeCounts = repo.db.getCountsByMediaType(fresh.id)
                    }
                    homeDataVersion++
                }
            }.onFailure { err ->
                Toast.makeText(context, "${strings.syncFailed}: ${err.message}", Toast.LENGTH_LONG).show()
                if (enterAfter) {
                    val fresh = playlists.firstOrNull { it.id == profile.id } ?: profile
                    openWorkspace(fresh)
                }
            }
        }
    }

    // Apply server playlists from Vercel & auto-sync primary playlist
    fun applyAdminPlaylists(remotePlaylists: List<PlaylistProfile>, pin: String) {
        prefs.setAdminSession(true, pin)
        prefs.setPassedLoginGate(true)
        isAdminLoggedIn = true

        val currentAll = repo.db.getAllPlaylists()
        val oldAdminIds = prefs.getAdminPlaylistIds()
        val newAdminIds = mutableSetOf<Long>()
        val savedProfiles = mutableListOf<PlaylistProfile>()

        remotePlaylists.forEachIndexed { idx, remote ->
            val existingMatch = currentAll.firstOrNull {
                (oldAdminIds.contains(it.id) && it.name.equals(remote.name, ignoreCase = true)) ||
                    (it.url.equals(remote.url, ignoreCase = true) && it.username == remote.username && it.type == remote.type)
            }
            val toSave = remote.copy(
                id = existingMatch?.id ?: 0L,
                colorIndex = idx % 6,
                lastUpdated = System.currentTimeMillis()
            )
            val savedId = repo.db.upsertPlaylist(toSave)
            newAdminIds.add(savedId)
            savedProfiles.add(toSave.copy(id = savedId))
        }

        // Remove stale old admin playlists that were deleted from Vercel
        for (oldId in oldAdminIds) {
            if (!newAdminIds.contains(oldId)) {
                repo.db.deletePlaylist(oldId)
            }
        }

        prefs.setAdminPlaylistIds(newAdminIds)
        adminPlaylistIds = newAdminIds
        playlists = repo.db.getAllPlaylists()
        homeDataVersion++
        returnStageAfterLogin = null

        val primary = savedProfiles.firstOrNull() ?: playlists.firstOrNull()
        if (primary != null) {
            syncAndEnter(primary, null, enterAfter = true)
        } else {
            stage = AppStage.PLAYLISTS
        }
    }

    fun performAdminLogin(pin: String) {
        isVerifyingLogin = true
        loginError = ""
        scope.launch {
            val res = repo.loginAdmin(pin)
            isVerifyingLogin = false
            res.onSuccess { auth ->
                Toast.makeText(context, strings.adminLoginSuccess, Toast.LENGTH_SHORT).show()
                applyAdminPlaylists(auth.playlists, pin)
            }.onFailure { err ->
                loginError = err.message ?: strings.wrongPin
            }
        }
    }

    fun performAdminLogout() {
        stopPlaybackCompletely()
        val idsToDelete = prefs.getAdminPlaylistIds()
        for (id in idsToDelete) {
            repo.db.deletePlaylist(id)
        }
        prefs.setAdminPlaylistIds(emptySet())
        prefs.setAdminSession(false, "")
        prefs.setPassedLoginGate(false)
        adminPlaylistIds = emptySet()
        isAdminLoggedIn = false
        playlists = repo.db.getAllPlaylists()
        if (activePlaylist != null && idsToDelete.contains(activePlaylist!!.id)) {
            activePlaylist = playlists.firstOrNull()
        }
        homeDataVersion++
        returnStageAfterLogin = null
        loginError = ""
        Toast.makeText(context, strings.adminLogoutDone, Toast.LENGTH_SHORT).show()
        stage = AppStage.ADMIN_LOGIN
    }

    fun syncAdminFromServer() {
        val savedPin = prefs.getAdminPin()
        if (savedPin.isBlank()) {
            returnStageAfterLogin = stage
            loginError = ""
            stage = AppStage.ADMIN_LOGIN
            return
        }
        isLoading = true
        loadingMessage = strings.adminSyncing
        scope.launch {
            val res = repo.loginAdmin(savedPin)
            res.onSuccess { auth ->
                applyAdminPlaylists(auth.playlists, savedPin)
            }.onFailure { err ->
                isLoading = false
                val msg = err.message.orEmpty()
                if (msg.contains("salah", ignoreCase = true) || msg.contains("401")) {
                    Toast.makeText(context, strings.adminSessionExpired, Toast.LENGTH_LONG).show()
                    performAdminLogout()
                } else {
                    Toast.makeText(context, "Error: $msg", Toast.LENGTH_LONG).show()
                }
            }
        }
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

    val allLockedMap = remember(playlists, lockedCategories, homeDataVersion) {
        prefs.getAllLockedMap(playlists.map { it.id })
    }
    val homeFavourites = remember(allLockedMap, homeDataVersion) {
        repo.db.getAllFavoritesAcrossPlaylists(allLockedMap, limit = 60)
    }
    val homeHistory = remember(allLockedMap, homeDataVersion) {
        repo.db.getRecentHistoryAcrossPlaylists(allLockedMap, limit = 30)
    }

    fun handleDoubleBackExit() {
        val now = System.currentTimeMillis()
        if (now - lastBackPressTime <= 2000L) {
            stopPlaybackCompletely()
            onExitApp()
        } else {
            lastBackPressTime = now
            Toast.makeText(context, strings.pressBackAgainExit, Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = stage != AppStage.SPLASH) {
        when {
            playerMode == PlayerMode.FULLSCREEN -> {
                saveCurrentVodProgress(refreshHome = true)
                playerMode = PlayerMode.FLOATING
                isLandscapeFullscreen = false
            }
            stage == AppStage.WELCOME_QUOTE -> stage = AppStage.ONBOARDING_GUIDE
            stage == AppStage.ONBOARDING_GUIDE -> {
                val updated = settings.copy(hasSeenWelcome = true)
                settings = updated
                prefs.saveSettings(updated)
                proceedAfterWelcome()
            }
            stage == AppStage.ADMIN_LOGIN -> {
                val ret = returnStageAfterLogin
                if (ret != null) {
                    returnStageAfterLogin = null
                    stage = ret
                } else {
                    handleDoubleBackExit()
                }
            }
            stage == AppStage.SETTINGS -> stage = returnStageAfterSettings
            stage == AppStage.WORKSPACE -> {
                if (activeTab != MainTab.HOME) {
                    if (activeTab == MainTab.LIVE && activeChannel != null && playerMode == PlayerMode.NONE && isPlaying) {
                        playerMode = PlayerMode.FLOATING
                    }
                    activeTab = MainTab.HOME
                } else {
                    handleDoubleBackExit()
                }
            }
            stage == AppStage.PLAYLISTS -> {
                if (activePlaylist != null) {
                    stage = AppStage.WORKSPACE
                } else {
                    handleDoubleBackExit()
                }
            }
            else -> handleDoubleBackExit()
        }
    }

    YlowstoneTheme(themeMode = settings.themeMode) {
        if (isInPipMode && activeChannel != null) {
            PipVideoSurface(exoPlayer = sharedPlayer, isPlaying = isPlaying)
            return@YlowstoneTheme
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (stage) {
                AppStage.SPLASH -> {
                    SplashOverlayScreen {
                        if (!settings.hasSeenWelcome) {
                            stage = AppStage.WELCOME_QUOTE
                        } else {
                            proceedAfterWelcome()
                        }
                    }
                }

                AppStage.WELCOME_QUOTE -> {
                    WelcomeQuotePopupScreen(
                        appIcon = settings.appIcon,
                        strings = strings,
                        onDismissToGuide = { stage = AppStage.ONBOARDING_GUIDE }
                    )
                }

                AppStage.ONBOARDING_GUIDE -> {
                    OnboardingTutorialScreen(
                        strings = strings,
                        onFinish = {
                            val updated = settings.copy(hasSeenWelcome = true)
                            settings = updated
                            prefs.saveSettings(updated)
                            proceedAfterWelcome()
                        }
                    )
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
                            proceedToAppOrLogin()
                        }
                    )
                }

                AppStage.ADMIN_LOGIN -> {
                    AdminLoginScreen(
                        appIcon = settings.appIcon,
                        strings = strings,
                        isTvMode = isTvMode,
                        isVerifying = isVerifyingLogin,
                        errorMessage = loginError,
                        canGoBack = returnStageAfterLogin != null,
                        onSubmitPin = { enteredPin -> performAdminLogin(enteredPin) },
                        onContinueGuest = {
                            prefs.setPassedLoginGate(true)
                            returnStageAfterLogin = null
                            if (playlists.isNotEmpty()) openWorkspace(playlists.first())
                            else stage = AppStage.PLAYLISTS
                        },
                        onBack = {
                            val ret = returnStageAfterLogin ?: AppStage.PLAYLISTS
                            returnStageAfterLogin = null
                            stage = ret
                        }
                    )
                }

                AppStage.PLAYLISTS -> {
                    PlaylistManagerScreen(
                        playlists = playlists,
                        adminPlaylistIds = adminPlaylistIds,
                        isLoggedIn = isAdminLoggedIn,
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
                        onRefreshPlaylist = { profile ->
                            val freshProfile = repo.db.getAllPlaylists().firstOrNull { it.id == profile.id } ?: profile
                            syncAndEnter(freshProfile, null, enterAfter = false)
                        },
                        onDeletePlaylist = { profile ->
                            val wasDefault = adminPlaylistIds.contains(profile.id) ||
                                profile.name.equals("US/UK/ID", ignoreCase = true) ||
                                profile.name.equals("USA", ignoreCase = true)
                            repo.db.deletePlaylist(profile.id)
                            val nextIds = adminPlaylistIds - profile.id
                            adminPlaylistIds = nextIds
                            prefs.setAdminPlaylistIds(nextIds)

                            val remaining = repo.db.getAllPlaylists()
                            playlists = remaining
                            if (activePlaylist?.id == profile.id) activePlaylist = remaining.firstOrNull()
                            homeDataVersion++

                            if (wasDefault || !hasDefaultServerPlaylist(remaining, nextIds)) {
                                prefs.setAdminSession(false, "")
                                prefs.setPassedLoginGate(false)
                                isAdminLoggedIn = false
                                returnStageAfterLogin = AppStage.PLAYLISTS
                                loginError = ""
                                stage = AppStage.ADMIN_LOGIN
                            }
                        },
                        onOpenLogin = {
                            returnStageAfterLogin = AppStage.PLAYLISTS
                            loginError = ""
                            stage = AppStage.ADMIN_LOGIN
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
                        lockedCategories = lockedCategories,
                        strings = strings,
                        repo = repo,
                        onBack = { stage = returnStageAfterSettings },
                        onUpdateSettings = { updated ->
                            settings = updated
                            prefs.saveSettings(updated)
                        },
                        onUnlockCategory = { cat ->
                            val plId = activePlaylist?.id ?: return@SettingsScreen
                            val next = lockedCategories - cat
                            lockedCategories = next
                            prefs.setLockedCategories(plId, next)
                            homeDataVersion++
                        },
                        onResetPinAndLocks = {
                            val updated = settings.copy(pinCode = "1234")
                            settings = updated
                            prefs.saveSettings(updated)
                            prefs.clearAllLockedCategories(playlists.map { it.id })
                            lockedCategories = emptySet()
                            homeDataVersion++
                        }
                    )
                }

                AppStage.WORKSPACE -> {
                    val pl = activePlaylist
                    if (pl == null) {
                        stage = AppStage.PLAYLISTS
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            AnimatedContent(
                                targetState = activeTab,
                                transitionSpec = {
                                    fadeIn(tween(260)) togetherWith fadeOut(tween(200))
                                },
                                label = "mainTabTransition",
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            ) { currentTab ->
                                when (currentTab) {
                                    MainTab.HOME -> HomeScreen(
                                        activePlaylist = pl,
                                        allPlaylists = playlists,
                                        counts = activeCounts,
                                        favourites = homeFavourites,
                                        history = homeHistory,
                                        strings = strings,
                                        isTvMode = isTvMode,
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
                                            activeTab = when (type) {
                                                MediaType.LIVE -> MainTab.LIVE
                                                MediaType.MOVIE -> MainTab.MOVIES
                                                MediaType.SERIES -> MainTab.SERIES
                                            }
                                        },
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
                            }

                            FotMobBottomNavbar(
                                selectedTab = activeTab,
                                strings = strings,
                                isTvMode = isTvMode,
                                onSelectTab = { nextTab ->
                                    if (activeTab == MainTab.LIVE && nextTab != MainTab.LIVE && activeChannel != null && playerMode == PlayerMode.NONE && isPlaying) {
                                        playerMode = PlayerMode.FLOATING
                                    }
                                    activeTab = nextTab
                                }
                            )
                        }
                    }
                }
            }

            // Global Sync / Indexing Overlay (when syncing right after Admin Login or from Home)
            if (isLoading && stage != AppStage.PLAYLISTS) {
                val c = LocalYlowColors.current
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.68f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(min = 240.dp, max = 320.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(c.surface)
                            .border(1.dp, c.glassBorder, RoundedCornerShape(20.dp))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator(color = c.accent, strokeWidth = 3.dp)
                        Text(
                            text = loadingMessage.ifBlank { strings.loadingPlaylist },
                            color = c.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Top-Right Anchored Quick Profile Dropdown
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .systemBarsPadding()
                    .padding(top = 52.dp, end = 16.dp)
            ) {
                ProfileQuickDropdown(
                    expanded = showProfileDropdown,
                    activePlaylistName = activePlaylist?.name,
                    deviceMode = settings.deviceMode,
                    isAdminLoggedIn = isAdminLoggedIn,
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
                    onOpenAdminLogin = {
                        returnStageAfterLogin = stage
                        loginError = ""
                        stage = AppStage.ADMIN_LOGIN
                    },
                    onSyncAdminPlaylists = {
                        syncAdminFromServer()
                    },
                    onLogoutAdmin = {
                        performAdminLogout()
                    },
                    onCheckUpdates = { runQuickUpdateCheck() }
                )
            }

            // Pure YouTube PiP Floating Mini-Player
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
                        .padding(end = 14.dp, bottom = 74.dp)
                )
            }

            // Fullscreen Player Overlay
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
                    subtitleSession = subtitleSession,
                    onUpdateSubtitleSession = { nextSub -> subtitleSession = nextSub },
                    onMinimizeToFloat = {
                        saveCurrentVodProgress(refreshHome = true)
                        playerMode = PlayerMode.FLOATING
                        isLandscapeFullscreen = false
                    },
                    onTracksModified = { tracksVersion++ }
                )
            }
        }

        // Quick Update Dialog
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
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent)
                    ) {
                        Text(strings.downloadUpdateBrowser, color = c.onAccent, fontWeight = FontWeight.Bold)
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
