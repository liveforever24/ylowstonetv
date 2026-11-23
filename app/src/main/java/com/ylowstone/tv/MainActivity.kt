package com.ylowstone.tv

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ylowstone.tv.data.AppPrefs
import com.ylowstone.tv.data.DatabaseHelper
import com.ylowstone.tv.data.IptvRepository
import com.ylowstone.tv.ui.YlowstoneApp
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var prefs: AppPrefs
    private lateinit var repo: IptvRepository

    private var isInPipMode by mutableStateOf(false)
    private var isPipEligible: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        prefs = AppPrefs(applicationContext)
        val db = DatabaseHelper(applicationContext)
        repo = IptvRepository(applicationContext, db)

        setContent {
            YlowstoneApp(
                prefs = prefs,
                repo = repo,
                isInPipMode = isInPipMode,
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
                onPipEligibilityChanged = { eligible ->
                    isPipEligible = eligible
                    updateAutoPipParams(eligible)
                },
                onEnterSystemPip = {
                    enterPipNow()
                },
                onInstallApk = { apkFile ->
                    installApkFile(apkFile)
                }
            )
        }
    }

    private fun buildPipParams(autoEnter: Boolean): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun updateAutoPipParams(eligible: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { setPictureInPictureParams(buildPipParams(eligible)) }
        }
    }

    private fun enterPipNow() {
        runCatching {
            enterPictureInPictureMode(buildPipParams(isPipEligible))
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPipEligible && !isInPictureInPictureMode && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
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

    private fun installApkFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Installer error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
