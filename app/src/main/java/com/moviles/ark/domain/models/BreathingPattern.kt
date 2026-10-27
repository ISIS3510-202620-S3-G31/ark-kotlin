package com.moviles.ark.domain.models

//fases del ejercicio de respiracion guiada
enum class BreathingPhase(val label: String, val instruction: String) {
    READY("Ready", "Tap Start to begin your breathing exercise"),
    INHALE("Inhale", "Breathe in deeply through your nose"),
    HOLD("Hold", "Hold your breath calmly"),
    EXHALE("Exhale", "Release slowly through your mouth"),
    HOLD_POST_EXHALE("Hold", "Rest gently with empty lungs")
}

//ritmo de respiracion en segundos y sus reglas: que fase sigue, cuanto dura cada una y cuando se completa un ciclo
data class BreathingPattern(
    val inhale: Int,
    val hold: Int,
    val exhale: Int,
    val holdPostExhale: Int = 0
) {
    fun durationOf(phase: BreathingPhase): Int = when (phase) {
        BreathingPhase.READY, BreathingPhase.INHALE -> inhale
        BreathingPhase.HOLD -> hold
        BreathingPhase.EXHALE -> exhale
        BreathingPhase.HOLD_POST_EXHALE -> holdPostExhale
    }

    //fase que sigue y si con ella se completo un ciclo; las pausas en 0 se saltan
    fun next(phase: BreathingPhase): Pair<BreathingPhase, Boolean> = when (phase) {
        BreathingPhase.READY -> BreathingPhase.INHALE to false
        BreathingPhase.INHALE -> (if (hold > 0) BreathingPhase.HOLD else BreathingPhase.EXHALE) to false
        BreathingPhase.HOLD -> BreathingPhase.EXHALE to false
        BreathingPhase.EXHALE -> if (holdPostExhale > 0) BreathingPhase.HOLD_POST_EXHALE to false else BreathingPhase.INHALE to true
        BreathingPhase.HOLD_POST_EXHALE -> BreathingPhase.INHALE to true
    }

    //el preset que tiene exactamente este ritmo, o null si es uno propio
    fun matchingPreset(): BreathingPreset? = BreathingPreset.entries.find {
        it.inhale == inhale && it.hold == hold && it.exhale == exhale && it.holdPostExhale == holdPostExhale
    }

    //inhalar y exhalar duran al menos 1 s y las pausas no pueden ser negativas
    fun normalized(): BreathingPattern = copy(
        inhale = inhale.coerceAtLeast(1),
        hold = hold.coerceAtLeast(0),
        exhale = exhale.coerceAtLeast(1),
        holdPostExhale = holdPostExhale.coerceAtLeast(0)
    )

    companion object {
        //rangos del ritmo Custom: inhalar y exhalar de 2 a 10 s, sostener de 0 a 10 s
        const val CUSTOM_MIN_BREATH_SECONDS = 2
        const val CUSTOM_MAX_SECONDS = 10
        val CUSTOM_BREATH_RANGE = CUSTOM_MIN_BREATH_SECONDS..CUSTOM_MAX_SECONDS
        val CUSTOM_HOLD_RANGE = 0..CUSTOM_MAX_SECONDS

        //ritmo Custom ya dentro de los rangos permitidos
        fun custom(inhale: Int, hold: Int, exhale: Int): BreathingPattern = BreathingPattern(
            inhale = inhale.coerceIn(CUSTOM_BREATH_RANGE),
            hold = hold.coerceIn(CUSTOM_HOLD_RANGE),
            exhale = exhale.coerceIn(CUSTOM_BREATH_RANGE)
        )

        //la unica vibracion del ejercicio es al pasar de hold a exhale: avisa que ya se puede soltar el aire
        fun cuesExhale(previous: BreathingPhase, current: BreathingPhase): Boolean =
            previous == BreathingPhase.HOLD && current == BreathingPhase.EXHALE

        //se puede reiniciar si ya empezo o si termino algun ciclo
        fun canReset(phase: BreathingPhase, completedCycles: Int): Boolean =
            phase != BreathingPhase.READY || completedCycles > 0
    }
}
