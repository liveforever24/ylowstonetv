package com.ylowstone.tv.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import com.ylowstone.tv.BuildConfig
import com.ylowstone.tv.data.IptvRepository
import com.ylowstone.tv.i18n.AppStrings
import com.ylowstone.tv.model.*
import com.ylowstone.tv.ui.components.PinDialog
import com.ylowstone.tv.ui.components.YlowstoneLogoBadge
import com.ylowstone.tv.ui.theme.LocalYlowColors
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen(
    settings: AppSettings,
    strings: AppStrings,
    repo: IptvRepository,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onUpdateSettings: (AppSettings) -> Unit,
    onInstallApk: (File) -> Unit
) {
    val c = LocalYlowColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showChangePin by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var updateStatusMsg by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showBackButton) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = c.textPrimary)
                }
            }
            Text(strings.settings, color = c.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        // 1. Device Mode (Smartphone / TV)
        SettingsGroupCard(title = strings.deviceMode) {
            SegmentedChoiceRow(
                options = listOf(DeviceMode.SMARTPHONE to strings.smartphone, DeviceMode.TV to strings.androidTv),
                selected = settings.deviceMode,
                onSelect = { onUpdateSettings(settings.copy(deviceMode = it, hasSelectedMode = true)) }
            )
        }

        // 2. Base Theme (AMOLED / Light / System) + 5 Streaming Platform Style Presets
        SettingsGroupCard(title = strings.appearanceTheme) {
            SegmentedChoiceRow(
                options = listOf(
                    ThemeMode.AMOLED to strings.themeAmoled,
                    ThemeMode.LIGHT to strings.themeLight,
                    ThemeMode.SYSTEM to strings.themeSystem
                ),
                selected = settings.themeMode,
                onSelect = { onUpdateSettings(settings.copy(themeMode = it)) }
            )
            Spacer(Modifier.height(14.dp))
            Text(strings.stylePresetTitle, color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(StylePreset.entries) { preset ->
                    val active = settings.stylePreset == preset
                    val swatch = Color(preset.accentHex)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (active) swatch.copy(alpha = 0.22f) else c.surfaceElevated)
                            .border(
                                width = if (active) 2.dp else 1.dp,
                                color = if (active) swatch else c.glassBorder,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { onUpdateSettings(settings.copy(stylePreset = preset)) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(swatch)
                        )
                        Text(
                            text = preset.label,
                            color = if (active) c.textPrimary else c.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 3. Language (Bahasa Indonesia & English US)
        SettingsGroupCard(title = strings.appLanguage) {
            SegmentedChoiceRow(
                options = listOf(AppLanguage.ID to "Bahasa Indonesia", AppLanguage.EN to "English (US)"),
                selected = settings.language,
                onSelect = { onUpdateSettings(settings.copy(language = it)) }
            )
        }

        // 4. Player Settings: Hide/Show Gestures + External Player + Stream Format
        SettingsGroupCard(title = "Player & Streaming") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.playerGestures, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(strings.playerGesturesDesc, color = c.textSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = settings.showPlayerGestures,
                    onCheckedChange = { onUpdateSettings(settings.copy(showPlayerGestures = it)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = c.accent)
                )
            }

            HorizontalDivider(color = c.glassBorder, modifier = Modifier.padding(vertical = 10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.preferExternalTitle, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(strings.preferExternalDesc, color = c.textSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = settings.preferExternalPlayer,
                    onCheckedChange = { onUpdateSettings(settings.copy(preferExternalPlayer = it)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = c.accent)
                )
            }

            HorizontalDivider(color = c.glassBorder, modifier = Modifier.padding(vertical = 10.dp))

            Text(strings.streamFormatTitle, color = c.textSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            SegmentedChoiceRow(
                options = listOf(StreamFormat.HLS to StreamFormat.HLS.label, StreamFormat.TS to StreamFormat.TS.label),
                selected = settings.streamFormat,
                onSelect = { onUpdateSettings(settings.copy(streamFormat = it)) }
            )
        }

        // 5. Security (PIN) & Cache
        SettingsGroupCard(title = strings.parentalPin) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { showChangePin = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.changePin, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = {
                        context.imageLoader.memoryCache?.clear()
                        Toast.makeText(context, strings.cacheCleared, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.clearCache, fontSize = 12.sp)
                }
            }
        }

        // 6. Check for Updates & Current App Version
        SettingsGroupCard(title = strings.checkUpdates) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${strings.currentVersion}: v${BuildConfig.VERSION_NAME}", color = c.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    if (updateStatusMsg.isNotEmpty()) {
                        Text(updateStatusMsg, color = c.accent, fontSize = 12.sp)
                    }
                }
                Button(
                    enabled = !checkingUpdate && downloadProgress == null,
                    onClick = {
                        checkingUpdate = true
                        updateStatusMsg = strings.checkingUpdate
                        scope.launch {
                            val res = repo.checkGitHubUpdate()
                            checkingUpdate = false
                            res.onSuccess { info ->
                                updateInfo = info
                                updateStatusMsg = if (info.hasUpdate) "${strings.updateAvailable}: v${info.latestVersion}" else strings.upToDate
                            }.onFailure { err ->
                                updateStatusMsg = "Error: ${err.message}"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.checkUpdates, color = Color.White, fontSize = 12.sp)
                }
            }

            updateInfo?.takeIf { it.hasUpdate }?.let { info ->
                Spacer(Modifier.height(10.dp))
                if (downloadProgress != null) {
                    LinearProgressIndicator(
                        progress = { downloadProgress!! },
                        color = c.accent,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("${strings.downloadingApk} ${(downloadProgress!! * 100).toInt()}%", color = c.textSecondary, fontSize = 11.sp)
                } else {
                    Button(
                        onClick = {
                            downloadProgress = 0f
                            scope.launch {
                                val dl = repo.downloadApk(info.apkDownloadUrl) { p -> downloadProgress = p }
                                downloadProgress = null
                                dl.onSuccess { apkFile -> onInstallApk(apkFile) }
                                    .onFailure { Toast.makeText(context, "Download error: ${it.message}", Toast.LENGTH_LONG).show() }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30D158)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("${strings.downloadAndInstall} (v${info.latestVersion})", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 7. Bottom Credit & GitHub Repository Link
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(c.surface)
                .border(1.dp, c.glassBorder, RoundedCornerShape(20.dp))
                .clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/liveforever24/ylowstonetv"))
                    runCatching { context.startActivity(intent) }
                }
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            YlowstoneLogoBadge(size = 46.dp)
            Text("YlowstoneTV v${BuildConfig.VERSION_NAME}", color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(strings.aboutCredit, color = c.textSecondary, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Rounded.Public, contentDescription = null, tint = c.accent, modifier = Modifier.size(14.dp))
                Text(
                    text = "github.com/liveforever24/ylowstonetv",
                    color = c.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (showChangePin) {
        PinDialog(
            title = strings.changePin,
            expectedPin = null,
            strings = strings,
            onDismiss = { showChangePin = false },
            onConfirm = { newPin ->
                onUpdateSettings(settings.copy(pinCode = newPin))
                showChangePin = false
                Toast.makeText(context, strings.pinUpdated, Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun SettingsGroupCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalYlowColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .border(1.dp, c.glassBorder, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(title, color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun <T> SegmentedChoiceRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    val c = LocalYlowColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surfaceElevated)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (active) c.accent else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 9.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (active) Color.White else c.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}
