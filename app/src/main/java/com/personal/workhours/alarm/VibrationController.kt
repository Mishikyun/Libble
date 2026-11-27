package com.personal.workhours.alarm

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.personal.workhours.domain.model.VibrationPatternType

class VibrationController(private val context: Context) {

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun startLooping(patternType: VibrationPatternType) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        val timings = when (patternType) {
            VibrationPatternType.PULSE -> longArrayOf(0, 500, 500)
            VibrationPatternType.HEARTBEAT -> longArrayOf(0, 180, 120, 260, 700)
            VibrationPatternType.URGENT -> longArrayOf(0, 700, 250, 700, 250)
        }
        val amplitudes = when (patternType) {
            VibrationPatternType.PULSE -> intArrayOf(0, 220, 0)
            VibrationPatternType.HEARTBEAT -> intArrayOf(0, 180, 0, 255, 0)
            VibrationPatternType.URGENT -> intArrayOf(0, 255, 0, 255, 0)
        }

        val effect = VibrationEffect.createWaveform(timings, amplitudes, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attrs = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_ALARM)
                .build()
            vib.vibrate(effect, attrs)
        } else {
            @Suppress("DEPRECATION")
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            @Suppress("DEPRECATION")
            vib.vibrate(effect, audioAttrs)
        }
    }

    fun stop() {
        vibrator?.cancel()
    }
}
