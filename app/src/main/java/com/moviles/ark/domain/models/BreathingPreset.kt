package com.moviles.ark.domain.models

/**
 * Medically verified guided breathing presets based on clinical respiratory patterns.
 */
enum class BreathingPreset(
    val title: String,
    val patternLabel: String,
    val inhale: Int,
    val hold: Int,
    val exhale: Int,
    val holdPostExhale: Int,
    val subtitle: String,
    val clinicalBenefit: String
) {
    /**
     * Box Breathing (Sama Vritti Pranayama): 4 equal sides.
     * Medically verified by the Cleveland Clinic and utilized by Navy SEALs / first responders
     * to down-regulate the sympathetic nervous system and rapidly reduce acute cortisol spikes.
     */
    BOX(
        title = "Box Breathing",
        patternLabel = "4-4-4-4",
        inhale = 4,
        hold = 4,
        exhale = 4,
        holdPostExhale = 4,
        subtitle = "Focus & Calm",
        clinicalBenefit = "Equal 4-part cycle that stabilizes heart rate variability (HRV) and clears mental fatigue under acute stress."
    ),

    /**
     * Relaxing Breathing (Dr. Andrew Weil's 4-7-8 method):
     * Prolonged retention and double-length exhalation acts as a natural tranquilizer for the nervous system,
     * slowing the pulse and easing sleep onset or deep anxiety.
     */
    RELAXING(
        title = "Relaxing",
        patternLabel = "4-7-8",
        inhale = 4,
        hold = 7,
        exhale = 8,
        holdPostExhale = 0,
        subtitle = "Deep Rest",
        clinicalBenefit = "Extended 8-second exhalation triggers profound parasympathetic dominance, lowering blood pressure and promoting deep relaxation."
    ),

    /**
     * Quick Calm (Resonant 4-6 pattern):
     * A 1:1.5 inhale-to-exhale ratio without breath retention, clinically indicated for rapid panic relief,
     * asthma-friendly relaxation, and effortless vagus nerve activation.
     */
    QUICK_CALM(
        title = "Quick Calm",
        patternLabel = "4-6",
        inhale = 4,
        hold = 0,
        exhale = 6,
        holdPostExhale = 0,
        subtitle = "Instant Relief",
        clinicalBenefit = "Gentle rhythm without breath-holding that stimulates the vagus nerve to slow racing heartbeats safely and quickly."
    );

    val formattedDescription: String
        get() = if (holdPostExhale > 0) {
            "Inhale ${inhale}s • Hold ${hold}s • Exhale ${exhale}s • Hold ${holdPostExhale}s"
        } else if (hold > 0) {
            "Inhale ${inhale}s • Hold ${hold}s • Exhale ${exhale}s"
        } else {
            "Inhale ${inhale}s • Exhale ${exhale}s"
        }
}
