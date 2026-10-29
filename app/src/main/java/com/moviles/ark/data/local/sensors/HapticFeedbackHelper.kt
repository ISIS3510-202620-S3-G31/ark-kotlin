package com.moviles.ark.data.local.sensors

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.moviles.ark.domain.adapters.HapticFeedbackAdapter

/**
 * Sensor helper that triggers a subtle haptic cue via VibrationEffect
 * on every phase change of the guided breathing (a double pulse for Hold to Exhale).
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
        // Doble pulsacion suave que marca la exhalacion y alivio
        vibrate(timings = longArrayOf(0L, 35L, 45L, 30L), amplitudes = intArrayOf(0, 100, 0, 70), fallbackMs = 35L)
    }

    //una pulsacion corta: inhalar, sostener o la pausa despues de exhalar
    override fun vibratePhaseCue() {
        vibrate(timings = longArrayOf(0L, 40L), amplitudes = intArrayOf(0, 90), fallbackMs = 40L)
    }

    private fun vibrate(timings: LongArray, amplitudes: IntArray, fallbackMs: Long) {
        val targetVibrator = vibrator ?: return
        if (!targetVibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                targetVibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                targetVibrator.vibrate(fallbackMs)
            }
        } catch (_: Exception) {
            // Failsafe silencioso para dispositivos sin soporte de vibrador o permisos restringidos
        }
    }
}
