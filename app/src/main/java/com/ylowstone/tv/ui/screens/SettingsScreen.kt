package com.ylowstone.tv.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import com.ylowstone.tv.BuildConfig
import com.ylowstone.tv.data.IptvRepository
import com.ylowstone.tv.i18n.AppStrings
import com.ylowstone.tv.model.*
import com.ylowstone.tv.ui.components.PinDialog
import com.ylowstone.tv.ui.theme.LocalYlowColors
import com.ylowstone.tv.ui.theme.resolveAccentTokens
import com.ylowstone.tv.util.AppIconManager
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: AppSettings,
    lockedCategories: Set<String>,
    strings: AppStrings,
    repo: IptvRepository,
    onBack: () -> Unit,
    onUpdateSettings: (AppSettings) -> Unit,
    onUnlockCategory: (String) -> Unit,
    onResetPinAndLocks: () -> Unit
) {
    val c = LocalYlowColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var expandedSection by remember { mutableStateOf("THEME") }
    var showChangePin by remember { mutableStateOf(false) }
    var showResetPinConfirm by remember { mutableStateOf(false) }
    var unlockTargetCat by remember { mutableStateOf<String?>(null) }

    var checkingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateStatusMsg by remember { mutableStateOf("") }

    val themeLabel = when (settings.themeMode) {
        ThemeMode.SYSTEM -> strings.themeSystem
        ThemeMode.LIGHT -> strings.themeLight
        ThemeMode.AMOLED -> strings.themeAmoled
    }

    val favLayoutLabel = when (settings.favoriteViewMode) {
        FavoriteViewMode.GRID -> strings.viewModeGrid
        FavoriteViewMode.LIST -> strings.viewModeList
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (c.isDark) Color(0xFF0C0E10) else c.surface)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = c.textPrimary)
            }
            Text(
                text = strings.settings,
                color = c.textPrimary,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )
        }
        HorizontalDivider(color = c.glassBorder, thickness = 0.6.dp)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            YlowSettingsRow(
                icon = Icons.Rounded.Contrast,
                title = strings.appearanceTheme,
                subtitle = "$themeLabel • ${settings.accentPreset.label}",
                expanded = expandedSection == "THEME",
                onClick = { expandedSection = if (expandedSection == "THEME") "" else "THEME" }
            ) {
                SegmentedChoiceRow(
                    options = listOf(
                        ThemeMode.SYSTEM to strings.themeSystem,
                        ThemeMode.LIGHT to strings.themeLight,
                        ThemeMode.AMOLED to strings.themeAmoled
                    ),
                    selected = settings.themeMode,
                    onSelect = { onUpdateSettings(settings.copy(themeMode = it)) }
                )

                Spacer(Modifier.height(12.dp))
                Text("Aksen Warna Minimalis", color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentPreset.entries.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { preset ->
                                val (swatch, _, _) = resolveAccentTokens(preset, c.isDark)
                                val active = settings.accentPreset == preset
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (active) c.accentContainer else c.surface)
                                        .border(
                                            width = if (active) 1.5.dp else 1.dp,
                                            color = if (active) c.accent else c.glassBorder,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onUpdateSettings(settings.copy(accentPreset = preset)) }
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
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
                                        color = if (active) c.accent else c.textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            YlowSettingsRow(
                icon = Icons.Rounded.DashboardCustomize,
                title = strings.favoriteLayoutTitle,
                subtitle = favLayoutLabel,
                expanded = expandedSection == "FAV_LAYOUT",
                onClick = { expandedSection = if (expandedSection == "FAV_LAYOUT") "" else "FAV_LAYOUT" }
            ) {
                SegmentedChoiceRow(
                    options = listOf(
                        FavoriteViewMode.GRID to strings.viewModeGrid,
                        FavoriteViewMode.LIST to strings.viewModeList
                    ),
                    selected = settings.favoriteViewMode,
                    onSelect = { onUpdateSettings(settings.copy(favoriteViewMode = it)) }
                )
            }

            YlowSettingsRow(
                icon = Icons.Rounded.Devices,
                title = strings.deviceMode,
                subtitle = if (settings.deviceMode == DeviceMode.TV) strings.androidTv else strings.smartphone,
                expanded = expandedSection == "DEVICE",
                onClick = { expandedSection = if (expandedSection == "DEVICE") "" else "DEVICE" }
            ) {
                SegmentedChoiceRow(
                    options = listOf(DeviceMode.SMARTPHONE to strings.smartphone, DeviceMode.TV to strings.androidTv),
                    selected = settings.deviceMode,
                    onSelect = { onUpdateSettings(settings.copy(deviceMode = it, hasSelectedMode = true)) }
                )
            }

            YlowSettingsRow(
                icon = Icons.Rounded.Apps,
                title = strings.appIconTitle,
                subtitle = settings.appIcon.label,
                expanded = expandedSection == "ICONS",
                onClick = { expandedSection = if (expandedSection == "ICONS") "" else "ICONS" }
            ) {
                Text(strings.appIconDesc, color = c.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                val rows = AppIconOption.entries.chunked(4)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { rowIcons ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowIcons.forEach { iconOpt ->
                                val selected = settings.appIcon == iconOpt
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (selected) c.accentContainer else c.surface)
                                        .border(
                                            width = if (selected) 1.8.dp else 1.dp,
                                            color = if (selected) c.accent else c.glassBorder,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            if (!selected) {
                                                onUpdateSettings(settings.copy(appIcon = iconOpt))
                                                Toast.makeText(context, strings.iconSwitchedToast, Toast.LENGTH_SHORT).show()
                                                AppIconManager.applyIcon(context, iconOpt)
                                            }
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = iconOpt.previewRes),
                                        contentDescription = iconOpt.label,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(44.dp).clip(CircleShape)
                                    )
                                    Text(
                                        text = iconOpt.label,
                                        color = if (selected) c.accent else c.textSecondary,
                                        fontSize = 9.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            YlowSettingsRow(
                icon = Icons.Rounded.Lock,
                title = strings.privacyStorageSection,
                subtitle = "${strings.parentalPin} (${lockedCategories.size})",
                expanded = expandedSection == "PIN",
                onClick = { expandedSection = if (expandedSection == "PIN") "" else "PIN" }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { showChangePin = true },
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Icon(Icons.Rounded.Pin, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.setOrChangePin, color = c.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { showResetPinConfirm = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(0.9f)
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.resetPinBtn, fontSize = 11.sp, maxLines = 1)
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(strings.lockedCategoriesTitle, color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))

                if (lockedCategories.isEmpty()) {
                    Text(strings.noLockedCategories, color = c.textSecondary.copy(alpha = 0.7f), fontSize = 12.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        lockedCategories.sorted().forEach { catName ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(c.surface)
                                    .border(1.dp, c.glassBorder, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = c.accent, modifier = Modifier.size(15.dp))
                                    Text(catName, color = c.textPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                TextButton(
                                    onClick = { unlockTargetCat = catName },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(strings.unlockCategory, color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        context.imageLoader.memoryCache?.clear()
                        Toast.makeText(context, strings.cacheCleared, Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.clearCache, fontSize = 12.sp)
                }
            }

            YlowSettingsRow(
                icon = Icons.Rounded.PlayCircleOutline,
                title = strings.playbackSection,
                subtitle = "${settings.streamFormat.label} • ${settings.bufferProfile.label}",
                expanded = expandedSection == "PLAYBACK",
                onClick = { expandedSection = if (expandedSection == "PLAYBACK") "" else "PLAYBACK" }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.playerGestures, color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(strings.playerGesturesDesc, color = c.textSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = settings.showPlayerGestures,
                        onCheckedChange = { onUpdateSettings(settings.copy(showPlayerGestures = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = c.onAccent,
                            checkedTrackColor = c.accent
                        )
                    )
                }

                HorizontalDivider(color = c.glassBorder, modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Subtitle Film & Seri", color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Otomatis pasang bahasa terakhir dipilih (${settings.preferredSubtitleLang.uppercase()}) tiap episode/film",
                            color = c.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = settings.autoSubtitleEnabled,
                        onCheckedChange = { onUpdateSettings(settings.copy(autoSubtitleEnabled = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = c.onAccent,
                            checkedTrackColor = c.accent
                        )
                    )
                }

                HorizontalDivider(color = c.glassBorder, modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.preferExternalTitle, color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(strings.preferExternalDesc, color = c.textSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = settings.preferExternalPlayer,
                        onCheckedChange = { onUpdateSettings(settings.copy(preferExternalPlayer = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = c.onAccent,
                            checkedTrackColor = c.accent
                        )
                    )
                }

                HorizontalDivider(color = c.glassBorder, modifier = Modifier.padding(vertical = 8.dp))

                Text(strings.streamFormatTitle, color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                SegmentedChoiceRow(
                    options = listOf(
                        StreamFormat.AUTO to "Otomatis",
                        StreamFormat.HLS to "HLS (.m3u8)",
                        StreamFormat.TS to "TS (.ts)"
                    ),
                    selected = settings.streamFormat,
                    onSelect = { onUpdateSettings(settings.copy(streamFormat = it)) }
                )

                Spacer(Modifier.height(10.dp))
                Text("Profil Buffer & Respons Live", color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                SegmentedChoiceRow(
                    options = listOf(
                        BufferProfile.LOW_LATENCY to "Cepat",
                        BufferProfile.BALANCED to "Seimbang",
                        BufferProfile.ANTI_BUFFER to "Anti-Buffer"
                    ),
                    selected = settings.bufferProfile,
                    onSelect = { onUpdateSettings(settings.copy(bufferProfile = it)) }
                )
            }

            YlowSettingsRow(
                icon = Icons.Rounded.Language,
                title = strings.appLanguage,
                subtitle = if (settings.language == AppLanguage.ID) "Bahasa Indonesia" else "English (US)",
                expanded = expandedSection == "LANGUAGE",
                onClick = { expandedSection = if (expandedSection == "LANGUAGE") "" else "LANGUAGE" }
            ) {
                SegmentedChoiceRow(
                    options = listOf(AppLanguage.ID to "Bahasa Indonesia", AppLanguage.EN to "English (US)"),
                    selected = settings.language,
                    onSelect = { onUpdateSettings(settings.copy(language = it)) }
                )
            }

            YlowSettingsRow(
                icon = Icons.Rounded.SystemUpdate,
                title = strings.checkUpdates,
                subtitle = "v${BuildConfig.VERSION_NAME}",
                expanded = expandedSection == "UPDATE",
                onClick = { expandedSection = if (expandedSection == "UPDATE") "" else "UPDATE" }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${strings.currentVersion}: v${BuildConfig.VERSION_NAME}", color = c.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        if (updateStatusMsg.isNotEmpty()) {
                            Text(updateStatusMsg, color = c.accent, fontSize = 11.sp)
                        }
                    }
                    Button(
                        enabled = !checkingUpdate,
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
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(strings.checkUpdates, color = c.onAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                updateInfo?.takeIf { it.hasUpdate }?.let { info ->
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val url = info.apkDownloadUrl.ifBlank { info.htmlUrl }
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("${strings.downloadUpdateBrowser} (v${info.latestVersion})", color = c.onAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ylowstonetv ${BuildConfig.VERSION_NAME}",
                        color = c.textSecondary,
                        fontSize = 12.sp
                    )
                    FooterLinkText("GitHub") {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/liveforever24/ylowstonetv"))) }
                    }
                    FooterLinkText("Developer") {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/liveforever24"))) }
                    }
                    FooterLinkText("Releases") {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/liveforever24/ylowstonetv/releases"))) }
                    }
                }
                Text(
                    text = "~Native Compose & Media3 IPTV Engine",
                    color = c.textSecondary.copy(alpha = 0.72f),
                    fontSize = 11.sp
                )
            }
        }
    }

    if (showChangePin) {
        PinDialog(
            title = strings.setOrChangePin,
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

    unlockTargetCat?.let { cat ->
        PinDialog(
            title = "${strings.unlockCategory}: $cat",
            expectedPin = settings.pinCode,
            strings = strings,
            onDismiss = { unlockTargetCat = null },
            onConfirm = {
                onUnlockCategory(cat)
                unlockTargetCat = null
            }
        )
    }

    if (showResetPinConfirm) {
        AlertDialog(
            onDismissRequest = { showResetPinConfirm = false },
            containerColor = c.surface,
            title = { Text(strings.resetPinConfirmTitle, color = c.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(strings.resetPinConfirmBody, color = c.textSecondary, fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        onResetPinAndLocks()
                        showResetPinConfirm = false
                        Toast.makeText(context, strings.pinResetDone, Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent)
                ) {
                    Text(strings.resetPinBtn, color = c.onAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetPinConfirm = false }) {
                    Text(strings.cancel, color = c.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun FooterLinkText(label: String, onClick: () -> Unit) {
    val c = LocalYlowColors.current
    Text(
        text = label,
        color = c.textPrimary.copy(alpha = 0.85f),
        fontSize = 12.sp,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun YlowSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LocalYlowColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Icon(icon, contentDescription = title, tint = c.textPrimary, modifier = Modifier.size(23.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (!expanded && subtitle.isNotBlank()) {
                    Text(subtitle, color = c.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(
                if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = c.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
            ) {
                content()
            }
        }
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
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface)
            .border(1.dp, c.glassBorder, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) c.accent else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 9.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (active) c.onAccent else c.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}
