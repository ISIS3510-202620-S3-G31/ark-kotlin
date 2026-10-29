package com.moviles.ark.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moviles.ark.domain.composite.MoodFactory
import com.moviles.ark.domain.models.MoodCheckInModel
import java.util.UUID

//tabla de check-ins emocionales guardados en el telefono
//cada fila es un check-in; el id lo pone room solo (autoGenerate)
//version 5 de la base: se agregaron userId y remoteId
@Entity(tableName = "mood_checkins")
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    //usuario que hizo el check-in; asi el home y el sync no mezclan cuentas en el mismo telefono
    val userId: String = "",
    //id del documento en firestore, fijo desde que se guarda: si el sync se repite sobrescribe el mismo documento
    val remoteId: String = UUID.randomUUID().toString(),
    //nombre del animo, por ejemplo "Happiness" o "Nostalgia"
    val moodName: String,
    //emociones como texto ("HAPPINESS", "SADNESS"); room las guarda con el Converters
    val emotions: List<String>,
    //intensidad de 1 a 5
    val intensity: Int,
    val note: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    //fecha y hora en milisegundos, igual que MoodCheckInModel.timestamp
    val timestamp: Long,
    //false mientras no se haya subido a firestore
    val isSynced: Boolean = false,
)

//de modelo de dominio a fila de la tabla
fun MoodCheckInModel.toEntity(userId: String = ""): CheckInEntity {
    return CheckInEntity(
        userId = userId,
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
fun CheckInEntity.toDomain(): MoodCheckInModel {
    //si llega un nombre que ya no existe en el enum, se ignora en vez de cerrar la app
    val emotionList = emotions.mapNotNull { MoodFactory.emotionFromName(it) }
    val mood = MoodFactory.fromEmotions(emotionList, intensity, moodName)
    return MoodCheckInModel(
        mood = mood,
        note = note,
        latitude = latitude,
        longitude = longitude,
        timestamp = timestamp,
    )
}


//alias de retrocompatibilidad con codigo que use MoodCheckInEntity
typealias MoodCheckInEntity = CheckInEntity
