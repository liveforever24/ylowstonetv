package com.ylowstone.tv.player.stabilizer

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

data class XtreamAccountStatus(
    val status: String,
    val activeConnections: Int,
    val maxConnections: Int,
    val expiresAtEpochSec: Long?,
    val isTrial: Boolean,
    val serverTimezone: String?
) {
    val isActive: Boolean
        get() = status.equals("Active", ignoreCase = true)

    val isQuotaFull: Boolean
        get() = maxConnections > 0 && activeConnections >= maxConnections

    fun formattedQuota(): String = "$activeConnections/$maxConnections"
}

class XtreamSessionMonitor(
    private val connectionGuard: StrictConnectionGuard
) {
    @Volatile
    private var cachedStatus: XtreamAccountStatus? = null

    @Volatile
    private var lastFetchMs: Long = 0L

    suspend fun fetchStatus(
        hostUrl: String,
        username: String,
        password: String,
        forceRefresh: Boolean = false
    ): XtreamAccountStatus? {
        val now = System.currentTimeMillis()
        val cached = cachedStatus
        if (!forceRefresh && cached != null && (now - lastFetchMs) < MIN_CACHE_TTL_MS) {
            return cached
        }

        return withContext(Dispatchers.IO) {
            val base = hostUrl.trim().trimEnd('/')
            val user = Uri.encode(username.trim())
            val pass = Uri.encode(password.trim())
            val url = "$base/player_api.php?username=$user&password=$pass"

            runCatching {
                val request = Request.Builder().url(url).get().build()
                connectionGuard.apiHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) return@use null
                    parseAccountStatus(body)
                }
            }.getOrNull()?.also { parsed ->
                cachedStatus = parsed
                lastFetchMs = System.currentTimeMillis()
            }
        }
    }

    private fun parseAccountStatus(rawJson: String): XtreamAccountStatus? {
        val root = JSONObject(rawJson)
        val userInfo = root.optJSONObject("user_info") ?: return null
        val serverInfo = root.optJSONObject("server_info")

        val status = userInfo.optString("status", "Unknown")
        val activeCons = userInfo.optString("active_cons", "0").toIntOrNull() ?: 0
        val maxCons = userInfo.optString("max_connections", "1").toIntOrNull() ?: 1
        val expDate = userInfo.optString("exp_date", "").toLongOrNull()
        val isTrial = userInfo.optString("is_trial", "0") == "1"
        val timezone = serverInfo?.optString("timezone")?.takeIf { it.isNotBlank() }

        return XtreamAccountStatus(
            status = status,
            activeConnections = activeCons,
            maxConnections = maxCons,
            expiresAtEpochSec = expDate,
            isTrial = isTrial,
            serverTimezone = timezone
        )
    }

    companion object {
        private const val MIN_CACHE_TTL_MS = 15_000L
    }
}
