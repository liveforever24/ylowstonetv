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
import androidx.compose.foundation.shape.CircleShape
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
 * Menu tekan lama (long-press) 3-in-1 untuk Live TV, Film, Seri, & Pencarian:
 * 1. Salin Link Stream
 * 2. Cek Status Aktif / Delay / Mati
 * 3. Kelompokkan Kategori Tontonan (Sports, Event Pertandingan, Daftar Tontonan, atau grup kustom)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChannelLongPressDialog(
    channel: ChannelItem,
    playlistName: String,
    repo: IptvRepository,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onWatchGroupsChanged: () -> Unit
) {
    val c = LocalYlowColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isChecking by remember(channel.streamUrl) { mutableStateOf(false) }
    var healthResult by remember(channel.streamUrl) { mutableStateOf<StreamHealthResult?>(null) }

    var allGroups by remember(channel.streamUrl) { mutableStateOf(repo.db.getAllWatchGroupNames()) }
    var memberGroups by remember(channel.streamUrl) { mutableStateOf(repo.db.getGroupsForStream(channel.streamUrl)) }
    var customGroupInput by remember { mutableStateOf("") }

    fun runHealthCheck() {
        if (isChecking) return
        isChecking = true
        healthResult = null
        scope.launch {
            healthResult = repo.checkStreamHealth(channel.streamUrl)
            isChecking = false
        }
    }

    fun toggleGroup(groupName: String) {
        val clean = groupName.trim()
        if (clean.isEmpty()) return
        if (memberGroups.contains(clean)) {
            repo.db.removeFromWatchGroup(clean, channel.streamUrl)
            memberGroups = memberGroups - clean
            Toast.makeText(context, "${strings.removedFromGroup} $clean", Toast.LENGTH_SHORT).show()
        } else {
            repo.db.addToWatchGroup(clean, channel, playlistName)
            memberGroups = memberGroups + clean
            if (!allGroups.contains(clean)) {
                allGroups = allGroups + clean
            }
            Toast.makeText(context, "${strings.addedToGroup} $clean", Toast.LENGTH_SHORT).show()
        }
        onWatchGroupsChanged()
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
                            text = channel.streamUrl,
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

                // 3. Kelompokkan Kategori Tontonan
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.surfaceElevated)
                        .border(1.dp, c.glassBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Bookmarks, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                        Text(
                            text = strings.groupToCategory,
                            color = c.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        allGroups.forEach { grp ->
                            val selected = memberGroups.contains(grp)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) c.accent else c.surface)
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) c.accent else c.glassBorder,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { toggleGroup(grp) }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = if (selected) Icons.Rounded.Check else Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = if (selected) c.onAccent else c.textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = grp,
                                    color = if (selected) c.onAccent else c.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = customGroupInput,
                            onValueChange = { customGroupInput = it },
                            placeholder = { Text(strings.newGroupPlaceholder, fontSize = 11.sp) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val trimmed = customGroupInput.trim()
                                if (trimmed.isNotEmpty()) {
                                    if (!memberGroups.contains(trimmed)) {
                                        toggleGroup(trimmed)
                                    }
                                    customGroupInput = ""
                                }
                            },
                            enabled = customGroupInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(strings.addGroupBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
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
