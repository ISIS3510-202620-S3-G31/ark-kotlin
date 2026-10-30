package com.moviles.ark.domain.models

import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.domain.composite.MoodComponent
import com.moviles.ark.domain.composite.MoodFactory
import com.moviles.ark.domain.composite.SingleEmotion

//emociones elegidas en el check-in o en el feedback, cada una con su propia intensidad de 1 a 5
//active es la emocion que esta ajustando el slider; el orden es el orden en que se eligieron
data class EmotionSelection(
    val intensities: Map<Emotion, Int> = emptyMap(),
    val active: Emotion? = null
) {
    val emotions: Set<Emotion> get() = intensities.keys

    //intensidad que muestra el slider: la de la emocion activa
    val activeIntensity: Int get() = active?.let { intensities[it] } ?: SingleEmotion.DEFAULT_INTENSITY

    fun isEmpty(): Boolean = intensities.isEmpty()

    //primer toque: la elige con intensidad moderada y el slider pasa a ella
    //toque en una elegida que no esta activa: el slider pasa a ella sin perder su intensidad
    //toque en la activa: se quita
    fun toggle(emotion: Emotion): EmotionSelection = when {
        emotion !in intensities -> copy(intensities = intensities + (emotion to SingleEmotion.DEFAULT_INTENSITY), active = emotion)
        emotion != active -> copy(active = emotion)
        else -> {
            val remaining = intensities - emotion
            copy(intensities = remaining, active = remaining.keys.lastOrNull())
        }
    }

    //el slider solo cambia la emocion activa
    fun withActiveIntensity(intensity: Int): EmotionSelection {
        val emotion = active ?: return this
        val safeIntensity = intensity.coerceIn(SingleEmotion.MIN_INTENSITY, SingleEmotion.MAX_INTENSITY)
        return copy(intensities = intensities + (emotion to safeIntensity))
    }

    fun toParts(): List<SingleEmotion> = intensities.map { (emotion, intensity) -> SingleEmotion(emotion, intensity) }

    //una emocion es SingleEmotion y varias son CompoundMood, cada una con su intensidad
    fun toMood(): MoodComponent = MoodFactory.fromParts(toParts())
}
