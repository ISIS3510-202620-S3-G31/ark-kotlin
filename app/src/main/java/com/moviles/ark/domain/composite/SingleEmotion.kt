package com.moviles.ark.domain.composite

//leaf: representa una sola emocion con su intensidad (1 a 5)
//al pasar val dentro de los parametros, crea esos atributos y sus getters
class SingleEmotion(var emotion: Emotion, var intensity: Int = 3) : MoodComponent {

    //retorna el nombre formateado (ej: "Happiness")
    override fun getName(): String {
        return emotion.name.lowercase().replaceFirstChar { it.uppercase() }
    }

    //retorna una lista que contiene unicamente esta emocion
    override fun getEmotions(): List<Emotion> {
        return listOf(emotion) //solo de lectura
    }

    //retorna su intensidad directa en formato decimal
    override fun getIntensity(): Float {
        return intensity.toFloat()
    }
}