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
import okhttp3.Request
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Resolver DNS-over-HTTPS (DoH) + Fast-IP Pinning (IPv4-first) untuk YlowstoneTV Stabilizer v2.0.
 * Menembus pemblokiran/redirect DNS ISP dan mengunci rute IPv4 tercepat selama sesi berlangsung.
 */
class Ipv4FirstDns(
    @Volatile var dohEnabled: Boolean = true
) : Dns {

    private data class PinnedAddresses(
        val addresses: List<InetAddress>,
        val expiresAtMs: Long
    )

    private val pinCache = ConcurrentHashMap<String, PinnedAddresses>()

    private val dohClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val cleanHost = hostname.trim().lowercase()
        if (cleanHost.isEmpty()) return Dns.SYSTEM.lookup(hostname)

        // Jika sudah berupa IP literal, langsung kembalikan tanpa query DoH
        if (IP_LITERAL_REGEX.matches(cleanHost)) {
            return listOf(InetAddress.getByName(cleanHost))
        }

        val now = System.currentTimeMillis()
        pinCache[cleanHost]?.let { cached ->
            if (now < cached.expiresAtMs && cached.addresses.isNotEmpty()) {
                return cached.addresses
            }
        }

        val resolved = if (dohEnabled) {
            queryDohIpv4(cleanHost) ?: systemIpv4First(cleanHost)
        } else {
            systemIpv4First(cleanHost)
        }

        if (resolved.isNotEmpty()) {
            pinCache[cleanHost] = PinnedAddresses(resolved, now + PIN_TTL_MS)
        }
        return resolved
    }

    fun prewarmHost(hostname: String) {
        runCatching { lookup(hostname) }
    }

    fun clearPins() {
        pinCache.clear()
    }

    private fun systemIpv4First(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        val ipv4 = addresses.filterIsInstance<Inet4Address>()
        return if (ipv4.isNotEmpty()) {
            ipv4 + addresses.filterNot { it is Inet4Address }
        } else {
            addresses
        }
    }

    private fun queryDohIpv4(hostname: String): List<InetAddress>? {
        for (endpoint in DOH_ENDPOINTS) {
            val ips = runCatching {
                val req = Request.Builder()
                    .url("$endpoint?name=$hostname&type=A")
                    .header("Accept", "application/dns-json")
                    .get()
                    .build()
                dohClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string().orEmpty()
                    if (body.isBlank()) return@use null
                    val json = JSONObject(body)
                    val answers = json.optJSONArray("Answer") ?: return@use null
                    val out = mutableListOf<InetAddress>()
                    for (i in 0 until answers.length()) {
                        val ans = answers.optJSONObject(i) ?: continue
                        if (ans.optInt("type", 0) == 1) {
                            val ip = ans.optString("data", "").trim()
                            if (IP_LITERAL_REGEX.matches(ip)) {
                                out.add(InetAddress.getByName(ip))
                            }
                        }
                    }
                    out.takeIf { it.isNotEmpty() }
                }
            }.getOrNull()
            if (!ips.isNullOrEmpty()) return ips
        }
        return null
    }

    companion object {
        private const val PIN_TTL_MS = 10L * 60L * 1000L
        private val IP_LITERAL_REGEX = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
        private val DOH_ENDPOINTS = listOf(
            "https://1.1.1.1/dns-query",
            "https://9.9.9.9:5053/dns-query"
        )
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

    fun clearHost(host: String) {
        store.remove(host)
    }

    fun clear() {
        store.clear()
    }
}

/**
 * Pengelola identitas HTTP seragam v2.0 (DoH + Fast-IP Pinning, CookieJar, Consistent Session Fingerprint,
 * Dual Buffer Profile Live vs VOD, dan Graceful Connection Teardown anti ghost-connection).
 */
class StrictConnectionGuard(
    defaultUserAgent: String = DEFAULT_USER_AGENT,
    dohEnabled: Boolean = true
) {
    @Volatile
    private var activeUserAgent: String = defaultUserAgent.trim().ifEmpty { DEFAULT_USER_AGENT }

    private val sessionFingerprintId: String = UUID.randomUUID().toString()
    private val dnsResolver = Ipv4FirstDns(dohEnabled = dohEnabled)
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
                    header("Accept-Encoding", "identity")
                    header("Connection", "keep-alive")
                    header("X-Playback-Session-Id", sessionFingerprintId)
                }
                .build()
            chain.proceed(updated)
        }
        .build()

    val apiHttpClient: OkHttpClient = streamHttpClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun setDohEnabled(enabled: Boolean) {
        dnsResolver.dohEnabled = enabled
        dnsResolver.clearPins()
    }

    fun prewarmHost(rawUrl: String) {
        val host = runCatching { android.net.Uri.parse(rawUrl).host }.getOrNull() ?: return
        if (host.isNotBlank()) {
            dnsResolver.prewarmHost(host)
        }
    }

    fun clearSessionCookiesForHost(rawUrl: String) {
        val host = runCatching { android.net.Uri.parse(rawUrl).host }.getOrNull()
        if (!host.isNullOrBlank()) {
            sessionCookieJar.clearHost(host)
        }
    }

    fun clearDnsPins() {
        dnsResolver.clearPins()
    }

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

    @OptIn(UnstableApi::class)
    fun createUniversalDataSourceFactory(
        context: Context,
        customUserAgent: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): DataSource.Factory {
        val httpFactory = createDataSourceFactory(customUserAgent, extraHeaders)
        return DefaultDataSource.Factory(context.applicationContext, httpFactory)
    }

    /**
     * Profil buffer cepat khusus Live TV (~1,2 detik start agar zapping instan).
     */
    @OptIn(UnstableApi::class)
    fun createNaturalLiveLoadControl(): LoadControl {
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 10_000,
                /* maxBufferMs = */ 25_000,
                /* bufferForPlaybackMs = */ 1_200,
                /* bufferForPlaybackAfterRebufferMs = */ 2_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    /**
     * Profil buffer besar khusus VOD (Film/Seri) agar anti-stutter & seeking responsif.
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

    /**
     * Graceful Connection Teardown: hentikan player, batalkan seluruh request aktif,
     * bersihkan connection pool agar socket lama benar-benar tertutup di sisi server Xtream,
     * lalu beri jeda cool-down adaptif sebelum membuka koneksi baru.
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
        dnsResolver.clearPins()
    }

    companion object {
        const val DEFAULT_USER_AGENT = "YlowstoneTV/2.5.0"
        const val DEFAULT_SWITCH_COOLDOWN_MS = 350L
    }
}
