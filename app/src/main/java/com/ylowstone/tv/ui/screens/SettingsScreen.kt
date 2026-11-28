package com.ylowstone.tv.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.ylowstone.tv.BuildConfig
import com.ylowstone.tv.data.AppPrefs
import com.ylowstone.tv.data.BackupManager
import com.ylowstone.tv.data.IptvRepository
import com.ylowstone.tv.i18n.AppStrings
import com.ylowstone.tv.model.*
import com.ylowstone.tv.ui.components.BoomerangLoadingIndicator
import com.ylowstone.tv.ui.components.PinDialog
import com.ylowstone.tv.ui.theme.LocalYlowColors
import com.ylowstone.tv.ui.theme.themePaletteSwatchColor
import com.ylowstone.tv.util.AppIconManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    lockedCategories: Set<String>,
    strings: AppStrings,
    prefs: AppPrefs,
    repo: IptvRepository,
    onBack: () -> Unit,
    onUpdateSettings: (AppSettings) -> Unit,
    onUnlockCategory: (String) -> Unit,
    onResetPinAndLocks: () -> Unit,
    isAdminLoggedIn: Boolean = false,
    isFullAdmin: Boolean = false,
    onLogoutAdmin: () -> Unit = {},
    onBackupRestored: (AppSettings) -> Unit = {}
) {
    val c = LocalYlowColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isIndonesian = settings.language == AppLanguage.ID

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            BackupManager.exportToUri(context, uri, prefs, repo.db)
                .onSuccess { count ->
                    val msg = if (isIndonesian) {
                        "${strings.backupExportSuccess} ($count playlist terkunci)"
                    } else {
                        "${strings.backupExportSuccess} ($count encrypted playlists)"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                .onFailure { Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            BackupManager.importFromUri(context, uri, prefs, repo.db)
                .onSuccess { result ->
                    onBackupRestored(result.settings)
                    val msg = if (isIndonesian) {
                        "${strings.backupImportSuccess} (${result.playlistsRestored} playlist)"
                    } else {
                        "${strings.backupImportSuccess} (${result.playlistsRestored} playlists)"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                .onFailure { Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    var expandedSection by remember { mutableStateOf("") }
    var showChangePin by remember { mutableStateOf(false) }
    var showResetPinConfirm by remember { mutableStateOf(false) }
    var unlockTargetCat by remember { mutableStateOf<String?>(null) }

    var checkingUpdate by remember { mutableStateOf(false) }
    var showUpdatePopup by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateErrorMsg by remember { mutableStateOf("") }

    fun triggerUpdateCheck() {
        if (checkingUpdate) return
        checkingUpdate = true
        updateErrorMsg = ""
        scope.launch {
            val res = repo.checkGitHubUpdate()
            checkingUpdate = false
            res.onSuccess { info ->
                updateInfo = info
                updateErrorMsg = ""
                showUpdatePopup = true
            }.onFailure { err ->
                updateInfo = null
                updateErrorMsg = err.message ?: if (isIndonesian) "Gagal memeriksa pembaruan" else "Failed to check updates"
                showUpdatePopup = true
            }
        }
    }

    val themeLabel = when (settings.themeMode) {
        ThemeMode.SYSTEM -> if (isIndonesian) "Sistem (Otomatis)" else "System (Auto)"
        ThemeMode.AMOLED -> if (isIndonesian) "Gelap" else "Dark"
        ThemeMode.LIGHT -> if (isIndonesian) "Cerah" else "Light"
        ThemeMode.AMBER_DUSK -> "Amber Dusk"
        ThemeMode.VELVET_CRIMSON -> "Velvet Crimson"
    }

    val favLayoutLabel = when (settings.favoriteViewMode) {
        FavoriteViewMode.GRID -> strings.viewModeGrid
        FavoriteViewMode.LIST -> strings.viewModeList
    }

    val streamFormatLabel = when (settings.streamFormat) {
        StreamFormat.AUTO -> strings.streamFormatAuto
        StreamFormat.HLS -> StreamFormat.HLS.label
        StreamFormat.TS -> StreamFormat.TS.label
    }

    val softMintGreen = Color(0xFF6EE7B7)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .systemBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ---- Top Header ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface)
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
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (isFullAdmin) {
                    Row(
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(c.accentContainer)
                            .border(1.dp, c.accent.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = c.accent, modifier = Modifier.size(13.dp))
                        Text("Admin", color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            HorizontalDivider(color = c.glassBorder, thickness = 0.6.dp)

            // ---- Scrollable Settings List ----
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp)
            ) {
                // 1. 5 Pilihan Tema Palet Terpadu
                YlowSettingsRow(
                    icon = Icons.Rounded.Contrast,
                    title = strings.appearanceTheme,
                    subtitle = themeLabel,
                    expanded = expandedSection == "THEME",
                    onClick = { expandedSection = if (expandedSection == "THEME") "" else "THEME" }
                ) {
                    val paletteOptions = listOf(
                        ThemeMode.SYSTEM to (if (isIndonesian) "Sistem (Otomatis)" else "System (Auto)"),
                        ThemeMode.AMOLED to (if (isIndonesian) "Gelap" else "Dark"),
                        ThemeMode.LIGHT to (if (isIndonesian) "Cerah" else "Light"),
                        ThemeMode.AMBER_DUSK to "Amber Dusk",
                        ThemeMode.VELVET_CRIMSON to "Velvet Crimson"
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        paletteOptions.forEach { (mode, label) ->
                            val selected = settings.themeMode == mode
                            val swatch = themePaletteSwatchColor(mode)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) c.accentContainer else c.surface)
                                    .border(
                                        width = if (selected) 1.6.dp else 1.dp,
                                        color = if (selected) c.accent else c.glassBorder,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onUpdateSettings(settings.copy(themeMode = mode)) }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(13.dp)
                                        .clip(CircleShape)
                                        .background(swatch)
                                        .border(0.8.dp, c.textPrimary.copy(alpha = 0.25f), CircleShape)
                                )
                                Text(
                                    text = label,
                                    color = if (selected) c.accent else c.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = c.glassBorder)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(strings.loadingAnimTitle, color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        BoomerangLoadingIndicator(style = settings.loadingAnimStyle, size = 34.dp)
                    }
                    Spacer(Modifier.height(8.dp))
                    SegmentedChoiceRow(
                        options = listOf(
                            LoadingAnimStyle.CHROMA_BOOMERANG to strings.loadingAnimChroma,
                            LoadingAnimStyle.YLLOWSTONE_EMBER to strings.loadingAnimAmber,
                            LoadingAnimStyle.AURORA_EMERALD to strings.loadingAnimAurora
                        ),
                        selected = settings.loadingAnimStyle,
                        onSelect = { onUpdateSettings(settings.copy(loadingAnimStyle = it)) }
                    )
                }

                // 2. Tampilan Saluran Favorit di Beranda
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

                // 3. Device Mode (Phone / TV)
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

                // 4. 12 Switchable 3D App Icons
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

                // 5. Category PIN & Encrypted Backup/Restore
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

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = c.glassBorder)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (isIndonesian) "${strings.backupSectionTitle} (Terenkripsi AES-256)" else "${strings.backupSectionTitle} (AES-256 Encrypted)",
                        color = c.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { exportBackupLauncher.launch("ylowstonetv_backup_v253.json") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(strings.exportBackupBtn, fontSize = 11.sp, maxLines = 1)
                        }
                        Button(
                            onClick = { importBackupLauncher.launch(arrayOf("application/json", "*/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(strings.importBackupBtn, color = c.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }

                // 6. Playback & Streaming
                YlowSettingsRow(
                    icon = Icons.Rounded.PlayCircleOutline,
                    title = strings.playbackSection,
                    subtitle = streamFormatLabel,
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
                            Text(strings.autoReconnectTitle, color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(strings.autoReconnectDesc, color = c.textSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = settings.autoReconnectLive,
                            onCheckedChange = { onUpdateSettings(settings.copy(autoReconnectLive = it)) },
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isIndonesian) "DNS-over-HTTPS (DoH) Anti-Blokir" else "Built-in DNS-over-HTTPS (DoH)",
                                color = c.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isIndonesian) "Enkripsi DNS & kunci rute IPv4 tercepat untuk server Xtream" else "Encrypted DNS & fast IPv4 route pinning for Xtream servers",
                                color = c.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = settings.enableDoh,
                            onCheckedChange = { onUpdateSettings(settings.copy(enableDoh = it)) },
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
                            StreamFormat.AUTO to strings.streamFormatAuto,
                            StreamFormat.HLS to "HLS",
                            StreamFormat.TS to "MPEG-TS"
                        ),
                        selected = settings.streamFormat,
                        onSelect = { onUpdateSettings(settings.copy(streamFormat = it)) }
                    )

                    Spacer(Modifier.height(12.dp))
                    Text(strings.bufferProfileTitle, color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    SegmentedChoiceRow(
                        options = listOf(
                            BufferProfile.LOW_LATENCY to strings.bufferLowLatency,
                            BufferProfile.BALANCED to strings.bufferBalanced,
                            BufferProfile.ANTI_BUFFER to strings.bufferAntiBuffer
                        ),
                        selected = settings.bufferProfile,
                        onSelect = { onUpdateSettings(settings.copy(bufferProfile = it)) }
                    )

                    Spacer(Modifier.height(12.dp))
                    Text(strings.autoSubtitleTitle, color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(strings.autoSubtitleDesc, color = c.textSecondary.copy(alpha = 0.75f), fontSize = 10.sp)
                    Spacer(Modifier.height(6.dp))
                    SegmentedChoiceRow(
                        options = listOf(
                            AutoSubtitleLang.ID to strings.subtitleLangId,
                            AutoSubtitleLang.EN to strings.subtitleLangEn,
                            AutoSubtitleLang.OFF to strings.subtitleLangOff
                        ),
                        selected = settings.autoSubtitleLang,
                        onSelect = { onUpdateSettings(settings.copy(autoSubtitleLang = it)) }
                    )
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = c.glassBorder)
                    Spacer(Modifier.height(10.dp))
                    Text(strings.skipIntroTitle, color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    SegmentedChoiceRow(
                        options = listOf(
                            SkipIntroMode.OFF to strings.skipIntroOff,
                            SkipIntroMode.MANUAL to strings.skipIntroManual,
                            SkipIntroMode.AUTO to strings.skipIntroAuto
                        ),
                        selected = settings.skipIntroMode,
                        onSelect = { onUpdateSettings(settings.copy(skipIntroMode = it)) }
                    )
                    if (settings.skipIntroMode != SkipIntroMode.OFF) {
                        val curMin = settings.skipIntroSeconds / 60
                        val curSec = settings.skipIntroSeconds % 60
                        val formattedDur = String.format(java.util.Locale.US, "%02d:%02d", curMin, curSec)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${strings.skipIntroDurationLabel}: $formattedDur (${settings.skipIntroSeconds}s)",
                            color = c.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        SegmentedChoiceRow(
                            options = listOf(60 to "01:00", 85 to "01:25", 100 to "01:40"),
                            selected = settings.skipIntroSeconds,
                            onSelect = { onUpdateSettings(settings.copy(skipIntroSeconds = it)) }
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (isIndonesian) "Atur Kustom (Menit & Detik)" else "Custom Duration (Min & Sec)",
                            color = c.textSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(c.surface)
                                    .border(1.dp, c.glassBorder, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(
                                    onClick = {
                                        val next = ((curMin - 1).coerceAtLeast(0) * 60 + curSec).coerceIn(5, 600)
                                        onUpdateSettings(settings.copy(skipIntroSeconds = next))
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Rounded.Remove, contentDescription = "-", tint = c.accent, modifier = Modifier.size(16.dp))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(String.format(java.util.Locale.US, "%02d", curMin), color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(if (isIndonesian) "Menit" else "Min", color = c.textSecondary, fontSize = 9.sp)
                                }
                                IconButton(
                                    onClick = {
                                        val next = ((curMin + 1).coerceAtMost(10) * 60 + curSec).coerceIn(5, 600)
                                        onUpdateSettings(settings.copy(skipIntroSeconds = next))
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = "+", tint = c.accent, modifier = Modifier.size(16.dp))
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(c.surface)
                                    .border(1.dp, c.glassBorder, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(
                                    onClick = {
                                        val next = (settings.skipIntroSeconds - 1).coerceIn(5, 600)
                                        onUpdateSettings(settings.copy(skipIntroSeconds = next))
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Rounded.Remove, contentDescription = "-", tint = c.accent, modifier = Modifier.size(16.dp))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(String.format(java.util.Locale.US, "%02d", curSec), color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(if (isIndonesian) "Detik" else "Sec", color = c.textSecondary, fontSize = 9.sp)
                                }
                                IconButton(
                                    onClick = {
                                        val next = (settings.skipIntroSeconds + 1).coerceIn(5, 600)
                                        onUpdateSettings(settings.copy(skipIntroSeconds = next))
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = "+", tint = c.accent, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = c.glassBorder, modifier = Modifier.padding(vertical = 10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.autoNextTitle, color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(strings.autoNextDesc, color = c.textSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = settings.autoNextEpisode,
                            onCheckedChange = { onUpdateSettings(settings.copy(autoNextEpisode = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = c.onAccent,
                                checkedTrackColor = c.accent
                            )
                        )
                    }
                }

                // 7. Language
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

                // 8. Check Updates — langsung jalankan Bumerang Loading Overlay + Popup Kartu Update (v2.4.7)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !checkingUpdate) { triggerUpdateCheck() }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Icon(Icons.Rounded.SystemUpdate, contentDescription = strings.checkUpdates, tint = c.textPrimary, modifier = Modifier.size(23.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.checkUpdates, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("v${BuildConfig.VERSION_NAME}", color = c.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Spacer(Modifier.height(22.dp))

                // ---- Minimalist Footer Links ----
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

                Spacer(Modifier.height(18.dp))

                // Tombol Bersihkan Cache (Terbuka di luar, warna hijau muda lembut, persis di atas Keluar Akun — v2.4.7)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(softMintGreen.copy(alpha = 0.14f))
                            .border(1.2.dp, softMintGreen.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                            .clickable {
                                context.imageLoader.memoryCache?.clear()
                                scope.launch(Dispatchers.IO) {
                                    runCatching { context.imageLoader.diskCache?.clear() }
                                    runCatching {
                                        context.cacheDir?.listFiles()?.forEach { f ->
                                            if (f.isFile && (f.name.endsWith(".srt") || f.name.endsWith(".zip"))) {
                                                f.delete()
                                            }
                                        }
                                    }
                                }
                                Toast.makeText(context, strings.cacheCleared, Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CleaningServices,
                            contentDescription = strings.clearCache,
                            tint = softMintGreen,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = strings.clearCache,
                            color = softMintGreen,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (isAdminLoggedIn || isFullAdmin) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFFA263E).copy(alpha = 0.14f))
                                .border(1.2.dp, Color(0xFFFA263E).copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                                .clickable(onClick = onLogoutAdmin)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Logout,
                                contentDescription = strings.adminMenuLogout,
                                tint = Color(0xFFFA263E),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = strings.adminMenuLogout,
                                color = Color(0xFFFA263E),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Bumerang Loading Overlay saat Periksa Update (sama seperti memuat playlist — v2.4.7)
        if (checkingUpdate) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.76f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BoomerangLoadingIndicator(style = settings.loadingAnimStyle, size = 76.dp)
                    Text(
                        text = strings.checkingUpdate,
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // Popup Kartu Update (v2.4.7)
    if (showUpdatePopup) {
        val info = updateInfo
        AlertDialog(
            onDismissRequest = { showUpdatePopup = false },
            containerColor = c.surface,
            shape = RoundedCornerShape(22.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(
                                when {
                                    updateErrorMsg.isNotEmpty() -> Color(0x26FF453A)
                                    info?.hasUpdate == true -> c.accentContainer
                                    else -> softMintGreen.copy(alpha = 0.16f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                updateErrorMsg.isNotEmpty() -> Icons.Rounded.ErrorOutline
                                info?.hasUpdate == true -> Icons.Rounded.SystemUpdate
                                else -> Icons.Rounded.CheckCircle
                            },
                            contentDescription = null,
                            tint = when {
                                updateErrorMsg.isNotEmpty() -> Color(0xFFFF6B6B)
                                info?.hasUpdate == true -> c.accent
                                else -> softMintGreen
                            },
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Column {
                        Text(
                            text = when {
                                updateErrorMsg.isNotEmpty() -> if (isIndonesian) "Gagal Memeriksa Update" else "Update Check Failed"
                                info?.hasUpdate == true -> "${strings.updateAvailable} v${info.latestVersion}"
                                else -> strings.upToDate
                            },
                            color = c.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${strings.currentVersion}: v${BuildConfig.VERSION_NAME}",
                            color = c.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (updateErrorMsg.isNotEmpty()) {
                        Text(updateErrorMsg, color = Color(0xFFFF8A80), fontSize = 13.sp)
                    } else if (info != null && info.hasUpdate) {
                        Text(
                            text = info.releaseNotes.ifBlank {
                                if (isIndonesian) "YlowstoneTV v${info.latestVersion} siap diunduh."
                                else "YlowstoneTV v${info.latestVersion} is ready to download."
                            },
                            color = c.textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    } else {
                        Text(
                            text = if (isIndonesian) {
                                "Anda sudah menggunakan versi terbaru YlowstoneTV (v${BuildConfig.VERSION_NAME})."
                            } else {
                                "You are already on the latest version of YlowstoneTV (v${BuildConfig.VERSION_NAME})."
                            },
                            color = c.textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                }
            },
            confirmButton = {
                if (info != null && info.hasUpdate) {
                    Button(
                        onClick = {
                            val url = info.apkDownloadUrl.ifBlank { info.htmlUrl }
                            showUpdatePopup = false
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.downloadUpdateBrowser, color = c.onAccent, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { showUpdatePopup = false },
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("OK", color = c.onAccent, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (info != null && info.hasUpdate) {
                    TextButton(onClick = { showUpdatePopup = false }) {
                        Text(strings.cancel, color = c.textSecondary)
                    }
                }
            }
        )
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
                    .padding(vertical = 9.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (active) c.onAccent else c.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
