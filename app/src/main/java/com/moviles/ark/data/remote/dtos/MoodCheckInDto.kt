package com.moviles.ark.data.remote.dtos

import com.moviles.ark.data.local.entities.CheckInEntity
import com.moviles.ark.domain.composite.MoodFactory
import com.moviles.ark.domain.composite.SingleEmotion
import com.moviles.ark.domain.models.MoodCheckInModel

//dto para serializar cada emocion con su respectiva intensidad
data class EmotionDto(
    val name: String = "",
    val intensity: Int = 3
)

//dto del documento users/{uid}/mood_checkins/{id} en firestore
//los valores por defecto le dan a firestore el constructor vacio que necesita para leerlo con toObject
data class MoodCheckInDto(
    val userId: String = "",
    //nombre del animo, por ejemplo "Happiness" o "Nostalgia"
    val moodName: String = "",
    //fecha y hora en milisegundos (stats y la racha la leen como numero)
    val timestamp: Long = 0L,
    val latitude: Double? = null,
    val longitude: Double? = null,
    //ciudad del check-in; null sin red o sin ubicacion
    val cityName: String? = null,
    //cada emocion con su propia intensidad
    val emotions: List<EmotionDto> = emptyList(),
    val note: String = ""
)

//el check-in y el feedback suben cada emocion con su intensidad igual
fun List<SingleEmotion>.toEmotionDtos(): List<EmotionDto> = map { EmotionDto(name = it.emotion.name, intensity = it.intensity) }

//si llega un nombre que ya no existe en el enum, se ignora en vez de cerrar la app
fun List<EmotionDto>.toParts(): List<SingleEmotion> = mapNotNull { dto ->
    val emotion = MoodFactory.emotionFromName(dto.name) ?: return@mapNotNull null
    SingleEmotion(emotion, dto.intensity.coerceIn(SingleEmotion.MIN_INTENSITY, SingleEmotion.MAX_INTENSITY))
}

//de fila de room a dto, para subir los check-ins guardados sin conexion
fun CheckInEntity.toDto(userId: String): MoodCheckInDto {
    return MoodCheckInDto(
        userId = userId,
        moodName = moodName,
        timestamp = timestamp,
        latitude = latitude,
        longitude = longitude,
        cityName = cityName,
        emotions = MoodFactory.decodeParts(emotions, intensity).toEmotionDtos(),
        note = note
    )
}

//de modelo de dominio a dto, para guardar el check-in directo en firestore
fun MoodCheckInModel.toDto(userId: String): MoodCheckInDto {
    return MoodCheckInDto(
        userId = userId,
        moodName = mood.getName(),
        timestamp = timestamp,
        latitude = latitude,
        longitude = longitude,
        cityName = cityName,
        emotions = MoodFactory.partsOf(mood).toEmotionDtos(),
        note = note
    )
}

//de dto a modelo de dominio: una emocion es SingleEmotion, varias son CompoundMood
//si llega un nombre que ya no existe en el enum, se ignora en vez de cerrar la app
private fun MoodCheckInDto.parts(): List<SingleEmotion> = emotions.toParts()

fun MoodCheckInDto.toDomain(): MoodCheckInModel {
    val parsedEmotions = parts()
    val mood = if (parsedEmotions.isEmpty()) MoodFactory.default() else MoodFactory.fromParts(parsedEmotions, moodName)
    return MoodCheckInModel(
        mood = mood,
        note = note,
        latitude = latitude,
        longitude = longitude,
        cityName = cityName,
        timestamp = timestamp
    )
}

//de dto a fila de room (por ejemplo para bajar el historial al telefono); ya viene de firestore, asi que esta sincronizado
fun MoodCheckInDto.toEntity(): CheckInEntity {
    return CheckInEntity(
        userId = userId,
        moodName = moodName,
        emotions = MoodFactory.encodeParts(parts()),
        intensity = parts().maxOfOrNull { it.intensity } ?: SingleEmotion.DEFAULT_INTENSITY,
        note = note,
        latitude = latitude,
        longitude = longitude,
        cityName = cityName,
        timestamp = timestamp,
        isSynced = true
    )
}
