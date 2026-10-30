package com.moviles.ark.domain.composite

//arma el animo con el patron composite: una emocion es SingleEmotion, varias son CompoundMood
//lo usan los viewmodels (check-in y feedback) y los mappers de room y firestore, asi la regla vive en un solo sitio
object MoodFactory {

    //todas las emociones elegidas con la misma intensidad
    fun fromEmotions(
        emotions: Collection<Emotion>,
        intensity: Int = SingleEmotion.DEFAULT_INTENSITY,
        name: String? = null
    ): MoodComponent = fromParts(emotions.map { SingleEmotion(it, intensity) }, name)

    //emociones que ya traen su propia intensidad (por ejemplo las que vienen de firestore)
    fun fromParts(parts: List<SingleEmotion>, name: String? = null): MoodComponent {
        if (parts.size == 1) return parts.first()
        return CompoundMood(customName = name).apply { parts.forEach { add(it) } }
    }

    //animo que se usa cuando un registro viejo no trae ninguna emocion valida
    fun default(): MoodComponent = SingleEmotion(Emotion.HAPPINESS, SingleEmotion.DEFAULT_INTENSITY)

    //nombre guardado -> emocion; si ya no existe en el enum se ignora en vez de cerrar la app
    fun emotionFromName(name: String): Emotion? = Emotion.entries.find { it.name.equals(name, ignoreCase = true) }

    //igual que emotionFromName pero con felicidad cuando el nombre no se reconoce (resumenes viejos de estadisticas)
    fun emotionOrDefault(name: String): Emotion = emotionFromName(name) ?: Emotion.HAPPINESS

    //cada emocion con su propia intensidad, recorriendo el composite hasta las hojas
    fun partsOf(mood: MoodComponent): List<SingleEmotion> =
        if (mood is SingleEmotion) listOf(mood) else mood.getChildren().flatMap { partsOf(it) }

    //room guarda cada emocion con su intensidad en la misma columna de texto: "HAPPINESS:4"
    fun encodeParts(parts: List<SingleEmotion>): List<String> = parts.map { "${it.emotion.name}$PART_SEPARATOR${it.intensity}" }

    //lee "HAPPINESS:4"; las filas viejas solo traen "HAPPINESS" y usan la intensidad general de la fila
    fun decodeParts(stored: List<String>, fallbackIntensity: Int): List<SingleEmotion> = stored.mapNotNull { item ->
        val emotion = emotionFromName(item.substringBefore(PART_SEPARATOR)) ?: return@mapNotNull null
        val intensity = item.substringAfter(PART_SEPARATOR, "").toIntOrNull() ?: fallbackIntensity
        SingleEmotion(emotion, intensity.coerceIn(SingleEmotion.MIN_INTENSITY, SingleEmotion.MAX_INTENSITY))
    }

    private const val PART_SEPARATOR = ":"
}
