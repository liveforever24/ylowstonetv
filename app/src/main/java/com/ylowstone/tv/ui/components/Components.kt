package com.ylowstone.tv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ylowstone.tv.BuildConfig
import com.ylowstone.tv.R
import com.ylowstone.tv.i18n.AppStrings
import com.ylowstone.tv.model.AppIconOption
import com.ylowstone.tv.model.DeviceMode
import com.ylowstone.tv.ui.theme.LocalYlowColors

@Composable
fun YlowstoneLogoBadge(
    iconOption: AppIconOption = AppIconOption.OBSIDIAN,
    size: Dp = 42.dp
) {
    val c = LocalYlowColors.current
    Image(
        painter = painterResource(id = iconOption.previewRes),
        contentDescription = "YlowstoneTV",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, c.glassBorder, CircleShape)
    )
}

@Composable
fun ProfileYBadgeButton(
    isTvMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalYlowColors.current
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .border(1.5.dp, c.glassBorder, CircleShape)
            .tvFocusBorder(isTvMode, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.profile_y_badge),
            contentDescription = "Menu",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun ProfileQuickDropdown(
    expanded: Boolean,
    activePlaylistName: String?,
    deviceMode: DeviceMode,
    isAdminLoggedIn: Boolean,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchDeviceMode: (DeviceMode) -> Unit,
    onManagePlaylists: () -> Unit,
    onOpenAdminLogin: () -> Unit,
    onSyncAdminPlaylists: () -> Unit,
    onLogoutAdmin: () -> Unit,
    onCheckUpdates: () -> Unit
) {
    val c = LocalYlowColors.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = c.surface,
        modifier = Modifier
            .width(250.dp)
            .border(1.dp, c.glassBorder, RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.profile_y_badge),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(34.dp).clip(CircleShape)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activePlaylistName ?: "YlowstoneTV",
                    color = c.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isAdminLoggedIn) "Admin • v${BuildConfig.VERSION_NAME}" else "YlowstoneTV v${BuildConfig.VERSION_NAME}",
                    color = if (isAdminLoggedIn) c.accent else c.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isAdminLoggedIn) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }

        HorizontalDivider(color = c.glassBorder, modifier = Modifier.padding(horizontal = 12.dp))

        DropdownMenuItem(
            text = { Text(strings.settings, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
            leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(20.dp)) },
            onClick = { onDismiss(); onOpenSettings() }
        )

        DropdownMenuItem(
            text = { Text(strings.managePlaylists, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
            leadingIcon = { Icon(Icons.Rounded.SwitchAccount, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(20.dp)) },
            onClick = { onDismiss(); onManagePlaylists() }
        )

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(strings.switchModeLabel, color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.surfaceElevated)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                listOf(
                    DeviceMode.SMARTPHONE to Pair(Icons.Rounded.Smartphone, strings.smartphone),
                    DeviceMode.TV to Pair(Icons.Rounded.Tv, strings.androidTv)
                ).forEach { (mode, pair) ->
                    val active = deviceMode == mode
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) c.accent else Color.Transparent)
                            .clickable { onSwitchDeviceMode(mode) }
                            .padding(vertical = 7.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(pair.first, contentDescription = null, tint = if (active) c.onAccent else c.textSecondary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = pair.second,
                            color = if (active) c.onAccent else c.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = c.glassBorder, modifier = Modifier.padding(horizontal = 12.dp))

        if (isAdminLoggedIn) {
            DropdownMenuItem(
                text = { Text(strings.adminMenuSync, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                leadingIcon = { Icon(Icons.Rounded.CloudSync, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp)) },
                onClick = { onDismiss(); onSyncAdminPlaylists() }
            )
            DropdownMenuItem(
                text = { Text(strings.adminMenuLogout, color = Color(0xFFFF6B6B), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) },
                leadingIcon = { Icon(Icons.Rounded.Logout, contentDescription = null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(20.dp)) },
                onClick = { onDismiss(); onLogoutAdmin() }
            )
        } else {
            DropdownMenuItem(
                text = { Text(strings.adminMenuLogin, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                leadingIcon = { Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp)) },
                onClick = { onDismiss(); onOpenAdminLogin() }
            )
        }

        HorizontalDivider(color = c.glassBorder, modifier = Modifier.padding(horizontal = 12.dp))

        DropdownMenuItem(
            text = { Text(strings.checkUpdates, color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) },
            leadingIcon = { Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp)) },
            onClick = { onDismiss(); onCheckUpdates() }
        )
    }
}

@Composable
fun Modifier.tvFocusBorder(
    isTvMode: Boolean,
    shape: Shape = RoundedCornerShape(14.dp)
): Modifier {
    val accent = LocalYlowColors.current.accent
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .focusable()
        .then(
            if (isTvMode && focused) {
                Modifier.border(2.5.dp, accent, shape)
            } else {
                Modifier
            }
        )
}

enum class MainTab { HOME, LIVE, MOVIES, SERIES, SEARCH }

/**
 * Clean FotMob-inspired full-width bottom navigation bar:
 * Exact FotMob position, flat height, 5 evenly distributed icon + label columns,
 * highlighted with Google Pixel Fog accent.
 */
@Composable
fun FotMobBottomNavbar(
    selectedTab: MainTab,
    strings: AppStrings,
    isTvMode: Boolean,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalYlowColors.current
    val items = listOf(
        Triple(MainTab.HOME, Icons.Rounded.Home, strings.home),
        Triple(MainTab.LIVE, Icons.Rounded.LiveTv, strings.liveTv),
        Triple(MainTab.MOVIES, Icons.Rounded.Movie, strings.movies),
        Triple(MainTab.SERIES, Icons.Rounded.Tv, strings.series),
        Triple(MainTab.SEARCH, Icons.Rounded.Search, strings.search)
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.navBarBg)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(color = c.glassBorder, thickness = 0.6.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (tab, icon, label) ->
                val active = tab == selectedTab
                val tint by animateColorAsState(
                    targetValue = if (active) c.accent else c.textSecondary,
                    label = "fotmobNavTint"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .tvFocusBorder(isTvMode, RoundedCornerShape(10.dp))
                        .clickable { onSelectTab(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = tint,
                        modifier = Modifier.size(23.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = label,
                        color = tint,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelLogoBox(
    url: String,
    size: Dp,
    corner: Dp = 10.dp
) {
    val c = LocalYlowColors.current
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(c.surfaceElevated),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(false)
                    .size(128)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(3.dp)
            )
        } else {
            Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(size * 0.5f))
        }
    }
}

@Composable
fun PinDialog(
    title: String,
    expectedPin: String?,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val c = LocalYlowColors.current
    var input by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title, color = c.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        if (it.length <= 4 && it.all(Char::isDigit)) {
                            input = it
                            errorMsg = ""
                        }
                    },
                    label = { Text(if (expectedPin != null) strings.enterPin else strings.newPinPrompt) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = errorMsg.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = Color(0xFFFF6B6B), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (expectedPin != null) {
                        if (input == expectedPin) onConfirm(input)
                        else errorMsg = strings.wrongPin
                    } else {
                        if (input.length == 4) onConfirm(input)
                        else errorMsg = strings.pinMustBe4Digits
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent)
            ) {
                Text("OK", color = c.onAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel, color = c.textSecondary)
            }
        }
    )
}

@Composable
fun SmallIconPill(
    icon: ImageVector,
    contentDescription: String,
    isTvMode: Boolean,
    onClick: () -> Unit
) {
    val c = LocalYlowColors.current
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(c.surface)
            .border(1.dp, c.glassBorder, CircleShape)
            .tvFocusBorder(isTvMode, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = c.textPrimary, modifier = Modifier.size(19.dp))
    }
}
