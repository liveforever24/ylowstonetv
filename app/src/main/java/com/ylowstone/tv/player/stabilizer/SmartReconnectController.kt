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
 * Pengendali stabilitas playback universal v1.3 untuk YlowstoneTV.
 * Mendukung satu pintu masuk untuk Xtream Codes, URL M3U/M3U8, file lokal,
 * direct .ts, VOD (.mp4/.mkv), DASH (.mpd), link tanpa ekstensi, dan video web,
 * lengkap dengan 2-way auto-swap (.m3u8 <-> .ts) anti-loop & cache format per channel.
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

            // File lokal tidak perlu retry jaringan berulang
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

            // 2. Fallback Live TV 2 arah (.m3u8 <-> .ts), maksimal 1x swap per sesi error (anti-loop)
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

            // Jika percobaan swap gagal tanpa pernah READY, bersihkan override agar tidak meracuni cache
            if (hasSwappedProtocolOnce) {
                resolver.clearPendingSwap(source)
            }

            // 3. Gangguan jaringan atau token redirect kedaluwarsa -> backoff + jitter natural
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
     * Pintu masuk universal untuk memutar segala jenis sumber YlowstoneTV.
     */
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

        val effectiveDebounce = debounceMs ?: if (source.isLive) ZAPPING_DEBOUNCE_MS else 0L

        activeJob = scope.launch {
            if (effectiveDebounce > 0L) {
                _state.value = StabilizerState.SwitchingChannel(channelLabel)
                delay(effectiveDebounce)
            } else if (!channelLabel.isNullOrBlank()) {
                _state.value = StabilizerState.SwitchingChannel(channelLabel)
            }
            streamMutex.withLock {
                connectionGuard.releaseActiveStreamCleanly(player)
                prepareFromSource(source, customUserAgent, startPositionMs)
            }
        }
    }

    /**
     * Pintasan cepat untuk URL mentah (M3U remote, item file .m3u/.m3u8 lokal,
     * aliran tunggal, VOD .mp4/.mkv, direct .ts, maupun tautan video web).
     */
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

    /**
     * Kompatibilitas dengan pemanggil v1.1 khusus Xtream Codes.
     */
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

    fun onNetworkSwitched(networkLabel: String) {
        val source = currentSource ?: return
        if (source is YlowstoneStreamSource.LocalMedia) return

        activeJob?.cancel()
        stabilityResetJob?.cancel()
        wasRecovering = true

        activeJob = scope.launch {
            _state.value = StabilizerState.SwitchingNetwork(networkLabel)
            streamMutex.withLock {
                connectionGuard.releaseActiveStreamCleanly(player, cooldownMs = 0L)
                delay(NETWORK_HANDOFF_DELAY_MS)
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
        private const val PROTOCOL_SWAP_DELAY_MS = 300L
        private const val NETWORK_HANDOFF_DELAY_MS = 1_200L
        private const val STABLE_PLAYBACK_RESET_MS = 10_000L
        private const val JITTER_MS = 300L
        private val SWAP_HTTP_STATUS_CODES = setOf(400, 403, 404, 405, 415, 500, 502, 503)
    }
}
