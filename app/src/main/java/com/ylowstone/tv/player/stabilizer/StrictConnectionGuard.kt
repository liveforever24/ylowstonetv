package com.ylowstone.tv.player.stabilizer

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
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

/**
 * Resolver DNS yang memprioritaskan IPv4 agar perangkat dual-stack tidak
 * berpindah-pindah antara IPv4 dan IPv6 di tengah sesi streaming.
 */
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

/**
 * CookieJar ringan dalam memori agar cookie sesi dari handshake/redirect
 * tetap konsisten antara API Xtream dan segmen media.
 */
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
 * Mengelola identitas HTTP seragam (User-Agent, IPv4-First DNS, CookieJar)
 * serta pembersihan soket ketat untuk mencegah ghost connection pada akun 1-2 IP.
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
                .header("User-Agent", activeUserAgent)
                .header("Accept", "*/*")
                .build()
            chain.proceed(updated)
        }
        .build()

    /**
     * Client khusus metadata API (player_api.php) yang berbagi DNS IPv4-first,
     * CookieJar, dan User-Agent identik dengan streamHttpClient agar server melihat
     * satu identitas klien yang konsisten.
     */
    val apiHttpClient: OkHttpClient = streamHttpClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun updateUserAgent(customUserAgent: String?) {
        activeUserAgent = customUserAgent?.trim().takeUnless { it.isNullOrEmpty() }
            ?: DEFAULT_USER_AGENT
    }

    @OptIn(UnstableApi::class)
    fun createDataSourceFactory(customUserAgent: String? = null): DataSource.Factory {
        updateUserAgent(customUserAgent)
        return OkHttpDataSource.Factory(streamHttpClient)
            .setUserAgent(activeUserAgent)
    }

    /**
     * Konfigurasi buffer Media3 yang natural untuk Live TV agar tidak menarik
     * segmen secara agresif namun tetap tahan fluktuasi jaringan ringan.
     */
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
     * Memutus stream aktif secara bersih sebelum membuka channel baru atau reconnect.
     */
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
