package com.moviles.ark.data.remote.dtos

import com.moviles.ark.data.local.entities.CheckInEntity
import com.moviles.ark.domain.composite.CompoundMood
import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.domain.composite.MoodComponent
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
    val emotions: List<EmotionDto> = emptyList(),
    val note: String = ""
)

//de fila de room a dto, para subir los check-ins guardados sin conexion
fun CheckInEntity.toDto(userId: String): MoodCheckInDto {
    return MoodCheckInDto(
        userId = userId,
        moodName = moodName,
        timestamp = timestamp,
        latitude = latitude,
        longitude = longitude,
        emotions = emotions.map { EmotionDto(name = it, intensity = intensity) },
        note = note
    )
}

//de modelo de dominio a dto, para guardar el check-in directo en firestore
fun MoodCheckInModel.toDto(userId: String): MoodCheckInDto {
    val intensity = mood.getIntensity().toInt()
    return MoodCheckInDto(
        userId = userId,
        moodName = mood.getName(),
        timestamp = timestamp,
        latitude = latitude,
        longitude = longitude,
        emotions = mood.getEmotions().map { EmotionDto(name = it.name, intensity = intensity) },
        note = note
    )
}

//de dto a modelo de dominio: una emocion es SingleEmotion, varias son CompoundMood
//si llega un nombre que ya no existe en el enum, se ignora en vez de cerrar la app
fun MoodCheckInDto.toDomain(): MoodCheckInModel {
    val parsedEmotions = emotions.mapNotNull { dto ->
        val emotion = MoodFactory.emotionFromName(dto.name) ?: return@mapNotNull null
        SingleEmotion(emotion, dto.intensity)
    }
    val mood = if (parsedEmotions.isEmpty()) MoodFactory.default() else MoodFactory.fromParts(parsedEmotions, moodName)
    return MoodCheckInModel(
        mood = mood,
        note = note,
        latitude = latitude,
        longitude = longitude,
        timestamp = timestamp
    )
}

//de dto a fila de room (por ejemplo para bajar el historial al telefono); ya viene de firestore, asi que esta sincronizado
fun MoodCheckInDto.toEntity(): CheckInEntity {
    return CheckInEntity(
        userId = userId,
        moodName = moodName,
        emotions = emotions.map { it.name },
        intensity = emotions.firstOrNull()?.intensity ?: SingleEmotion.DEFAULT_INTENSITY,
        note = note,
        latitude = latitude,
        longitude = longitude,
        timestamp = timestamp,
        isSynced = true
    )
}
