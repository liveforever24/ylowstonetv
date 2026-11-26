package com.ylowstone.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Glossy translucent press feedback modifier:
 * Gently scales down on touch with spring physics and illuminates a glossy glass sheen.
 */
fun Modifier.glossyPressPill(
    cornerRadiusDp: Float = 16f
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val accent = LocalYlowColors.current.accent

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.968f else 1f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 320f
        ),
        label = "glossyScale"
    )
    val sheenAlpha by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = tween(durationMillis = if (isPressed) 120 else 280),
        label = "glossySheen"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawWithContent {
            drawContent()
            if (sheenAlpha > 0.01f) {
                val r = cornerRadiusDp.dp.toPx()
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.16f * sheenAlpha),
                            accent.copy(alpha = 0.10f * sheenAlpha),
                            Color.Transparent
                        )
                    ),
                    cornerRadius = CornerRadius(r, r)
                )
                drawRoundRect(
                    color = accent.copy(alpha = 0.55f * sheenAlpha),
                    cornerRadius = CornerRadius(r, r),
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
        }
}

/**
 * Floating transparent glossy scroll pill for LazyColumn lists.
 * Appears smoothly during scroll, shows active position indicator, and supports thumb scrubbing.
 */
@Composable
fun GlossyScrollPositionPill(
    listState: LazyListState,
    totalItemsCount: Int,
    modifier: Modifier = Modifier
) {
    if (totalItemsCount <= 12) return
    val c = LocalYlowColors.current
    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var containerHeightPx by remember { mutableFloatStateOf(1f) }

    val isScrolling = listState.isScrollInProgress
    LaunchedEffect(isScrolling, isDragging) {
        if (isScrolling || isDragging) {
            visible = true
        } else {
            delay(1100L)
            visible = false
        }
    }

    val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val progress = (firstVisible.toFloat() / (totalItemsCount - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 360f),
        label = "scrollPillProgress"
    )
    val pct = (animatedProgress * 100f).roundToInt().coerceIn(0, 100)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(56.dp)
            .onSizeChanged { containerHeightPx = it.height.toFloat().coerceAtLeast(1f) }
            .padding(vertical = 24.dp, horizontal = 4.dp)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(240)),
            exit = fadeOut(tween(420)),
            modifier = Modifier.fillMaxSize()
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val pillHeight = 36.dp
                val travel = (maxHeight - pillHeight).coerceAtLeast(0.dp)
                val offsetY = travel * animatedProgress

                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = offsetY)
                        .height(pillHeight)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xCC1E262E),
                                    Color(0xAA101418)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.34f),
                                    c.accent.copy(alpha = 0.38f)
                                )
                            ),
                            CircleShape
                        )
                        .pointerInput(totalItemsCount, containerHeightPx) {
                            detectVerticalDragGestures(
                                onDragStart = { isDragging = true },
                                onDragEnd = { isDragging = false },
                                onDragCancel = { isDragging = false }
                            ) { change, dragAmount ->
                                change.consume()
                                val deltaRatio = dragAmount / (containerHeightPx * 0.82f).coerceAtLeast(1f)
                                val currentRatio = listState.firstVisibleItemIndex.toFloat() / (totalItemsCount - 1).coerceAtLeast(1)
                                val targetRatio = (currentRatio + deltaRatio).coerceIn(0f, 1f)
                                val targetIdx = (targetRatio * (totalItemsCount - 1)).roundToInt().coerceIn(0, totalItemsCount - 1)
                                scope.launch {
                                    listState.scrollToItem(targetIdx)
                                }
                            }
                        }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(c.accent)
                    )
                    Text(
                        text = "$pct%",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Floating transparent glossy scroll pill for LazyVerticalGrid (Movies & Series).
 */
@Composable
fun GlossyGridScrollPositionPill(
    gridState: LazyGridState,
    totalItemsCount: Int,
    modifier: Modifier = Modifier
) {
    if (totalItemsCount <= 12) return
    val c = LocalYlowColors.current
    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var containerHeightPx by remember { mutableFloatStateOf(1f) }

    val isScrolling = gridState.isScrollInProgress
    LaunchedEffect(isScrolling, isDragging) {
        if (isScrolling || isDragging) {
            visible = true
        } else {
            delay(1100L)
            visible = false
        }
    }

    val firstVisible by remember { derivedStateOf { gridState.firstVisibleItemIndex } }
    val progress = (firstVisible.toFloat() / (totalItemsCount - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 360f),
        label = "gridScrollPillProgress"
    )
    val pct = (animatedProgress * 100f).roundToInt().coerceIn(0, 100)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(56.dp)
            .onSizeChanged { containerHeightPx = it.height.toFloat().coerceAtLeast(1f) }
            .padding(vertical = 24.dp, horizontal = 4.dp)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(240)),
            exit = fadeOut(tween(420)),
            modifier = Modifier.fillMaxSize()
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val pillHeight = 36.dp
                val travel = (maxHeight - pillHeight).coerceAtLeast(0.dp)
                val offsetY = travel * animatedProgress

                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = offsetY)
                        .height(pillHeight)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xCC1E262E),
                                    Color(0xAA101418)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.34f),
                                    c.accent.copy(alpha = 0.38f)
                                )
                            ),
                            CircleShape
                        )
                        .pointerInput(totalItemsCount, containerHeightPx) {
                            detectVerticalDragGestures(
                                onDragStart = { isDragging = true },
                                onDragEnd = { isDragging = false },
                                onDragCancel = { isDragging = false }
                            ) { change, dragAmount ->
                                change.consume()
                                val deltaRatio = dragAmount / (containerHeightPx * 0.82f).coerceAtLeast(1f)
                                val currentRatio = gridState.firstVisibleItemIndex.toFloat() / (totalItemsCount - 1).coerceAtLeast(1)
                                val targetRatio = (currentRatio + deltaRatio).coerceIn(0f, 1f)
                                val targetIdx = (targetRatio * (totalItemsCount - 1)).roundToInt().coerceIn(0, totalItemsCount - 1)
                                scope.launch {
                                    gridState.scrollToItem(targetIdx)
                                }
                            }
                        }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(c.accent)
                    )
                    Text(
                        text = "$pct%",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

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
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .tvFocusBorder(isTvMode, CircleShape)
            .glossyPressPill(20f)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ylowstone_y_emblem),
            contentDescription = "Menu",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(32.dp)
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
    onLogoutAdmin: () -> Unit = {},
    onCheckUpdates: () -> Unit = {}
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
                painter = painterResource(id = R.drawable.ylowstone_y_emblem),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(32.dp)
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
                    text = "YlowstoneTV • v${BuildConfig.VERSION_NAME}",
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
                            .glossyPressPill(10f)
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
        } else {
            DropdownMenuItem(
                text = { Text(strings.adminMenuLogin, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                leadingIcon = { Icon(Icons.Rounded.Login, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp)) },
                onClick = { onDismiss(); onOpenAdminLogin() }
            )
        }

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
 * Elegant Bottom Navigation Bar with Sliding Transparent Glossy Pill Indicator
 */
@Composable
fun YlowBottomNavbar(
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
    val selectedIndex = items.indexOfFirst { it.first == selectedTab }.coerceAtLeast(0)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.navBarBg)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(color = c.glassBorder, thickness = 0.6.dp)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 6.dp, vertical = 5.dp)
        ) {
            val slotWidth = maxWidth / items.size
            val pillOffsetX by animateDpAsState(
                targetValue = slotWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = 0.80f,
                    stiffness = 260f
                ),
                label = "navGlossyPillOffset"
            )

            // Sliding compact transparent glossy pill behind active tab
            Box(
                modifier = Modifier
                    .offset(x = pillOffsetX)
                    .width(slotWidth)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp, vertical = 1.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                c.accent.copy(alpha = 0.24f),
                                c.accent.copy(alpha = 0.10f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.32f),
                                c.accent.copy(alpha = 0.42f)
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { (tab, icon, label) ->
                    val active = tab == selectedTab
                    val tint by animateColorAsState(
                        targetValue = if (active) c.accent else c.textSecondary,
                        animationSpec = tween(260),
                        label = "ylowNavTint"
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (active) 1.08f else 1.0f,
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = 300f),
                        label = "navIconScale"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .tvFocusBorder(isTvMode, RoundedCornerShape(16.dp))
                            .glossyPressPill(16f)
                            .clickable { onSelectTab(tab) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = tint,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = label,
                            color = tint,
                            fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
            .glossyPressPill(19f)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = c.textPrimary, modifier = Modifier.size(19.dp))
    }
}
