package com.moviles.ark.data.local.sensors

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.moviles.ark.domain.adapters.HapticFeedbackAdapter
import com.moviles.ark.domain.models.BreathingPhase

/**
 * Sensor helper that triggers a haptic cue via VibrationEffect when every phase of the guided breathing starts:
 * a long pulse to inhale, a short one to hold and a double pulse to exhale.
 */
class HapticFeedbackHelper(private val context: Context) : HapticFeedbackAdapter {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    //se distinguen por la duracion y el numero de pulsos, no por la fuerza: hay telefonos que no regulan la intensidad
    override fun vibratePhaseCue(phase: BreathingPhase) {
        when (phase) {
            //pulso largo: empieza un ciclo, tomar aire
            BreathingPhase.INHALE -> vibrate(timings = longArrayOf(0L, 90L), amplitudes = intArrayOf(0, 200), fallbackMs = 90L)
            //pulso corto: sostener el aire o la pausa con los pulmones vacios
            BreathingPhase.HOLD, BreathingPhase.HOLD_POST_EXHALE ->
                vibrate(timings = longArrayOf(0L, 45L), amplitudes = intArrayOf(0, 160), fallbackMs = 45L)
            //doble pulso: soltar el aire
            BreathingPhase.EXHALE ->
                vibrate(timings = longArrayOf(0L, 50L, 80L, 50L), amplitudes = intArrayOf(0, 180, 0, 140), fallbackMs = 50L)
            BreathingPhase.READY -> Unit
        }
    }

    private fun vibrate(timings: LongArray, amplitudes: IntArray, fallbackMs: Long) {
        val targetVibrator = vibrator ?: return
        if (!targetVibrator.hasVibrator()) return

        try {
            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) VibrationEffect.createWaveform(timings, amplitudes, -1) else null
            //sin decir el uso, android toma la pulsacion corta como vibracion al tocar y la ignora si el usuario
            //apago esa opcion (pasaba en el miro); como media respeta solo el ajuste de vibracion de multimedia
            if (effect != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                targetVibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA))
            } else if (effect != null) {
                @Suppress("DEPRECATION")
                targetVibrator.vibrate(
                    effect,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                targetVibrator.vibrate(fallbackMs)
            }
        } catch (_: Exception) {
            // Failsafe silencioso para dispositivos sin soporte de vibrador o permisos restringidos
        }
    }
}
