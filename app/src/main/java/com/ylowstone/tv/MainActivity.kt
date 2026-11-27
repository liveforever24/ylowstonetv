package com.ylowstone.tv

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ylowstone.tv.data.AppPrefs
import com.ylowstone.tv.data.DatabaseHelper
import com.ylowstone.tv.data.IptvRepository
import com.ylowstone.tv.ui.YlowstoneApp

class MainActivity : ComponentActivity() {

    companion object {
        private const val ACTION_PIP_CONTROL = "com.ylowstone.tv.PIP_CONTROL"
        private const val EXTRA_CMD = "cmd"
    }

    private lateinit var prefs: AppPrefs
    private lateinit var repo: IptvRepository

    private var isInPipMode by mutableStateOf(false)
    private var isPipEligible: Boolean = false
    private var isPlayerPlaying: Boolean = false

    private var pipCommandNonce by mutableIntStateOf(0)
    private var pipCommandAction by mutableStateOf("")

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_PIP_CONTROL) {
                pipCommandAction = intent.getStringExtra(EXTRA_CMD) ?: return
                pipCommandNonce++
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Proteksi anti-debugger pada build Release
        if (!BuildConfig.DEBUG && (Debug.isDebuggerConnected() || Debug.waitingForDebugger())) {
            finishAffinity()
            return
        }

        enableEdgeToEdge()

        prefs = AppPrefs(applicationContext)
        val db = DatabaseHelper(applicationContext)
        repo = IptvRepository(applicationContext, db)

        val filter = IntentFilter(ACTION_PIP_CONTROL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(pipReceiver, filter)
        }

        setContent {
            YlowstoneApp(
                prefs = prefs,
                repo = repo,
                isInPipMode = isInPipMode,
                pipCommandNonce = pipCommandNonce,
                pipCommandAction = pipCommandAction,
                onRequestLandscape = { landscape ->
                    val target = if (landscape) {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                    if (requestedOrientation != target) {
                        requestedOrientation = target
                    }
                },
                onRequestImmersive = { immersive ->
                    setImmersiveMode(immersive)
                },
                onSetSecureScreen = { secure ->
                    if (secure) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                },
                onPipStateChanged = { eligible, playing ->
                    isPipEligible = eligible
                    isPlayerPlaying = playing
                    updatePipParams(eligible && playing, playing)
                },
                onEnterSystemPip = {
                    enterPipNow()
                },
                onExitApp = {
                    finish()
                }
            )
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pipReceiver) }
        super.onDestroy()
    }

    private fun makeRemoteAction(iconRes: Int, title: String, cmd: String, reqCode: Int): RemoteAction {
        val intent = Intent(ACTION_PIP_CONTROL).setPackage(packageName).putExtra(EXTRA_CMD, cmd)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(this, reqCode, intent, flags)
        return RemoteAction(Icon.createWithResource(this, iconRes), title, title, pi)
    }

    private fun buildPipParams(autoEnter: Boolean, playing: Boolean): PictureInPictureParams {
        val actions = listOf(
            makeRemoteAction(
                if (playing) R.drawable.ic_pip_pause else R.drawable.ic_pip_play,
                if (playing) "Pause" else "Play",
                "TOGGLE",
                102
            )
        )
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(actions)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun updatePipParams(autoEnter: Boolean, playing: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { setPictureInPictureParams(buildPipParams(autoEnter, playing)) }
        }
    }

    private fun enterPipNow() {
        runCatching {
            enterPictureInPictureMode(buildPipParams(isPipEligible && isPlayerPlaying, isPlayerPlaying))
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPipEligible && isPlayerPlaying && !isInPipMode && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            enterPipNow()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
    }

    private fun setImmersiveMode(enabled: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (enabled) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
