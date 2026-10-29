package com.moviles.ark.data.local.sensors

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.moviles.ark.domain.adapters.HapticFeedbackAdapter

/**
 * Sensor helper that triggers a subtle haptic cue via VibrationEffect
 * when the guided breathing goes from Hold to Exhale.
 */
class HapticFeedbackHelper(private val context: Context) : HapticFeedbackAdapter {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    //doble pulsacion suave; cuando vibrar lo decide BreathingPattern.cuesExhale
    override fun vibrateExhaleCue() {
        val targetVibrator = vibrator ?: return
        if (!targetVibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Doble pulsacion suave que marca la exhalacion y alivio
                targetVibrator.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0L, 35L, 45L, 30L),
                        intArrayOf(0, 100, 0, 70),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                targetVibrator.vibrate(35L)
            }
        } catch (_: Exception) {
            // Failsafe silencioso para dispositivos sin soporte de vibrador o permisos restringidos
        }
    }
}
