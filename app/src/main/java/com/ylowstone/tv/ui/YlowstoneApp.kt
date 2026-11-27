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
import androidx.compose.animation.scaleIn
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.ylowstone.tv.data.AppPrefs
import com.ylowstone.tv.data.ActiveSubtitleSession
import com.ylowstone.tv.data.IptvRepository
import com.ylowstone.tv.data.SubtitleFetcher
import com.ylowstone.tv.i18n.stringsFor
import com.ylowstone.tv.model.*
import com.ylowstone.tv.player.stabilizer.NetworkSwitchGuard
import com.ylowstone.tv.player.stabilizer.SmartReconnectController
import com.ylowstone.tv.player.stabilizer.StabilizerStatusPill
import com.ylowstone.tv.player.stabilizer.StrictConnectionGuard
import com.ylowstone.tv.player.stabilizer.YlowstoneStreamSource
import com.ylowstone.tv.player.stabilizer.XtreamSessionMonitor
import com.ylowstone.tv.player.stabilizer.XtreamStreamResolver
import com.ylowstone.tv.ui.components.BoomerangLoadingIndicator
import com.ylowstone.tv.ui.components.ChannelLongPressDialog
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
    var longPressTarget by remember { mutableStateOf<Pair<ChannelItem, String>?>(null) }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    var lastCategoryBackPressTime by remember { mutableLongStateOf(0L) }

    // Shared Persistent Media3 Player
    var activeChannel by remember { mutableStateOf<ChannelItem?>(null) }
    var activeQueue by remember { mutableStateOf<List<ChannelItem>>(emptyList()) }
    var playerMode by remember { mutableStateOf(PlayerMode.NONE) }
    var isLandscapeFullscreen by remember { mutableStateOf(false) }

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var tracksVersion by remember { mutableIntStateOf(0) }
    var subtitleSession by remember { mutableStateOf(ActiveSubtitleSession()) }
    var liveReconnectAttempts by remember { mutableIntStateOf(0) }

    val connectionGuard = remember { StrictConnectionGuard("YlowstoneTV/2.4.6") }
    val streamResolver = remember { XtreamStreamResolver() }
    val sessionMonitor = remember(connectionGuard) { XtreamSessionMonitor(connectionGuard) }

    val sharedPlayer = remember(settings.bufferProfile, connectionGuard) {
        val dsFactory = connectionGuard.createUniversalDataSourceFactory(context.applicationContext, "YlowstoneTV/2.4.6")
        val bp = settings.bufferProfile
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(bp.minBufferMs, bp.maxBufferMs, bp.playbackMs, bp.rebufferMs)
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dsFactory))
            .setLoadControl(loadControl)
            .build()
            .apply {
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setPreferredAudioLanguages("id", "in", "en")
                    .setPreferredTextLanguages("id", "in", "en")
                    .build()
            }
    }

    val reconnectController = remember(sharedPlayer) {
        SmartReconnectController(
            appContext = context.applicationContext,
            player = sharedPlayer,
            scope = scope,
            resolver = streamResolver,
            connectionGuard = connectionGuard,
            sessionMonitor = sessionMonitor
        )
    }
    val stabilizerState by reconnectController.state.collectAsState()

    // Apply embedded subtitle language preference whenever setting changes
    LaunchedEffect(sharedPlayer, settings.autoSubtitleLang) {
        val b = sharedPlayer.trackSelectionParameters.buildUpon()
        when (settings.autoSubtitleLang) {
            AutoSubtitleLang.ID -> {
                b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                b.setPreferredTextLanguages("id", "in", "en")
            }
            AutoSubtitleLang.EN -> {
                b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                b.setPreferredTextLanguages("en", "id")
            }
            AutoSubtitleLang.OFF -> {
                b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            }
        }
        sharedPlayer.trackSelectionParameters = b.build()
    }

    // v2.4.5: Auto-Subtitle Bahasa Indonesia untuk Film & Seri (Xtream + M3U Remote/Lokal, prioritas judul sebelum SxxExx)
    LaunchedEffect(activeChannel?.streamUrl, settings.autoSubtitleLang) {
        val ch = activeChannel ?: return@LaunchedEffect
        val urlPathLower = ch.streamUrl.lowercase(java.util.Locale.US).substringBefore('?')
        val m = Regex("""[Ss](\d+)[Ee](\d+)""").find(ch.name)
        val isSeriesEffective = ch.mediaType == MediaType.SERIES ||
            urlPathLower.contains("/series/") ||
            m != null
        val isVod = isSeriesEffective ||
            ch.mediaType == MediaType.MOVIE ||
            urlPathLower.contains("/movie/") ||
            urlPathLower.endsWith(".mp4") ||
            urlPathLower.endsWith(".mkv") ||
            urlPathLower.endsWith(".webm")
        if (!isVod || settings.autoSubtitleLang != AutoSubtitleLang.ID) return@LaunchedEffect
        if (subtitleSession.streamUrl == ch.streamUrl && subtitleSession.cues.isNotEmpty()) return@LaunchedEffect

        val (qTitle, sNum, eNum) = if (!isSeriesEffective) {
            Triple(ch.name, null as Int?, null as Int?)
        } else {
            val s = m?.groupValues?.getOrNull(1)?.toIntOrNull()
            val e = m?.groupValues?.getOrNull(2)?.toIntOrNull()
            val nameBeforeSe = m?.let { ch.name.substring(0, it.range.first).trim(' ', '-', ':', '|', '.', '_') } ?: ""
            val titleCandidate = when {
                nameBeforeSe.length >= 2 -> nameBeforeSe
                s != null && ch.category.isNotBlank() -> ch.category
                else -> ch.name
            }
            Triple(titleCandidate, s, e)
        }

        SubtitleFetcher.searchIndoSubtitles(
            rawTitle = qTitle,
            isSeries = isSeriesEffective,
            season = sNum,
            episode = eNum
        ).onSuccess { candidates ->
            if (activeChannel?.streamUrl != ch.streamUrl) return@onSuccess
            if (candidates.isEmpty()) return@onSuccess
            val best = SubtitleFetcher.pickBestCandidate(candidates, ch.name, sNum, eNum) ?: candidates.first()
            val savedOffset = prefs.getSeriesSubtitleOffsetMs(qTitle)
            SubtitleFetcher.downloadSubtitleToCache(
                context = context,
                zipUrl = best.zipUrl,
                season = sNum,
                episode = eNum
            ).onSuccess { rawFile ->
                if (activeChannel?.streamUrl != ch.streamUrl) return@onSuccess
                val cues = SubtitleFetcher.parseSrtCues(rawFile)
                if (cues.isNotEmpty()) {
                    subtitleSession = ActiveSubtitleSession(
                        streamUrl = ch.streamUrl,
                        candidates = candidates,
                        rawFile = rawFile,
                        cues = cues,
                        label = best.releaseName,
                        offsetMs = savedOffset
                    )
                    tracksVersion++
                }
            }
        }
    }

    DisposableEffect(sharedPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    liveReconnectAttempts = 0
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onTracksChanged(tracks: Tracks) {
                tracksVersion++
            }
            override fun onPlayerError(error: PlaybackException) {
                // Xtream Live ditangani penuh oleh SmartReconnectController agar tidak terjadi double-reconnect
                if (reconnectController.isHandlingCurrentStream) return
                val cur = activeChannel
                if (cur != null && cur.mediaType == MediaType.LIVE && settings.autoReconnectLive && liveReconnectAttempts < 2) {
                    liveReconnectAttempts++
                    scope.launch {
                        delay(1500L)
                        if (activeChannel?.streamUrl == cur.streamUrl) {
                            sharedPlayer.setMediaItem(MediaItem.fromUri(cur.streamUrl))
                            sharedPlayer.prepare()
                            sharedPlayer.playWhenReady = true
                        }
                    }
                } else {
                    Toast.makeText(context, "Stream error: ${error.errorCodeName}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        sharedPlayer.addListener(listener)
        reconnectController.attach()
        val networkGuard = NetworkSwitchGuard(context) { netLabel ->
            if (reconnectController.isHandlingCurrentStream) {
                reconnectController.onNetworkSwitched(netLabel)
            }
        }
        networkGuard.start()
        onDispose {
            networkGuard.stop()
            reconnectController.detach()
            connectionGuard.shutdown()
            sharedPlayer.removeListener(listener)
            sharedPlayer.release()
        }
    }

    fun resolvePlaylistName(ch: ChannelItem): String {
        return playlists.firstOrNull { it.id == ch.playlistId }?.name ?: activePlaylist?.name ?: "YlowstoneTV"
    }

    fun saveCurrentVodProgress(refreshHome: Boolean = false) {
        val cur = activeChannel ?: return
        if (cur.isAdultContent()) return
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

    LaunchedEffect(activeChannel?.streamUrl, isPlaying) {
        val cur = activeChannel ?: return@LaunchedEffect
        if (cur.isAdultContent()) return@LaunchedEffect
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
            liveReconnectAttempts = 0
            saveCurrentVodProgress(refreshHome = false)
            if (subtitleSession.streamUrl != ch.streamUrl) {
                subtitleSession = ActiveSubtitleSession(streamUrl = ch.streamUrl)
            }
        }
        activeChannel = ch
        activeQueue = queue.ifEmpty { listOf(ch) }
        if (!ch.isAdultContent()) {
            val plName = resolvePlaylistName(ch)
            repo.db.recordWatchHistory(ch, plName)
            homeDataVersion++
        }

        if (!sameStream) {
            val plObj = playlists.firstOrNull { it.id == ch.playlistId } ?: activePlaylist
            val isVod = ch.mediaType == MediaType.MOVIE || ch.mediaType == MediaType.SERIES
            val resumeMs = if (isVod && !ch.isAdultContent()) {
                val saved = repo.db.getWatchProgress(ch.streamUrl)
                if (saved != null && saved.first > 5_000L && (saved.second <= 0L || saved.first < (saved.second * 95L) / 100L)) {
                    saved.first
                } else 0L
            } else 0L

            if (resumeMs > 0L) {
                Toast.makeText(context, "Melanjutkan dari ${formatResumeMs(resumeMs)}", Toast.LENGTH_SHORT).show()
            }

            val useXtreamCanonical = ch.mediaType == MediaType.LIVE &&
                plObj != null &&
                plObj.type == PlaylistType.XTREAM &&
                plObj.url.isNotBlank() &&
                plObj.username.isNotBlank() &&
                ch.streamId.isNotBlank()

            if (useXtreamCanonical && plObj != null) {
                val xtreamSrc = YlowstoneStreamSource.Xtream(
                    hostUrl = plObj.url,
                    username = plObj.username,
                    password = plObj.password,
                    streamId = ch.streamId,
                    isLive = true
                )
                if (settings.streamFormat == StreamFormat.TS) {
                    streamResolver.markHlsUnsupported(xtreamSrc)
                }
                reconnectController.play(
                    source = xtreamSrc,
                    customUserAgent = plObj.userAgent.ifBlank { null },
                    channelLabel = ch.name
                )
            } else {
                reconnectController.playUrl(
                    rawUrl = ch.streamUrl,
                    isLiveHint = ch.mediaType == MediaType.LIVE,
                    customUserAgent = plObj?.userAgent?.ifBlank { null },
                    channelLabel = ch.name,
                    startPositionMs = resumeMs
                )
            }
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
        reconnectController.stopStream()
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
        if (enterLandscape) {
            playerMode = PlayerMode.FULLSCREEN
            isLandscapeFullscreen = true
        } else {
            playerMode = PlayerMode.SPLIT
            isLandscapeFullscreen = false
        }
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

    // v2.3.1 Perbaikan #1: Sync murni hanya 1 playlist yang sedang aktif dipilih (tidak tarik ulang server / semua playlist)
    fun syncActivePlaylistOnly() {
        val current = activePlaylist ?: playlists.firstOrNull()
        if (current != null) {
            val fresh = repo.db.getAllPlaylists().firstOrNull { it.id == current.id } ?: current
            syncAndEnter(fresh, null, enterAfter = false)
        } else {
            Toast.makeText(context, strings.noPlaylistYet, Toast.LENGTH_SHORT).show()
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

    // v2.3.1 Perbaikan #3: Gestur Back 2x dari layar Kategori -> langsung Beranda tanpa pop-up
    fun handleDoubleBackToHome() {
        val now = System.currentTimeMillis()
        if (now - lastCategoryBackPressTime <= 2000L) {
            lastCategoryBackPressTime = 0L
            if (activeTab == MainTab.LIVE && activeChannel != null && playerMode == PlayerMode.NONE && isPlaying) {
                playerMode = PlayerMode.FLOATING
            }
            activeTab = MainTab.HOME
        } else {
            lastCategoryBackPressTime = now
            Toast.makeText(context, strings.pressBackAgainHome, Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = stage != AppStage.SPLASH) {
        when {
            longPressTarget != null -> {
                longPressTarget = null
            }
            playerMode == PlayerMode.FULLSCREEN -> {
                saveCurrentVodProgress(refreshHome = true)
                playerMode = PlayerMode.SPLIT
                isLandscapeFullscreen = false
            }
            playerMode == PlayerMode.SPLIT -> {
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
                when (activeTab) {
                    MainTab.LIVE, MainTab.MOVIES, MainTab.SERIES -> {
                        handleDoubleBackToHome()
                    }
                    MainTab.SEARCH -> {
                        activeTab = MainTab.HOME
                    }
                    MainTab.HOME -> {
                        handleDoubleBackExit()
                    }
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

    YlowstoneTheme(themeMode = settings.themeMode, accentPreset = settings.accentPreset) {
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
                        loadingAnimStyle = settings.loadingAnimStyle,
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
                        prefs = prefs,
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
                        },
                        isAdminLoggedIn = isAdminLoggedIn,
                        onLogoutAdmin = { performAdminLogout() },
                        onBackupRestored = { restored ->
                            settings = restored
                            homeDataVersion++
                        }
                    )
                }

                AppStage.WORKSPACE -> {
                    val pl = activePlaylist
                    if (pl == null) {
                        stage = AppStage.PLAYLISTS
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .navigationBarsPadding()
                        ) {
                            AnimatedContent(
                                targetState = activeTab,
                                transitionSpec = {
                                    if (initialState == MainTab.HOME && targetState != MainTab.HOME) {
                                        (scaleIn(initialScale = 0.95f, animationSpec = tween(220)) + fadeIn(tween(220))) togetherWith fadeOut(tween(140))
                                    } else {
                                        fadeIn(tween(200)) togetherWith fadeOut(tween(160))
                                    }
                                },
                                label = "mainTabTransition",
                                modifier = Modifier.fillMaxSize()
                            ) { currentTab ->
                                when (currentTab) {
                                    MainTab.HOME -> HomeScreen(
                                        activePlaylist = pl,
                                        counts = activeCounts,
                                        favourites = homeFavourites,
                                        history = homeHistory,
                                        favoriteViewMode = settings.favoriteViewMode,
                                        activeChannel = activeChannel,
                                        isPlaying = isPlaying,
                                        strings = strings,
                                        isTvMode = isTvMode,
                                        onOpenSection = { type ->
                                            activeTab = when (type) {
                                                MediaType.LIVE -> MainTab.LIVE
                                                MediaType.MOVIE -> MainTab.MOVIES
                                                MediaType.SERIES -> MainTab.SERIES
                                            }
                                        },
                                        onOpenPlaylists = {
                                            if (activeChannel != null && playerMode == PlayerMode.NONE && isPlaying) {
                                                playerMode = PlayerMode.FLOATING
                                            }
                                            stage = AppStage.PLAYLISTS
                                        },
                                        onChangeFavoriteViewMode = { mode ->
                                            val updated = settings.copy(favoriteViewMode = mode)
                                            settings = updated
                                            prefs.saveSettings(updated)
                                        },
                                        onPlayTaggedChannel = { tagged, list ->
                                            launchPlayer(tagged.channel, list.map { it.channel }, false)
                                        },
                                        onLongPressTaggedChannel = { tagged ->
                                            longPressTarget = tagged.channel to tagged.playlistName
                                        },
                                        onTogglePlayPause = {
                                            if (sharedPlayer.isPlaying) sharedPlayer.pause() else sharedPlayer.play()
                                        },
                                        onOpenActivePlayer = {
                                            if (activeChannel != null) {
                                                playerMode = PlayerMode.SPLIT
                                                isLandscapeFullscreen = false
                                            }
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
                                        isPlaying = isPlaying,
                                        playerMode = playerMode,
                                        lockedCategories = lockedCategories,
                                        pinCode = settings.pinCode,
                                        strings = strings,
                                        isTvMode = isTvMode,
                                        onBackHome = { activeTab = MainTab.HOME },
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
                                        onLongPressChannel = { ch ->
                                            longPressTarget = ch to pl.name
                                        },
                                        onTogglePlayPause = {
                                            if (sharedPlayer.isPlaying) sharedPlayer.pause() else sharedPlayer.play()
                                        },
                                        onOpenActivePlayer = {
                                            if (activeChannel != null) {
                                                playerMode = PlayerMode.SPLIT
                                                isLandscapeFullscreen = false
                                            }
                                        },
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
                                        isPlaying = isPlaying,
                                        playerMode = playerMode,
                                        lockedCategories = lockedCategories,
                                        pinCode = settings.pinCode,
                                        strings = strings,
                                        isTvMode = isTvMode,
                                        onBackHome = { activeTab = MainTab.HOME },
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
                                        onSelectChannelInline = { ch, list -> launchPlayer(ch, list, false) },
                                        onLaunchPlayer = { ch, list, landscape -> launchPlayer(ch, list, landscape) },
                                        onLongPressChannel = { ch ->
                                            longPressTarget = ch to pl.name
                                        },
                                        onTogglePlayPause = {
                                            if (sharedPlayer.isPlaying) sharedPlayer.pause() else sharedPlayer.play()
                                        },
                                        onOpenActivePlayer = {
                                            if (activeChannel != null) {
                                                playerMode = PlayerMode.SPLIT
                                                isLandscapeFullscreen = false
                                            }
                                        },
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
                                        isPlaying = isPlaying,
                                        playerMode = playerMode,
                                        lockedCategories = lockedCategories,
                                        pinCode = settings.pinCode,
                                        strings = strings,
                                        isTvMode = isTvMode,
                                        onBackHome = { activeTab = MainTab.HOME },
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
                                        onSelectChannelInline = { ch, list -> launchPlayer(ch, list, false) },
                                        onLaunchPlayer = { ch, list, landscape -> launchPlayer(ch, list, landscape) },
                                        onLongPressChannel = { ch ->
                                            longPressTarget = ch to pl.name
                                        },
                                        onTogglePlayPause = {
                                            if (sharedPlayer.isPlaying) sharedPlayer.pause() else sharedPlayer.play()
                                        },
                                        onOpenActivePlayer = {
                                            if (activeChannel != null) {
                                                playerMode = PlayerMode.SPLIT
                                                isLandscapeFullscreen = false
                                            }
                                        },
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
                                            launchPlayer(tagged.channel, list.map { it.channel }, false)
                                        },
                                        onLongPressTagged = { tagged ->
                                            longPressTarget = tagged.channel to tagged.playlistName
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
                        }
                    }
                }
            }

            // Global Sync / Indexing Overlay (v2.3.3: Bumerang melayang bersih tanpa kotak hitam)
            if (isLoading && stage != AppStage.PLAYLISTS) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.76f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        BoomerangLoadingIndicator(style = settings.loadingAnimStyle, size = 78.dp)
                        Text(
                            text = loadingMessage.ifBlank { strings.loadingPlaylist },
                            color = Color.White.copy(alpha = 0.92f),
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
                        syncActivePlaylistOnly()
                    }
                )
            }

            // Split-Screen Player Overlay
            if (playerMode == PlayerMode.SPLIT && activeChannel != null && stage != AppStage.SPLASH) {
                SplitPlayerScreen(
                    exoPlayer = sharedPlayer,
                    currentChannel = activeChannel!!,
                    channelList = activeQueue.ifEmpty { listOf(activeChannel!!) },
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    tracksVersion = tracksVersion,
                    isTvMode = isTvMode,
                    subtitleSession = subtitleSession,
                    onUpdateSubtitleSession = { nextSub -> subtitleSession = nextSub },
                    onTracksModified = { tracksVersion++ },
                    onSaveSeriesSubtitleOffset = { sKey, offMs -> prefs.saveSeriesSubtitleOffsetMs(sKey, offMs) },
                    strings = strings,
                    onSelectChannel = { nextCh -> playStream(nextCh, activeQueue) },
                    onLongPressChannel = { ch -> longPressTarget = ch to resolvePlaylistName(ch) },
                    onToggleFavorite = { ch ->
                        repo.db.toggleFavorite(ch.id, !ch.isFavorite)
                        activeQueue = activeQueue.map {
                            if (it.id == ch.id) it.copy(isFavorite = !ch.isFavorite) else it
                        }
                        if (activeChannel?.id == ch.id) {
                            activeChannel = activeChannel?.copy(isFavorite = !ch.isFavorite)
                        }
                        homeDataVersion++
                    },
                    onEnterFullscreen = {
                        playerMode = PlayerMode.FULLSCREEN
                        isLandscapeFullscreen = true
                    },
                    onMinimizeToFloat = {
                        saveCurrentVodProgress(refreshHome = true)
                        playerMode = PlayerMode.FLOATING
                        isLandscapeFullscreen = false
                    },
                    onEnterSystemPip = onEnterSystemPip,
                    onClosePlayer = { stopPlaybackCompletely() }
                )
            }

            // Minimalist Floating Mini-Player
            if (playerMode == PlayerMode.FLOATING && activeChannel != null && stage != AppStage.SPLASH) {
                DraggableFloatingPlayer(
                    exoPlayer = sharedPlayer,
                    channel = activeChannel!!,
                    isPlaying = isPlaying,
                    onExpandFullscreen = {
                        playerMode = PlayerMode.FULLSCREEN
                        isLandscapeFullscreen = true
                    },
                    onClose = { stopPlaybackCompletely() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 14.dp, bottom = 18.dp)
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
                    onToggleFullscreenOrientation = {
                        if (isLandscapeFullscreen) {
                            playerMode = PlayerMode.SPLIT
                            isLandscapeFullscreen = false
                        } else {
                            isLandscapeFullscreen = true
                        }
                    },
                    subtitleSession = subtitleSession,
                    onUpdateSubtitleSession = { nextSub -> subtitleSession = nextSub },
                    onMinimizeToFloat = {
                        saveCurrentVodProgress(refreshHome = true)
                        playerMode = PlayerMode.FLOATING
                        isLandscapeFullscreen = false
                    },
                    onTracksModified = { tracksVersion++ },
                    skipIntroMode = settings.skipIntroMode,
                    skipIntroSeconds = prefs.getSeriesIntroSeconds(
                        activeChannel!!.category.ifBlank { activeChannel!!.name },
                        settings.skipIntroSeconds
                    ),
                    autoNextEpisode = settings.autoNextEpisode,
                    onSaveSeriesSubtitleOffset = { sKey, offMs -> prefs.saveSeriesSubtitleOffsetMs(sKey, offMs) },
                    onSaveSeriesIntroSeconds = { sKey, sec -> prefs.saveSeriesIntroSeconds(sKey, sec) }
                )
            }

            // Pil Status XtreamStabilizer v1.1 (otomatis hilang 2.8 detik saat stream stabil)
            if ((playerMode == PlayerMode.FULLSCREEN || playerMode == PlayerMode.SPLIT) && activeChannel != null) {
                StabilizerStatusPill(
                    state = stabilizerState,
                    onRetryClick = { reconnectController.retryNow() },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 12.dp)
                )
            }
        }

        // Menu Tekan Lama (Long-Press) Ringkas v2.4.0 (Salin Link, Cek Status, Favorit)
        longPressTarget?.let { (ch, plName) ->
            ChannelLongPressDialog(
                channel = ch,
                playlistName = plName,
                repo = repo,
                strings = strings,
                onDismiss = { longPressTarget = null },
                onToggleFavorite = { targetCh ->
                    repo.db.toggleFavorite(targetCh.id, !targetCh.isFavorite)
                    activeQueue = activeQueue.map {
                        if (it.id == targetCh.id) it.copy(isFavorite = !targetCh.isFavorite) else it
                    }
                    if (activeChannel?.id == targetCh.id) {
                        activeChannel = activeChannel?.copy(isFavorite = !targetCh.isFavorite)
                    }
                    longPressTarget = targetCh.copy(isFavorite = !targetCh.isFavorite) to plName
                    homeDataVersion++
                }
            )
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
