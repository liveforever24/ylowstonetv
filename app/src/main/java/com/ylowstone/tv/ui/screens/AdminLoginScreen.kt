package com.ylowstone.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ylowstone.tv.data.IptvRepository
import com.ylowstone.tv.i18n.AppStrings
import com.ylowstone.tv.model.AppIconOption
import com.ylowstone.tv.ui.components.YlowstoneLogoBadge
import com.ylowstone.tv.ui.components.tvFocusBorder
import com.ylowstone.tv.ui.theme.LocalYlowColors

@Composable
fun AdminLoginScreen(
    appIcon: AppIconOption,
    strings: AppStrings,
    isTvMode: Boolean,
    isVerifying: Boolean,
    errorMessage: String,
    canGoBack: Boolean,
    onSubmitPin: (String) -> Unit,
    onContinueGuest: () -> Unit,
    onBack: () -> Unit
) {
    val c = LocalYlowColors.current
    var pin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf("") }

    val displayError = localError.ifBlank { errorMessage }

    fun triggerSubmit() {
        if (isVerifying) return
        if (pin.length != 4) {
            localError = strings.pinMustBe4Digits
            return
        }
        localError = ""
        onSubmitPin(pin)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .systemBarsPadding()
            .padding(24.dp)
    ) {
        if (canGoBack) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(c.surface)
                    .border(1.dp, c.glassBorder, CircleShape)
                    .clickable(enabled = !isVerifying, onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = c.textPrimary, modifier = Modifier.size(20.dp))
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 410.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(c.surface)
                .border(1.dp, c.glassBorder, RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            YlowstoneLogoBadge(iconOption = appIcon, size = 60.dp)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = strings.adminLoginTitle,
                    color = c.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = strings.adminLoginSubtitle,
                    color = c.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Locked Permanent Username Row ("YlowstoneTV")
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = strings.adminUsernameLabel,
                    color = c.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.surfaceElevated)
                        .border(1.dp, c.glassBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.AdminPanelSettings,
                            contentDescription = null,
                            tint = c.accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = IptvRepository.FIXED_ADMIN_USERNAME,
                            color = c.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp
                        )
                    }
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = "Locked",
                        tint = c.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 4-Digit Numeric PIN Input
            OutlinedTextField(
                value = pin,
                onValueChange = { next ->
                    if (next.length <= 4 && next.all(Char::isDigit)) {
                        pin = next
                        localError = ""
                    }
                },
                label = { Text(strings.adminPinLabel, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Rounded.Pin, contentDescription = null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    IconButton(onClick = { showPin = !showPin }) {
                        Icon(
                            if (showPin) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "Toggle PIN",
                            modifier = Modifier.size(19.dp)
                        )
                    }
                },
                visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { triggerSubmit() }),
                singleLine = true,
                enabled = !isVerifying,
                isError = displayError.isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // 4 Visual PIN Progress Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { idx ->
                    val filled = idx < pin.length
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (filled) c.accent else c.glassBorder)
                    )
                }
            }

            if (displayError.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x26FF453A))
                        .border(1.dp, Color(0x66FF453A), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(18.dp))
                    Text(
                        text = displayError,
                        color = Color(0xFFFF8A80),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Button(
                onClick = { triggerSubmit() },
                enabled = !isVerifying,
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .tvFocusBorder(isTvMode, RoundedCornerShape(14.dp))
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        color = c.onAccent,
                        strokeWidth = 2.2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(strings.adminVerifying, color = c.onAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                } else {
                    Icon(Icons.Rounded.Login, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.adminLoginBtn, color = c.onAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            if (!canGoBack) {
                TextButton(
                    enabled = !isVerifying,
                    onClick = onContinueGuest
                ) {
                    Text(
                        text = strings.adminContinueGuest,
                        color = c.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
