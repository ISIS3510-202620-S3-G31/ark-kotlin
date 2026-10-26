package com.moviles.ark.data.local.sensors

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.moviles.ark.ui.viewmodels.BreathingPhase

/**
 * Sensor helper that triggers a subtle haptic cue via VibrationEffect
 * when the guided breathing goes from Hold to Exhale.
 */
class HapticFeedbackHelper(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    //la unica vibracion del ejercicio es al pasar de hold a exhale: avisa que ya se puede soltar el aire
    //los demas cambios de fase no vibran para no distraer
    fun onPhaseChanged(previous: BreathingPhase?, current: BreathingPhase) {
        if (previous == BreathingPhase.HOLD && current == BreathingPhase.EXHALE) {
            vibrateExhaleCue()
        }
    }

    private fun vibrateExhaleCue() {
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
