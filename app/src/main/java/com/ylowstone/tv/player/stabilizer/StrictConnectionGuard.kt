package com.ylowstone.tv.player.stabilizer

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class Ipv4FirstDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        val ipv4 = addresses.filterIsInstance<Inet4Address>()
        return if (ipv4.isNotEmpty()) {
            ipv4 + addresses.filterNot { it is Inet4Address }
        } else {
            addresses
        }
    }
}

class InMemorySessionCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val host = url.host
        val existing = store.getOrPut(host) { mutableListOf() }
        synchronized(existing) {
            for (incoming in cookies) {
                existing.removeAll { it.name == incoming.name && it.path == incoming.path }
                if (incoming.expiresAt >= System.currentTimeMillis()) {
                    existing.add(incoming)
                }
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val list = store[url.host] ?: return emptyList()
        synchronized(list) {
            list.removeAll { it.expiresAt < now }
            return list.filter { it.matches(url) }
        }
    }

    fun clear() {
        store.clear()
    }
}

/**
 * Mengelola identitas HTTP seragam (IPv4-First DNS, CookieJar, User-Agent, dan header kustom M3U)
 * serta mendukung sumber lokal (file:// / content://) dalam satu DataSource.Factory universal.
 */
class StrictConnectionGuard(
    defaultUserAgent: String = DEFAULT_USER_AGENT
) {
    @Volatile
    private var activeUserAgent: String = defaultUserAgent.trim().ifEmpty { DEFAULT_USER_AGENT }

    private val dnsResolver = Ipv4FirstDns()
    private val sessionCookieJar = InMemorySessionCookieJar()

    private val streamConnectionPool = ConnectionPool(
        maxIdleConnections = 2,
        keepAliveDuration = 15,
        timeUnit = TimeUnit.SECONDS
    )

    val streamHttpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(dnsResolver)
        .cookieJar(sessionCookieJar)
        .connectionPool(streamConnectionPool)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request()
            val updated = req.newBuilder()
                .apply {
                    if (req.header("User-Agent").isNullOrBlank()) {
                        header("User-Agent", activeUserAgent)
                    }
                    header("Accept", "*/*")
                }
                .build()
            chain.proceed(updated)
        }
        .build()

    val apiHttpClient: OkHttpClient = streamHttpClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun updateUserAgent(customUserAgent: String?) {
        activeUserAgent = customUserAgent?.trim().takeUnless { it.isNullOrEmpty() }
            ?: DEFAULT_USER_AGENT
    }

    @OptIn(UnstableApi::class)
    fun createDataSourceFactory(
        customUserAgent: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): DataSource.Factory {
        updateUserAgent(customUserAgent)
        return OkHttpDataSource.Factory(streamHttpClient)
            .setUserAgent(activeUserAgent)
            .apply {
                if (extraHeaders.isNotEmpty()) {
                    setDefaultRequestProperties(extraHeaders)
                }
            }
    }

    /**
     * Membungkus OkHttpDataSource di dalam DefaultDataSource agar URL HTTP/HTTPS
     * maupun berkas lokal (file:// / content://) berjalan melalui satu jalur factory.
     */
    @OptIn(UnstableApi::class)
    fun createUniversalDataSourceFactory(
        context: Context,
        customUserAgent: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): DataSource.Factory {
        val httpFactory = createDataSourceFactory(customUserAgent, extraHeaders)
        return DefaultDataSource.Factory(context.applicationContext, httpFactory)
    }

    @OptIn(UnstableApi::class)
    fun createNaturalLiveLoadControl(): LoadControl {
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 30_000,
                /* bufferForPlaybackMs = */ 2_500,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    /**
     * Buffer lebih lapang untuk VOD (Film/Seri/Video Web) agar fitur seeking
     * dan Skip Intro MM:SS berjalan responsif.
     */
    @OptIn(UnstableApi::class)
    fun createVodSeekLoadControl(): LoadControl {
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 2_000,
                /* bufferForPlaybackAfterRebufferMs = */ 4_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    suspend fun releaseActiveStreamCleanly(
        player: ExoPlayer,
        cooldownMs: Long = DEFAULT_SWITCH_COOLDOWN_MS
    ) {
        withContext(Dispatchers.Main.immediate) {
            player.stop()
            player.clearMediaItems()
        }
        withContext(Dispatchers.IO) {
            streamHttpClient.dispatcher.cancelAll()
            streamConnectionPool.evictAll()
        }
        if (cooldownMs > 0L) {
            delay(cooldownMs)
        }
    }

    fun shutdown() {
        streamHttpClient.dispatcher.cancelAll()
        streamConnectionPool.evictAll()
        sessionCookieJar.clear()
    }

    companion object {
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
        const val DEFAULT_SWITCH_COOLDOWN_MS = 200L
    }
}
