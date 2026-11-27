package com.ylowstone.tv.player.stabilizer

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.random.Random

sealed class StabilizerState {
    data object Idle : StabilizerState()
    data class SwitchingChannel(val channelLabel: String?) : StabilizerState()
    data class Playing(
        val protocol: StreamProtocol,
        val recoveredFromIssue: Boolean = false
    ) : StabilizerState()
    data class SwitchingNetwork(val networkLabel: String) : StabilizerState()
    data class Reconnecting(
        val attempt: Int,
        val maxAttempts: Int,
        val delaySeconds: Int,
        val reason: String
    ) : StabilizerState()
    data class QuotaExhausted(
        val activeConnections: Int,
        val maxConnections: Int
    ) : StabilizerState()
    data class Failed(val message: String) : StabilizerState()
}

/**
 * Pengendali stabilitas playback universal v2.0 untuk YlowstoneTV:
 * - Mempertahankan 2-way auto-swap (.m3u8 <-> .ts) anti-loop & cache format per channel (v1.3).
 * - Graceful Teardown + Cool-Down Handshake + cek active_cons saat pindah jaringan (anti ketendang IP baru).
 * - Silent Token Auto-Heal untuk HTTP 401/403/410 pada stream bertoken sementara tanpa spam retry.
 * - Zapping Pre-Warm host DNS/IP di latar belakang.
 */
class SmartReconnectController(
    private val appContext: Context,
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
    private val resolver: XtreamStreamResolver,
    private val connectionGuard: StrictConnectionGuard,
    private val sessionMonitor: XtreamSessionMonitor
) {
    private val backoffScheduleMs = longArrayOf(2_000L, 5_000L, 10_000L, 20_000L)
    private val streamMutex = Mutex()

    private val _state = MutableStateFlow<StabilizerState>(StabilizerState.Idle)
    val state: StateFlow<StabilizerState> = _state.asStateFlow()

    private var currentSource: YlowstoneStreamSource? = null
    val isHandlingCurrentStream: Boolean
        get() = currentSource != null
    private var currentUserAgent: String? = null
    private var currentProtocol: StreamProtocol = StreamProtocol.AUTO_DETECT
    private var attemptCount: Int = 0
    private var wasRecovering: Boolean = false
    private var hasSwappedProtocolOnce: Boolean = false
    private var hasSilentTokenHealedOnce: Boolean = false

    private var activeJob: Job? = null
    private var stabilityResetJob: Job? = null
    private var isAttached: Boolean = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                currentSource?.let { src ->
                    resolver.confirmWorkingProtocol(src, currentProtocol)
                }
                val recovered = wasRecovering
                wasRecovering = false
                _state.value = StabilizerState.Playing(
                    protocol = currentProtocol,
                    recoveredFromIssue = recovered
                )
                scheduleAttemptReset()
            }
        }

        @OptIn(UnstableApi::class)
        override fun onPlayerError(error: PlaybackException) {
            val source = currentSource ?: return
            stabilityResetJob?.cancel()

            if (source is YlowstoneStreamSource.LocalMedia) {
                _state.value = StabilizerState.Failed("Berkas lokal tidak dapat diputar")
                return
            }

            // 1. HLS live window tertinggal -> lompat ke live edge
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                wasRecovering = true
                player.seekToDefaultPosition()
                player.prepare()
                return
            }

            // 2. Silent Token Auto-Heal untuk HTTP 401/403/410 (token sementara kadaluarsa)
            if (!hasSilentTokenHealedOnce && isTokenExpiredError(error)) {
                hasSilentTokenHealedOnce = true
                wasRecovering = true
                val targetHost = when (source) {
                    is YlowstoneStreamSource.Xtream -> source.normalizedHost
                    is YlowstoneStreamSource.DirectUrl -> source.rawUrl
                    is YlowstoneStreamSource.Vod -> source.rawUrl
                    else -> ""
                }
                if (targetHost.isNotBlank()) {
                    connectionGuard.clearSessionCookiesForHost(targetHost)
                }
                launchReconnect(
                    source = source,
                    delayMs = TOKEN_HEAL_DELAY_MS,
                    countAsAttempt = false,
                    reason = "Menyegarkan sesi"
                )
                return
            }

            // 3. Fallback Live TV 2 arah (.m3u8 <-> .ts), maksimal 1x swap per sesi error (anti-loop)
            if (source.isLive &&
                !hasSwappedProtocolOnce &&
                (currentProtocol == StreamProtocol.HLS_M3U8 || currentProtocol == StreamProtocol.MPEG_TS) &&
                isProtocolSwapError(error)
            ) {
                val swappedProtocol = resolver.swapLiveProtocol(source, currentProtocol)
                if (swappedProtocol != null) {
                    hasSwappedProtocolOnce = true
                    wasRecovering = true
                    val swapReason = when (swappedProtocol) {
                        StreamProtocol.MPEG_TS -> "Beralih ke mode TS"
                        StreamProtocol.HLS_M3U8 -> "Beralih ke mode HLS"
                        else -> "Beralih format aliran"
                    }
                    launchReconnect(
                        source = source,
                        delayMs = PROTOCOL_SWAP_DELAY_MS,
                        countAsAttempt = false,
                        reason = swapReason
                    )
                    return
                }
            }

            if (hasSwappedProtocolOnce) {
                resolver.clearPendingSwap(source)
            }

            // 4. Gangguan jaringan -> backoff + jitter natural
            if (attemptCount < backoffScheduleMs.size) {
                val baseDelay = backoffScheduleMs[attemptCount]
                val jitter = Random.nextLong(-JITTER_MS, JITTER_MS + 1)
                val finalDelay = (baseDelay + jitter).coerceAtLeast(1_500L)
                attemptCount += 1
                wasRecovering = true
                launchReconnect(
                    source = source,
                    delayMs = finalDelay,
                    countAsAttempt = true,
                    reason = describeError(error)
                )
            } else {
                verifyQuotaAndFail(source, error)
            }
        }
    }

    fun attach() {
        if (!isAttached) {
            player.addListener(playerListener)
            isAttached = true
        }
    }

    fun detach() {
        activeJob?.cancel()
        stabilityResetJob?.cancel()
        if (isAttached) {
            player.removeListener(playerListener)
            isAttached = false
        }
        _state.value = StabilizerState.Idle
    }

    /**
     * Pre-warm DNS DoH & Fast-IP Pinning di latar belakang saat pengguna menjelajahi daftar channel.
     */
    fun prewarmUrl(rawUrl: String) {
        if (rawUrl.isBlank()) return
        scope.launch(Dispatchers.IO) {
            connectionGuard.prewarmHost(rawUrl)
        }
    }

    fun play(
        source: YlowstoneStreamSource,
        customUserAgent: String? = null,
        channelLabel: String? = null,
        debounceMs: Long? = null,
        startPositionMs: Long = 0L
    ) {
        activeJob?.cancel()
        stabilityResetJob?.cancel()
        resolver.clearPendingSwap(source)
        currentSource = source
        currentUserAgent = customUserAgent
        attemptCount = 0
        wasRecovering = false
        hasSwappedProtocolOnce = false
        hasSilentTokenHealedOnce = false

        val effectiveDebounce = debounceMs ?: if (source.isLive) ZAPPING_DEBOUNCE_MS else 0L

        activeJob = scope.launch {
            if (effectiveDebounce > 0L) {
                _state.value = StabilizerState.SwitchingChannel(channelLabel)
                delay(effectiveDebounce)
            } else if (!channelLabel.isNullOrBlank()) {
                _state.value = StabilizerState.SwitchingChannel(channelLabel)
            }
            streamMutex.withLock {
                // Graceful teardown + cool-down adaptif agar sesi lama tertutup bersih di server Xtream
                val cooldown = if (source.isLive) LIVE_SWITCH_COOLDOWN_MS else 180L
                connectionGuard.releaseActiveStreamCleanly(player, cooldownMs = cooldown)
                prepareFromSource(source, customUserAgent, startPositionMs)
            }
        }
    }

    fun playUrl(
        rawUrl: String,
        isLiveHint: Boolean? = null,
        customUserAgent: String? = null,
        customHeaders: Map<String, String> = emptyMap(),
        channelLabel: String? = null,
        startPositionMs: Long = 0L
    ) {
        val source = YlowstoneStreamSource.fromRawUrl(
            rawUrl = rawUrl,
            isLiveHint = isLiveHint,
            customHeaders = customHeaders
        )
        play(
            source = source,
            customUserAgent = customUserAgent,
            channelLabel = channelLabel,
            startPositionMs = startPositionMs
        )
    }

    fun playCanonical(
        source: XtreamCanonicalSource,
        customUserAgent: String? = null,
        channelLabel: String? = null,
        debounceMs: Long = ZAPPING_DEBOUNCE_MS
    ) {
        play(
            source = source,
            customUserAgent = customUserAgent,
            channelLabel = channelLabel,
            debounceMs = debounceMs
        )
    }

    /**
     * Graceful Teardown + Cool-Down Handshake + cek kuota active_cons saat ganti IP/Jaringan (Wi-Fi <-> 4G/5G).
     * Mencegah server Xtream menganggap IP baru sebagai koneksi ganda (anti ketendang).
     */
    fun onNetworkSwitched(networkLabel: String) {
        val source = currentSource ?: return
        if (source is YlowstoneStreamSource.LocalMedia) return

        activeJob?.cancel()
        stabilityResetJob?.cancel()
        wasRecovering = true
        hasSilentTokenHealedOnce = false

        activeJob = scope.launch {
            _state.value = StabilizerState.SwitchingNetwork(networkLabel)
            streamMutex.withLock {
                connectionGuard.clearDnsPins()
                connectionGuard.releaseActiveStreamCleanly(player, cooldownMs = 0L)
                delay(NETWORK_HANDOFF_DELAY_MS)

                if (source is YlowstoneStreamSource.Xtream) {
                    val status = sessionMonitor.fetchStatus(
                        hostUrl = source.normalizedHost,
                        username = source.username,
                        password = source.password,
                        forceRefresh = true
                    )
                    if (status != null && status.isQuotaFull) {
                        // Beri ekstra waktu agar server Xtream melepas socket ghost dari IP lama
                        delay(GHOST_SOCKET_EXTRA_COOLDOWN_MS)
                    }
                }

                prepareFromSource(source, currentUserAgent)
            }
        }
    }

    fun retryNow() {
        val source = currentSource ?: return
        play(source, currentUserAgent, debounceMs = 0L)
    }

    fun stopStream() {
        activeJob?.cancel()
        stabilityResetJob?.cancel()
        currentSource?.let { resolver.clearPendingSwap(it) }
        currentSource = null
        attemptCount = 0
        wasRecovering = false
        hasSwappedProtocolOnce = false
        hasSilentTokenHealedOnce = false
        scope.launch {
            streamMutex.withLock {
                connectionGuard.releaseActiveStreamCleanly(player, cooldownMs = 0L)
                _state.value = StabilizerState.Idle
            }
        }
    }

    private fun launchReconnect(
        source: YlowstoneStreamSource,
        delayMs: Long,
        countAsAttempt: Boolean,
        reason: String
    ) {
        activeJob?.cancel()
        activeJob = scope.launch {
            val shownAttempt = if (countAsAttempt) attemptCount else 1
            _state.value = StabilizerState.Reconnecting(
                attempt = shownAttempt,
                maxAttempts = backoffScheduleMs.size,
                delaySeconds = ((delayMs + 999L) / 1000L).toInt().coerceAtLeast(1),
                reason = reason
            )
            streamMutex.withLock {
                connectionGuard.releaseActiveStreamCleanly(player, cooldownMs = 0L)
                delay(delayMs)
                prepareFromSource(source, currentUserAgent)
            }
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun prepareFromSource(
        source: YlowstoneStreamSource,
        customUserAgent: String?,
        startPositionMs: Long = 0L
    ) {
        val resolved = resolver.resolve(source)
        if (resolved == null) {
            _state.value = StabilizerState.Failed("Tautan video web memerlukan ekstraktor aktif")
            return
        }

        currentProtocol = resolved.protocol
        val factory = connectionGuard.createUniversalDataSourceFactory(
            context = appContext,
            customUserAgent = customUserAgent,
            extraHeaders = resolved.headers
        )
        val mediaSource = resolver.buildMediaSource(resolved, factory)

        withContext(Dispatchers.Main.immediate) {
            if (startPositionMs > 0L && !resolved.isLive) {
                player.setMediaSource(mediaSource, startPositionMs)
            } else {
                player.setMediaSource(mediaSource)
            }
            player.prepare()
            player.playWhenReady = true
        }
    }

    private fun scheduleAttemptReset() {
        stabilityResetJob?.cancel()
        stabilityResetJob = scope.launch {
            delay(STABLE_PLAYBACK_RESET_MS)
            attemptCount = 0
            hasSwappedProtocolOnce = false
            hasSilentTokenHealedOnce = false
        }
    }

    private fun verifyQuotaAndFail(source: YlowstoneStreamSource, error: PlaybackException) {
        activeJob?.cancel()
        activeJob = scope.launch {
            if (source is YlowstoneStreamSource.Xtream) {
                val status = sessionMonitor.fetchStatus(
                    hostUrl = source.normalizedHost,
                    username = source.username,
                    password = source.password,
                    forceRefresh = true
                )
                if (status != null && status.isQuotaFull) {
                    _state.value = StabilizerState.QuotaExhausted(
                        activeConnections = status.activeConnections,
                        maxConnections = status.maxConnections
                    )
                    return@launch
                }
            }
            _state.value = StabilizerState.Failed(
                message = "Aliran offline (${describeError(error)})"
            )
        }
    }

    @OptIn(UnstableApi::class)
    private fun isTokenExpiredError(error: PlaybackException): Boolean {
        val httpCause = error.cause as? HttpDataSource.InvalidResponseCodeException
        return httpCause?.responseCode in TOKEN_EXPIRED_HTTP_CODES
    }

    @OptIn(UnstableApi::class)
    private fun isProtocolSwapError(error: PlaybackException): Boolean {
        val code = error.errorCode
        if (code == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ||
            code == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ||
            code == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
            code == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
            code == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
        ) {
            return true
        }
        val httpCause = error.cause as? HttpDataSource.InvalidResponseCodeException
        return httpCause?.responseCode in SWAP_HTTP_STATUS_CODES
    }

    @OptIn(UnstableApi::class)
    private fun describeError(error: PlaybackException): String {
        val httpCause = error.cause as? HttpDataSource.InvalidResponseCodeException
        if (httpCause != null) {
            return "HTTP ${httpCause.responseCode}"
        }
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Timeout jaringan"
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Koneksi terputus"
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Token ditolak"
            else -> "Gangguan stream"
        }
    }

    companion object {
        private const val ZAPPING_DEBOUNCE_MS = 400L
        private const val LIVE_SWITCH_COOLDOWN_MS = 420L
        private const val PROTOCOL_SWAP_DELAY_MS = 350L
        private const val TOKEN_HEAL_DELAY_MS = 900L
        private const val NETWORK_HANDOFF_DELAY_MS = 1_100L
        private const val GHOST_SOCKET_EXTRA_COOLDOWN_MS = 1_400L
        private const val STABLE_PLAYBACK_RESET_MS = 10_000L
        private const val JITTER_MS = 300L
        private val TOKEN_EXPIRED_HTTP_CODES = setOf(401, 403, 410)
        private val SWAP_HTTP_STATUS_CODES = setOf(400, 403, 404, 405, 415, 500, 502, 503)
    }
}
