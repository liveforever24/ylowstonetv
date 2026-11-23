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
import androidx.compose.ui.graphics.Brush
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
import com.ylowstone.tv.util.AppIconManager
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: AppSettings,
    strings: AppStrings,
    repo: IptvRepository,
    onBack: () -> Unit,
    onUpdateSettings: (AppSettings) -> Unit
) {
    val c = LocalYlowColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var expandedSection by remember { mutableStateOf("APPEARANCE") }
    var showChangePin by remember { mutableStateOf(false) }

    var checkingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateStatusMsg by remember { mutableStateOf("") }

    fun matches(vararg keys: String): Boolean {
        val q = searchQuery.trim()
        if (q.isEmpty()) return true
        return keys.any { it.contains(q, ignoreCase = true) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 66.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = strings.settings,
                color = c.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            // Search Settings Bar (BitChord-style!)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(strings.searchSettings, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // ---- GROUPED BITChORD-STYLE SETTINGS CARD ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(c.surface)
                    .border(1.dp, c.glassBorder, RoundedCornerShape(22.dp))
            ) {
                // 1. Device Mode
                if (matches(strings.deviceMode, "mode", "tv", "phone", "ponsel")) {
                    SettingsAccordionRow(
                        icon = Icons.Rounded.Devices,
                        title = strings.deviceMode,
                        subtitle = if (settings.deviceMode == DeviceMode.TV) strings.androidTv else strings.smartphone,
                        expanded = expandedSection == "DEVICE" || searchQuery.isNotBlank(),
                        onClick = { expandedSection = if (expandedSection == "DEVICE") "" else "DEVICE" }
                    ) {
                        SegmentedChoiceRow(
                            options = listOf(DeviceMode.SMARTPHONE to strings.smartphone, DeviceMode.TV to strings.androidTv),
                            selected = settings.deviceMode,
                            onSelect = { onUpdateSettings(settings.copy(deviceMode = it, hasSelectedMode = true)) }
                        )
                    }
                    SettingsDivider()
                }

                // 2. Appearance & 5 Elegant Color Styles
                if (matches(strings.appearanceTheme, strings.colorStyleTitle, "tema", "theme", "amoled", "warna", "color")) {
                    SettingsAccordionRow(
                        icon = Icons.Rounded.Palette,
                        title = strings.appearanceTheme,
                        subtitle = "${settings.themeMode.name} • ${settings.stylePreset.label}",
                        expanded = expandedSection == "APPEARANCE" || searchQuery.isNotBlank(),
                        onClick = { expandedSection = if (expandedSection == "APPEARANCE") "" else "APPEARANCE" }
                    ) {
                        SegmentedChoiceRow(
                            options = listOf(
                                ThemeMode.AMOLED to strings.themeAmoled,
                                ThemeMode.LIGHT to strings.themeLight,
                                ThemeMode.SYSTEM to strings.themeSystem
                            ),
                            selected = settings.themeMode,
                            onSelect = { onUpdateSettings(settings.copy(themeMode = it)) }
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(strings.colorStyleTitle, color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(StylePreset.entries) { preset ->
                                val active = settings.stylePreset == preset
                                val swatch = Color(preset.accentHex)
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (active) swatch.copy(alpha = 0.22f) else c.surfaceElevated)
                                        .border(if (active) 1.8.dp else 1.dp, if (active) swatch else c.glassBorder, RoundedCornerShape(14.dp))
                                        .clickable { onUpdateSettings(settings.copy(stylePreset = preset)) }
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(swatch))
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
                    SettingsDivider()
                }

                // 3. 12 Switchable 3D App Icons!
                if (matches(strings.appIconTitle, "icon", "ikon", "logo")) {
                    SettingsAccordionRow(
                        icon = Icons.Rounded.Apps,
                        title = strings.appIconTitle,
                        subtitle = settings.appIcon.label,
                        expanded = expandedSection == "ICONS" || searchQuery.isNotBlank(),
                        onClick = { expandedSection = if (expandedSection == "ICONS") "" else "ICONS" }
                    ) {
                        Text(strings.appIconDesc, color = c.textSecondary, fontSize = 11.sp)
                        Spacer(Modifier.height(10.dp))
                        // 4x3 Clean Visual Grid of all 12 Icons
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
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(if (selected) c.accent.copy(alpha = 0.16f) else c.surfaceElevated.copy(alpha = 0.6f))
                                                .border(
                                                    width = if (selected) 2.dp else 1.dp,
                                                    color = if (selected) c.accent else c.glassBorder,
                                                    shape = RoundedCornerShape(16.dp)
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
                    SettingsDivider()
                }

                // 4. Playback & Streaming (Gestures, External Player, HLS/TS)
                if (matches(strings.playbackSection, strings.playerGestures, "gestur", "volume", "hls", "external", "pemain")) {
                    SettingsAccordionRow(
                        icon = Icons.Rounded.PlayCircleOutline,
                        title = strings.playbackSection,
                        subtitle = settings.streamFormat.label,
                        expanded = expandedSection == "PLAYBACK" || searchQuery.isNotBlank(),
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
                                colors = SwitchDefaults.colors(checkedTrackColor = c.accent)
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
                                colors = SwitchDefaults.colors(checkedTrackColor = c.accent)
                            )
                        }
                        HorizontalDivider(color = c.glassBorder, modifier = Modifier.padding(vertical = 8.dp))
                        Text(strings.streamFormatTitle, color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        SegmentedChoiceRow(
                            options = listOf(StreamFormat.HLS to StreamFormat.HLS.label, StreamFormat.TS to StreamFormat.TS.label),
                            selected = settings.streamFormat,
                            onSelect = { onUpdateSettings(settings.copy(streamFormat = it)) }
                        )
                    }
                    SettingsDivider()
                }

                // 5. Privacy & Storage (PIN & Clear Cache)
                if (matches(strings.privacyStorageSection, "pin", "cache", "privasi", "storage")) {
                    SettingsAccordionRow(
                        icon = Icons.Rounded.Security,
                        title = strings.privacyStorageSection,
                        subtitle = "${strings.parentalPin} • Cache",
                        expanded = expandedSection == "PRIVACY" || searchQuery.isNotBlank(),
                        onClick = { expandedSection = if (expandedSection == "PRIVACY") "" else "PRIVACY" }
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { showChangePin = true }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
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
                                Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.clearCache, fontSize = 12.sp)
                            }
                        }
                    }
                    SettingsDivider()
                }

                // 6. Language
                if (matches(strings.appLanguage, "language", "bahasa", "english", "indonesia")) {
                    SettingsAccordionRow(
                        icon = Icons.Rounded.Language,
                        title = strings.appLanguage,
                        subtitle = if (settings.language == AppLanguage.ID) "Bahasa Indonesia" else "English (US)",
                        expanded = expandedSection == "LANGUAGE" || searchQuery.isNotBlank(),
                        onClick = { expandedSection = if (expandedSection == "LANGUAGE") "" else "LANGUAGE" }
                    ) {
                        SegmentedChoiceRow(
                            options = listOf(AppLanguage.ID to "Bahasa Indonesia", AppLanguage.EN to "English (US)"),
                            selected = settings.language,
                            onSelect = { onUpdateSettings(settings.copy(language = it)) }
                        )
                    }
                    SettingsDivider()
                }

                // 7. Check for Updates (Play-Protect friendly browser download)
                if (matches(strings.checkUpdates, "update", "versi", "version")) {
                    SettingsAccordionRow(
                        icon = Icons.Rounded.SystemUpdate,
                        title = strings.checkUpdates,
                        subtitle = "v${BuildConfig.VERSION_NAME}",
                        expanded = expandedSection == "UPDATE" || searchQuery.isNotBlank(),
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
                                colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(strings.checkUpdates, color = Color.White, fontSize = 12.sp)
                            }
                        }

                        updateInfo?.takeIf { it.hasUpdate }?.let { info ->
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    val url = info.apkDownloadUrl.ifBlank { info.htmlUrl }
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30D158)),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("${strings.downloadUpdateBrowser} (v${info.latestVersion})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ---- BITChORD-STYLE MINIMALIST FOOTER LINKS ----
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

        // ---- FLOATING TOP LIQUID GLASS BACK BUTTON (<) ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(c.bg, c.bg.copy(alpha = 0.82f), Color.Transparent)
                    )
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(c.glassBg)
                    .border(1.dp, c.glassHighlight, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "Back", tint = c.textPrimary, modifier = Modifier.size(26.dp))
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
private fun SettingsDivider() {
    val c = LocalYlowColors.current
    HorizontalDivider(color = c.glassBorder, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun SettingsAccordionRow(
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
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = title, tint = c.textPrimary, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (!expanded && subtitle.isNotBlank()) {
                    Text(subtitle, color = c.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
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
