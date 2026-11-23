package com.ylowstone.tv.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
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
import com.ylowstone.tv.model.ChannelItem
import com.ylowstone.tv.model.DeviceMode
import com.ylowstone.tv.ui.theme.LocalYlowColors

@Composable
fun YlowstoneLogoBadge(
    iconOption: AppIconOption = AppIconOption.OBSIDIAN,
    size: Dp = 42.dp
) {
    Image(
        painter = painterResource(id = iconOption.previewRes),
        contentDescription = "YlowstoneTV",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape)
    )
}

@Composable
fun ProfileYBadgeButton(
    isTvMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.45f))
            .clip(CircleShape)
            .border(1.5.dp, Color.White.copy(alpha = 0.30f), CircleShape)
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
    strings: AppStrings,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchDeviceMode: (DeviceMode) -> Unit,
    onManagePlaylists: () -> Unit,
    onCheckUpdates: () -> Unit
) {
    val c = LocalYlowColors.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = if (c.isDark) Color(0xF0181820) else Color(0xF7FFFFFF),
        modifier = Modifier
            .width(250.dp)
            .border(1.dp, c.glassHighlight, RoundedCornerShape(22.dp))
    ) {
        // Header info
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
                    text = "YlowstoneTV v${BuildConfig.VERSION_NAME}",
                    color = c.textSecondary,
                    fontSize = 11.sp
                )
            }
        }

        HorizontalDivider(color = c.glassBorder, modifier = Modifier.padding(horizontal = 12.dp))

        // Settings
        DropdownMenuItem(
            text = { Text(strings.settings, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
            leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(20.dp)) },
            onClick = { onDismiss(); onOpenSettings() }
        )

        // Manage / Switch Playlist
        DropdownMenuItem(
            text = { Text(strings.managePlaylists, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
            leadingIcon = { Icon(Icons.Rounded.SwitchAccount, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(20.dp)) },
            onClick = { onDismiss(); onManagePlaylists() }
        )

        // Inline Switch Mode: Smartphone / TV
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
                        Icon(pair.first, contentDescription = null, tint = if (active) Color.White else c.textSecondary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = pair.second,
                            color = if (active) Color.White else c.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = c.glassBorder, modifier = Modifier.padding(horizontal = 12.dp))

        // Bottom: Check for Updates
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
    shape: Shape = RoundedCornerShape(16.dp)
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

@Composable
fun MorphingLiquidGlassNavbar(
    selectedTab: MainTab,
    isCompact: Boolean,
    activeChannel: ChannelItem?,
    activePlaylistName: String,
    isPlaying: Boolean,
    strings: AppStrings,
    isTvMode: Boolean,
    onSelectTab: (MainTab) -> Unit,
    onTogglePlayPause: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalYlowColors.current

    AnimatedContent(
        targetState = isCompact && !isTvMode,
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                .togetherWith(fadeOut(tween(180)) + scaleOut(targetScale = 0.95f))
        },
        label = "morphNavbar",
        modifier = modifier
    ) { compact ->
        if (compact) {
            // ---- BITChORD-STYLE COLLAPSED 3-PART LIQUID GLASS BAR ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Left Circle: Home
                LiquidGlassCircleButton(
                    icon = Icons.Rounded.Home,
                    active = selectedTab == MainTab.HOME,
                    onClick = { onSelectTab(MainTab.HOME) }
                )

                // Center Capsule: Now Playing Mini Pill (or Active Playlist)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .shadow(16.dp, RoundedCornerShape(28.dp), ambientColor = Color.Black.copy(alpha = 0.55f))
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    c.glassBg.copy(alpha = if (c.isDark) 0.90f else 0.94f),
                                    c.glassBg.copy(alpha = if (c.isDark) 0.78f else 0.86f)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            Brush.verticalGradient(listOf(c.glassHighlight, c.glassBorder)),
                            RoundedCornerShape(28.dp)
                        )
                        .clickable {
                            if (activeChannel != null) onOpenNowPlaying()
                            else onSelectTab(MainTab.LIVE)
                        }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (activeChannel != null) {
                        ChannelLogoBox(activeChannel.logoUrl, 32.dp, corner = 9.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeChannel.name,
                                color = c.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = activeChannel.category,
                                color = c.textSecondary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(34.dp)) {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "PlayPause",
                                tint = c.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(c.accent.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(activePlaylistName, color = c.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(strings.nothingPlaying, color = c.textSecondary, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }

                // Right Circle: Search
                LiquidGlassCircleButton(
                    icon = Icons.Rounded.Search,
                    active = selectedTab == MainTab.SEARCH,
                    onClick = { onSelectTab(MainTab.SEARCH) }
                )
            }
        } else {
            // ---- FULL 5-TAB LIQUID GLASS CAPSULE (Home · Live · Movies · Series · Search) ----
            val items = listOf(
                Triple(MainTab.HOME, Icons.Rounded.Home, strings.home),
                Triple(MainTab.LIVE, Icons.Rounded.LiveTv, strings.liveTv),
                Triple(MainTab.MOVIES, Icons.Rounded.Movie, strings.movies),
                Triple(MainTab.SERIES, Icons.Rounded.Tv, strings.series),
                Triple(MainTab.SEARCH, Icons.Rounded.Search, strings.search)
            )

            Row(
                modifier = Modifier
                    .shadow(20.dp, RoundedCornerShape(34.dp), ambientColor = Color.Black.copy(alpha = 0.55f))
                    .clip(RoundedCornerShape(34.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                c.glassBg.copy(alpha = if (c.isDark) 0.90f else 0.94f),
                                c.glassBg.copy(alpha = if (c.isDark) 0.76f else 0.86f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(listOf(c.glassHighlight, c.glassBorder)),
                        shape = RoundedCornerShape(34.dp)
                    )
                    .padding(horizontal = 7.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { (tab, icon, label) ->
                    val active = tab == selectedTab
                    val pillBg by animateColorAsState(
                        targetValue = if (active) c.accent.copy(alpha = 0.22f) else Color.Transparent,
                        label = "tabBg"
                    )
                    val tint by animateColorAsState(
                        targetValue = if (active) c.accent else c.textSecondary,
                        label = "tabTint"
                    )

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(26.dp))
                            .background(pillBg)
                            .tvFocusBorder(isTvMode, RoundedCornerShape(26.dp))
                            .clickable { onSelectTab(tab) }
                            .padding(horizontal = if (active) 13.dp else 11.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
                        if (active) {
                            Text(
                                text = label,
                                color = tint,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidGlassCircleButton(
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit
) {
    val c = LocalYlowColors.current
    Box(
        modifier = Modifier
            .size(50.dp)
            .shadow(16.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.55f))
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        c.glassBg.copy(alpha = if (c.isDark) 0.90f else 0.94f),
                        c.glassBg.copy(alpha = if (c.isDark) 0.78f else 0.86f)
                    )
                )
            )
            .border(1.dp, Brush.verticalGradient(listOf(c.glassHighlight, c.glassBorder)), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) c.accent else c.textPrimary,
            modifier = Modifier.size(22.dp)
        )
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
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title, color = c.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) { input = it; error = false } },
                    label = { Text(if (expectedPin != null) strings.enterPin else strings.newPinPrompt) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = error,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error) {
                    Text(strings.wrongPin, color = Color(0xFFFF453A), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (expectedPin != null) {
                        if (input == expectedPin) onConfirm(input) else error = true
                    } else if (input.length >= 4) {
                        onConfirm(input)
                    } else {
                        error = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = c.accent)
            ) {
                Text("OK", color = Color.White)
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
            .background(c.glassBg)
            .border(1.dp, c.glassBorder, CircleShape)
            .tvFocusBorder(isTvMode, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = c.textPrimary, modifier = Modifier.size(19.dp))
    }
}
