package com.ylowstone.tv.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ylowstone.tv.data.IptvRepository
import com.ylowstone.tv.i18n.AppStrings
import com.ylowstone.tv.model.ChannelItem
import com.ylowstone.tv.model.StreamHealthResult
import com.ylowstone.tv.model.StreamHealthState
import com.ylowstone.tv.ui.theme.LocalYlowColors
import kotlinx.coroutines.launch

/**
 * Menyembunyikan potongan username/password Xtream di teks preview UI agar aman saat di-screenshot,
 * sementara link asli utuh tetap disalin ke clipboard saat tombol ditekan.
 */
private fun maskStreamUrlForPreview(url: String): String {
    val clean = url.trim()
    val xtreamPattern = Regex("""(https?://[^/]+/(?:live|movie|series)/)([^/]+)/([^/]+)(/.*)""")
    val match = xtreamPattern.find(clean)
    if (match != null) {
        val prefix = match.groupValues[1]
        val suffix = match.groupValues[4]
        return "$prefix••••/••••$suffix"
    }
    return clean
}

/**
 * Menu tekan lama (long-press) ringkas v2.4.0 untuk Live TV, Film, Seri, & Pencarian:
 * 1. Salin Link Stream (preview tersamar, clipboard utuh)
 * 2. Cek Status Aktif / Delay / Mati
 * 3. Tambah / Hapus Favorit
 * (Fitur Kategori Tontonan / Watch Groups dihapus total sesuai desain v2.4.0)
 */
@Composable
fun ChannelLongPressDialog(
    channel: ChannelItem,
    playlistName: String,
    repo: IptvRepository,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onToggleFavorite: (ChannelItem) -> Unit
) {
    val c = LocalYlowColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isChecking by remember(channel.streamUrl) { mutableStateOf(false) }
    var healthResult by remember(channel.streamUrl) { mutableStateOf<StreamHealthResult?>(null) }
    var isFav by remember(channel.id, channel.isFavorite) { mutableStateOf(channel.isFavorite) }

    fun runHealthCheck() {
        if (isChecking) return
        isChecking = true
        healthResult = null
        scope.launch {
            healthResult = repo.checkStreamHealth(channel.streamUrl)
            isChecking = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        shape = RoundedCornerShape(22.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ChannelLogoBox(channel.logoUrl, 42.dp, corner = 10.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        color = c.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${playlistName.ifBlank { "YlowstoneTV" }} • ${channel.category}",
                        color = c.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Salin Link Stream
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.surfaceElevated)
                        .border(1.dp, c.glassBorder, RoundedCornerShape(14.dp))
                        .clickable {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText(channel.name, channel.streamUrl))
                            Toast.makeText(context, strings.copied, Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.accentContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.copyStreamLink,
                            color = c.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = maskStreamUrlForPreview(channel.streamUrl),
                            color = c.textSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 2. Cek Status Stream (Aktif / Delay / Mati)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.surfaceElevated)
                        .border(1.dp, c.glassBorder, RoundedCornerShape(14.dp))
                        .clickable(enabled = !isChecking) { runHealthCheck() }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.accentContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isChecking) {
                                CircularProgressIndicator(
                                    color = c.accent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(Icons.Rounded.NetworkCheck, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.checkStreamStatus,
                                color = c.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isChecking) strings.checkingStreamStatus
                                else if (healthResult == null) "${strings.statusActive} / ${strings.statusDelay} / ${strings.statusDead}"
                                else strings.recheckStatus,
                                color = c.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    healthResult?.let { res ->
                        val (statusColor, statusLabel, statusIcon) = when (res.state) {
                            StreamHealthState.ACTIVE -> Triple(Color(0xFF22C55E), strings.statusActive, Icons.Rounded.CheckCircle)
                            StreamHealthState.DELAY -> Triple(Color(0xFFF59E0B), strings.statusDelay, Icons.Rounded.Schedule)
                            StreamHealthState.DEAD -> Triple(Color(0xFFEF4444), strings.statusDead, Icons.Rounded.Cancel)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(statusColor.copy(alpha = 0.14f))
                                .border(1.dp, statusColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
                            Text(
                                text = statusLabel,
                                color = statusColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val meta = buildString {
                                if (res.latencyMs > 0L) append("${res.latencyMs} ms")
                                if (res.detail.isNotBlank()) {
                                    if (isNotEmpty()) append(" • ")
                                    append(res.detail)
                                }
                            }
                            if (meta.isNotBlank()) {
                                Text(
                                    text = meta,
                                    color = c.textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 3. Tambah / Hapus Favorit
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.surfaceElevated)
                        .border(
                            width = 1.dp,
                            color = if (isFav) c.accent.copy(alpha = 0.55f) else c.glassBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            onToggleFavorite(channel.copy(isFavorite = isFav))
                            isFav = !isFav
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isFav) c.accent.copy(alpha = 0.22f) else c.accentContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = null,
                            tint = c.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        val isIndo = strings.watchHistory.startsWith("Riwayat", ignoreCase = true)
                        Text(
                            text = if (isFav) {
                                if (isIndo) "Hapus dari Favorit" else "Remove from Favourites"
                            } else {
                                if (isIndo) "Simpan ke Favorit" else "Add to Favourites"
                            },
                            color = c.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = strings.favouriteChannels,
                            color = c.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.closeBtn, color = c.accent, fontWeight = FontWeight.Bold)
            }
        }
    )
}
