package com.ylowstone.tv.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.view.HapticFeedbackConstants
import android.view.View
import com.ylowstone.tv.R

/**
 * Mesin Micro-Haptics & Suara Navigasi Elegan ala TV Flagship (v2.5.0).
 * Menggunakan SoundPool berlatensi rendah dengan envelope akustik lembut dan sakelar on/off.
 */
object FlagshipFeedback {

    @Volatile
    var soundsEnabled: Boolean = true

    private var soundPool: SoundPool? = null
    private var tickSoundId: Int = 0
    private var selectSoundId: Int = 0
    @Volatile
    private var initialized: Boolean = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            runCatching {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val sp = SoundPool.Builder()
                    .setMaxStreams(3)
                    .setAudioAttributes(attrs)
                    .build()
                tickSoundId = sp.load(context.applicationContext, R.raw.ui_tick_flagship, 1)
                selectSoundId = sp.load(context.applicationContext, R.raw.ui_select_flagship, 1)
                soundPool = sp
                initialized = true
            }
        }
    }

    fun playTick(view: View? = null) {
        view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        if (!soundsEnabled) return
        val sp = soundPool ?: return
        if (tickSoundId != 0) {
            runCatching { sp.play(tickSoundId, 0.28f, 0.28f, 1, 0, 1.0f) }
        }
    }

    fun playSelect(view: View? = null) {
        view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        if (!soundsEnabled) return
        val sp = soundPool ?: return
        if (selectSoundId != 0) {
            runCatching { sp.play(selectSoundId, 0.34f, 0.34f, 1, 0, 1.0f) }
        }
    }

    fun release() {
        synchronized(this) {
            runCatching { soundPool?.release() }
            soundPool = null
            tickSoundId = 0
            selectSoundId = 0
            initialized = false
        }
    }
}
