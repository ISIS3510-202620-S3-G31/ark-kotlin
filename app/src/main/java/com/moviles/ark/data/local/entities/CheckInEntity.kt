package com.moviles.ark.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moviles.ark.domain.composite.CompoundMood
import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.domain.composite.MoodComponent
import com.moviles.ark.domain.composite.SingleEmotion
import com.moviles.ark.domain.models.CheckInModel

//tabla de check-ins emocionales guardados en el telefono (#12, #33)
//cada fila es un check-in; el id lo pone room solo (autoGenerate)
@Entity(tableName = "mood_checkins")
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    //nombre del animo, por ejemplo "Happiness" o "Nostalgia"
    val moodName: String,
    //emociones como texto ("HAPPINESS", "SADNESS"); room las guarda con el Converters
    val emotions: List<String>,
    //intensidad de 1 a 5
    val intensity: Int,
    val note: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    //fecha y hora en milisegundos, igual que CheckInModel.timestamp
    val timestamp: Long,
    //false mientras no se haya subido a firestore (#33)
    val isSynced: Boolean = false,
)

//de modelo de dominio a fila de la tabla
fun CheckInModel.toEntity(): CheckInEntity {
    return CheckInEntity(
        moodName = mood.getName(),
        emotions = mood.getEmotions().map { it.name },
        intensity = mood.getIntensity().toInt(),
        note = note,
        latitude = latitude,
        longitude = longitude,
        timestamp = timestamp,
    )
}

//de fila de la tabla a modelo de dominio: una emocion es SingleEmotion, varias son CompoundMood
fun CheckInEntity.toDomain(): CheckInModel {
    //si llega un nombre que ya no existe en el enum, se ignora en vez de cerrar la app
    val emotionList = emotions.mapNotNull { name -> Emotion.entries.find { it.name == name } }
    val mood: MoodComponent = if (emotionList.size == 1) {
        SingleEmotion(emotionList.first(), intensity)
    } else {
        CompoundMood(customName = moodName).apply {
            emotionList.forEach { add(SingleEmotion(it, intensity)) }
        }
    }
    return CheckInModel(
        mood = mood,
        note = note,
        latitude = latitude,
        longitude = longitude,
        timestamp = timestamp,
    )
}
