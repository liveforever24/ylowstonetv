package com.ylowstone.tv.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ylowstone.tv.i18n.AppStrings
import com.ylowstone.tv.ui.theme.LocalYlowColors

@Composable
fun YlowstoneLogoBadge(size: Dp = 44.dp) {
    val c = LocalYlowColors.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(
                Brush.linearGradient(
                    listOf(c.accent, c.accent.copy(alpha = 0.72f), Color(0xFF2B1028))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.LiveTv,
            contentDescription = "YlowstoneTV",
            tint = Color.White,
            modifier = Modifier.size(size * 0.56f)
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

enum class MainTab { HOME, LIVE, MOVIES, SERIES, SETTINGS }

@Composable
fun FloatingGlassNavbar(
    selectedTab: MainTab,
    onSelect: (MainTab) -> Unit,
    strings: AppStrings,
    isTvMode: Boolean,
    modifier: Modifier = Modifier
) {
    val c = LocalYlowColors.current
    val items = listOf(
        Triple(MainTab.HOME, Icons.Rounded.Home, strings.home),
        Triple(MainTab.LIVE, Icons.Rounded.LiveTv, strings.liveTv),
        Triple(MainTab.MOVIES, Icons.Rounded.Movie, strings.movies),
        Triple(MainTab.SERIES, Icons.Rounded.Tv, strings.series),
        Triple(MainTab.SETTINGS, Icons.Rounded.Settings, strings.settings)
    )

    Row(
        modifier = modifier
            .shadow(18.dp, RoundedCornerShape(32.dp), ambientColor = Color.Black.copy(alpha = 0.45f))
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        c.glassBg.copy(alpha = if (c.isDark) 0.86f else 0.90f),
                        c.glassBg.copy(alpha = if (c.isDark) 0.72f else 0.82f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (c.isDark) 0.28f else 0.7f),
                        c.glassBorder
                    )
                ),
                shape = RoundedCornerShape(32.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (tab, icon, label) ->
            val active = tab == selectedTab
            val pillBg by animateColorAsState(
                targetValue = if (active) c.accent.copy(alpha = 0.22f) else Color.Transparent,
                label = "navPill"
            )
            val tint by animateColorAsState(
                targetValue = if (active) c.accent else c.textSecondary,
                label = "navTint"
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(pillBg)
                    .tvFocusBorder(isTvMode, RoundedCornerShape(24.dp))
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(19.dp))
                if (active) {
                    Text(
                        text = label,
                        color = tint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
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
            .background(c.surfaceElevated.copy(alpha = 0.85f))
            .border(1.dp, c.glassBorder, CircleShape)
            .tvFocusBorder(isTvMode, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = c.textPrimary, modifier = Modifier.size(19.dp))
    }
}
