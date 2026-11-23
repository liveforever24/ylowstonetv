package com.ylowstone.tv

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
                onInstallApk = { apkFile ->
                    installApkFile(apkFile)
                }
            )
        }
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
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Installer error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
