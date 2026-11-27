package com.ylowstone.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
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
import com.ylowstone.tv.ui.components.glossyPressPill
import com.ylowstone.tv.ui.components.tvFocusBorder
import com.ylowstone.tv.ui.theme.LocalYlowColors
import com.ylowstone.tv.util.FlagshipFeedback
import com.ylowstone.tv.util.SecurityCrypto
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun AdminLoginScreen(
    appIcon: AppIconOption,
    strings: AppStrings,
    isTvMode: Boolean,
    isVerifying: Boolean,
    errorMessage: String,
    canGoBack: Boolean,
    lockoutUntilMs: Long = 0L,
    onRecordFailedAttempt: () -> Unit = {},
    onSubmitPin: (String) -> Unit,
    onSubmitFullAdmin: (includeDefaultPlaylists: Boolean, serverPin: String) -> Unit,
    onContinueGuest: () -> Unit,
    onBack: () -> Unit
) {
    val c = LocalYlowColors.current
    val view = LocalView.current
    val isIndo = strings.watchHistory.startsWith("Riwayat", ignoreCase = true)

    var pin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }

    var showAdminPanel by remember { mutableStateOf(false) }
    var adminPassword by remember { mutableStateOf("") }
    var showAdminPassword by remember { mutableStateOf(false) }
    var includeDefaultPlaylists by remember { mutableStateOf(true) }

    var localError by remember { mutableStateOf("") }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(lockoutUntilMs) {
        while (lockoutUntilMs > System.currentTimeMillis()) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
        nowMs = System.currentTimeMillis()
    }

    val lockoutRemainingSec = ((lockoutUntilMs - nowMs) / 1000L).coerceAtLeast(0L).toInt()
    val isLockedOut = lockoutRemainingSec > 0
    val formattedLockout = remember(lockoutRemainingSec) {
        val m = lockoutRemainingSec / 60
        val s = lockoutRemainingSec % 60
        String.format(Locale.US, "%02d:%02d", m, s)
    }

    val displayError = when {
        isLockedOut -> if (isIndo) {
            "Terlalu banyak percobaan salah. Coba lagi dalam $formattedLockout"
        } else {
            "Too many failed attempts. Try again in $formattedLockout"
        }
        localError.isNotEmpty() -> localError
        else -> errorMessage
    }

    fun triggerUserPinSubmit() {
        if (isVerifying || isLockedOut) return
        if (pin.length != 4) {
            localError = strings.pinMustBe4Digits
            return
        }
        FlagshipFeedback.playSelect(view)
        localError = ""
        onSubmitPin(pin)
    }

    fun triggerFullAdminSubmit() {
        if (isVerifying || isLockedOut) return
        if (!SecurityCrypto.verifyAdminPassword(adminPassword)) {
            onRecordFailedAttempt()
            localError = if (isIndo) "Password Admin tidak valid" else "Invalid Admin password"
            return
        }
        if (includeDefaultPlaylists && pin.length != 4) {
            localError = if (isIndo) {
                "Masukkan PIN 4 digit untuk menyertakan playlist default"
            } else {
                "Enter 4-digit PIN to include default playlists"
            }
            return
        }
        FlagshipFeedback.playSelect(view)
        localError = ""
        onSubmitFullAdmin(includeDefaultPlaylists, pin)
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
                    .glossyPressPill(20f)
                    .clickable(enabled = !isVerifying, onClick = {
                        FlagshipFeedback.playTick(view)
                        onBack()
                    }),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = c.textPrimary, modifier = Modifier.size(20.dp))
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(c.surface)
                .border(1.dp, c.glassBorder, RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            YlowstoneLogoBadge(iconOption = appIcon, size = 58.dp)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = strings.adminLoginTitle,
                    color = c.textPrimary,
                    fontSize = 20.sp,
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

            // Fixed Username Row: YlowstoneTV
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
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
                        Text(
                            text = IptvRepository.FIXED_ADMIN_USERNAME,
                            color = c.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp
                        )
                    }
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
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
                keyboardActions = KeyboardActions(onDone = {
                    if (showAdminPanel) triggerFullAdminSubmit() else triggerUserPinSubmit()
                }),
                singleLine = true,
                enabled = !isVerifying && !isLockedOut,
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
                    Icon(
                        if (isLockedOut) Icons.Rounded.Timer else Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = displayError,
                        color = Color(0xFFFF8A80),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (!showAdminPanel) {
                Button(
                    onClick = { triggerUserPinSubmit() },
                    enabled = !isVerifying && !isLockedOut,
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .tvFocusBorder(isTvMode, RoundedCornerShape(14.dp))
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(color = c.onAccent, strokeWidth = 2.2.dp, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(strings.adminVerifying, color = c.onAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    } else {
                        Icon(Icons.Rounded.Login, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.adminLoginBtn, color = c.onAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            HorizontalDivider(color = c.glassBorder, thickness = 0.7.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (showAdminPanel) c.accentContainer else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (showAdminPanel) c.accent.copy(alpha = 0.55f) else c.glassBorder,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable(enabled = !isVerifying && !isLockedOut) {
                        FlagshipFeedback.playTick(view)
                        showAdminPanel = !showAdminPanel
                        localError = ""
                    }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AdminPanelSettings,
                        contentDescription = null,
                        tint = if (showAdminPanel) c.accent else c.textSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = if (isIndo) "Login Admin" else "Admin Login",
                        color = if (showAdminPanel) c.accent else c.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    imageVector = if (showAdminPanel) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = c.textSecondary,
                    modifier = Modifier.size(19.dp)
                )
            }

            androidx.compose.animation.AnimatedVisibility(visible = showAdminPanel) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.surfaceElevated)
                        .border(1.dp, c.glassBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = adminPassword,
                        onValueChange = {
                            adminPassword = it
                            localError = ""
                        },
                        label = { Text(if (isIndo) "Password Admin" else "Admin Password", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(19.dp)) },
                        trailingIcon = {
                            IconButton(onClick = { showAdminPassword = !showAdminPassword }) {
                                Icon(
                                    if (showAdminPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "Toggle Password",
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        },
                        visualTransformation = if (showAdminPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { triggerFullAdminSubmit() }),
                        singleLine = true,
                        enabled = !isVerifying && !isLockedOut,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !isVerifying && !isLockedOut) {
                                FlagshipFeedback.playTick(view)
                                includeDefaultPlaylists = !includeDefaultPlaylists
                                localError = ""
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = includeDefaultPlaylists,
                            enabled = !isLockedOut,
                            onCheckedChange = {
                                FlagshipFeedback.playTick(view)
                                includeDefaultPlaylists = it
                                localError = ""
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = c.accent,
                                checkmarkColor = c.onAccent
                            )
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isIndo) "Sertakan playlist default" else "Include default playlists",
                                color = c.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (includeDefaultPlaylists) {
                                    if (isIndo) "Playlist server ikut dimuat (gunakan PIN di atas)" else "Default server playlists will load (uses PIN above)"
                                } else {
                                    if (isIndo) "Masuk mode Admin bersih tanpa playlist default" else "Enter clean Admin mode without default playlists"
                                },
                                color = c.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Button(
                        onClick = { triggerFullAdminSubmit() },
                        enabled = !isVerifying && !isLockedOut,
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .tvFocusBorder(isTvMode, RoundedCornerShape(12.dp))
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(color = c.onAccent, strokeWidth = 2.2.dp, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(strings.adminVerifying, color = c.onAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isIndo) "Masuk Akses Penuh" else "Sign In as Admin",
                                color = c.onAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            if (!canGoBack) {
                TextButton(
                    enabled = !isVerifying && !isLockedOut,
                    onClick = {
                        FlagshipFeedback.playTick(view)
                        onContinueGuest()
                    }
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
