package com.ylowstone.tv.player.stabilizer

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * Pengendali stabilitas playback Xtream Codes v1.1 untuk YlowstoneTV:
 * - Single-Stream Mutex (mustahil membuka 2 stream paralel)
 * - Zapping Debounce 400ms (pindah channel cepat tidak spam server)
 * - Clean Network Handoff saat Android berpindah Wi-Fi <-> Seluler
 * - Exponential Backoff + natural jitter + fresh 302 token dari URL kanonik
 */
class SmartReconnectController(
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

    private var currentSource: XtreamCanonicalSource? = null
    private var currentUserAgent: String? = null
    private var currentProtocol: StreamProtocol = StreamProtocol.HLS_M3U8
    private var attemptCount: Int = 0
    private var wasRecovering: Boolean = false

    private var activeJob: Job? = null
    private var stabilityResetJob: Job? = null
    private var isAttached: Boolean = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
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

            // 1. HLS live window tertinggal -> lompat ke live edge tanpa menambah hitungan retry
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                wasRecovering = true
                player.seekToDefaultPosition()
                player.prepare()
                return
            }

            // 2. Server menolak manifest HLS (.m3u8) -> langsung fallback ke MPEG-TS (.ts)
            if (source.isLive && currentProtocol == StreamProtocol.HLS_M3U8 && isHlsUnsupportedError(error)) {
                val newlyMarked = resolver.markHlsUnsupported(source.streamId)
                if (newlyMarked) {
                    wasRecovering = true
                    launchReconnect(
                        source = source,
                        delayMs = 300L,
                        countAsAttempt = false,
                        reason = "Beralih ke mode TS"
                    )
                    return
                }
            }

            // 3. Error jaringan / token Load Balancer kedaluwarsa -> backoff + jitter natural
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
     * Memutar channel dari URL kanonik dengan Zapping Debounce 400ms dan
     * Single-Stream Mutex agar perpindahan cepat tidak memicu blokir server.
     */
    fun playCanonical(
        source: XtreamCanonicalSource,
        customUserAgent: String? = null,
        channelLabel: String? = null,
        debounceMs: Long = ZAPPING_DEBOUNCE_MS
    ) {
        activeJob?.cancel()
        stabilityResetJob?.cancel()
        currentSource = source
        currentUserAgent = customUserAgent
        attemptCount = 0
        wasRecovering = false

        activeJob = scope.launch {
            if (debounceMs > 0L) {
                _state.value = StabilizerState.SwitchingChannel(channelLabel)
                delay(debounceMs)
            }
            streamMutex.withLock {
                connectionGuard.releaseActiveStreamCleanly(player)
                prepareFromCanonical(source, customUserAgent)
            }
        }
    }

    /**
     * Dipanggil oleh NetworkSwitchGuard saat Android berpindah Wi-Fi <-> Seluler.
     * Menutup soket lama bersih lalu menunggu 1.2 detik sebelum membuka sesi baru.
     */
    fun onNetworkSwitched(networkLabel: String) {
        val source = currentSource ?: return
        activeJob?.cancel()
        stabilityResetJob?.cancel()
        wasRecovering = true

        activeJob = scope.launch {
            _state.value = StabilizerState.SwitchingNetwork(networkLabel)
            streamMutex.withLock {
                connectionGuard.releaseActiveStreamCleanly(player, cooldownMs = 0L)
                delay(NETWORK_HANDOFF_DELAY_MS)
                prepareFromCanonical(source, currentUserAgent)
            }
        }
    }

    fun retryNow() {
        val source = currentSource ?: return
        playCanonical(source, currentUserAgent, debounceMs = 0L)
    }

    val isHandlingCurrentStream: Boolean
        get() = currentSource != null

    fun cancelTrackingWithoutStopping() {
        activeJob?.cancel()
        stabilityResetJob?.cancel()
        currentSource = null
        attemptCount = 0
        wasRecovering = false
        _state.value = StabilizerState.Idle
    }

    fun stopStream() {
        activeJob?.cancel()
        stabilityResetJob?.cancel()
        currentSource = null
        attemptCount = 0
        wasRecovering = false
        scope.launch {
            streamMutex.withLock {
                connectionGuard.releaseActiveStreamCleanly(player, cooldownMs = 0L)
                _state.value = StabilizerState.Idle
            }
        }
    }

    private fun launchReconnect(
        source: XtreamCanonicalSource,
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
                prepareFromCanonical(source, currentUserAgent)
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun prepareFromCanonical(
        source: XtreamCanonicalSource,
        customUserAgent: String?
    ) {
        val resolved = resolver.resolve(source)
        currentProtocol = resolved.protocol
        val factory = connectionGuard.createDataSourceFactory(customUserAgent)
        val mediaSource = resolver.buildMediaSource(resolved, factory)
        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true
    }

    private fun scheduleAttemptReset() {
        stabilityResetJob?.cancel()
        stabilityResetJob = scope.launch {
            delay(STABLE_PLAYBACK_RESET_MS)
            attemptCount = 0
        }
    }

    private fun verifyQuotaAndFail(source: XtreamCanonicalSource, error: PlaybackException) {
        activeJob?.cancel()
        activeJob = scope.launch {
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
            } else {
                _state.value = StabilizerState.Failed(
                    message = "Channel offline (${describeError(error)})"
                )
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun isHlsUnsupportedError(error: PlaybackException): Boolean {
        val code = error.errorCode
        if (code == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ||
            code == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ||
            code == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
        ) {
            return true
        }
        val httpCause = error.cause as? HttpDataSource.InvalidResponseCodeException
        return httpCause?.responseCode in listOf(404, 415)
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
        private const val NETWORK_HANDOFF_DELAY_MS = 1_200L
        private const val STABLE_PLAYBACK_RESET_MS = 10_000L
        private const val JITTER_MS = 300L
    }
}
