package com.moviles.ark.data.local.sensors

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.moviles.ark.ui.viewmodels.BreathingPhase

/**
 * Sensor helper responsible for triggering subtle haptic vibrations via VibrationEffect
 * during guided breathing phase transitions (Inhale, Hold, Exhale).
 */
class HapticFeedbackHelper(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * Triggers a subtle tactile pulse customized for each breathing transition:
     * - INHALE: Distinct energetic pulse marking the start of oxygen intake.
     * - HOLD / HOLD_POST_EXHALE: Soft, concise tap indicating pause and still lungs.
     * - EXHALE: Gentle decaying wave indicating relaxation and slow air release.
     */
    fun triggerPhaseTransition(phase: BreathingPhase) {
        val targetVibrator = vibrator ?: return
        if (!targetVibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect: VibrationEffect? = when (phase) {
                    BreathingPhase.INHALE -> {
                        // Pulso sutil y claro para iniciar la inhalacion
                        VibrationEffect.createOneShot(45L, 120)
                    }
                    BreathingPhase.HOLD -> {
                        // Toque suave y preciso para sostener
                        VibrationEffect.createOneShot(30L, 80)
                    }
                    BreathingPhase.EXHALE -> {
                        // Doble pulsacion suave que marca la exhalacion y alivio
                        VibrationEffect.createWaveform(
                            longArrayOf(0L, 35L, 45L, 30L),
                            intArrayOf(0, 100, 0, 70),
                            -1
                        )
                    }
                    BreathingPhase.READY -> null
                }
                effect?.let { targetVibrator.vibrate(it) }
            } else {
                @Suppress("DEPRECATION")
                targetVibrator.vibrate(35L)
            }
        } catch (_: Exception) {
            // Failsafe silencioso para dispositivos sin soporte de vibrador o permisos restringidos
        }
    }
}

// Añadir pero en este hay un error