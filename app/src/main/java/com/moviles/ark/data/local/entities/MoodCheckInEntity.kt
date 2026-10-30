package com.moviles.ark.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moviles.ark.domain.composite.MoodFactory
import com.moviles.ark.domain.composite.SingleEmotion
import com.moviles.ark.domain.models.MoodCheckInModel
import java.util.UUID

//tabla de check-ins emocionales guardados en el telefono
//cada fila es un check-in; el id lo pone room solo (autoGenerate)
//version 5 de la base: se agregaron userId y remoteId; version 8: cityName
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
    //cada emocion con su intensidad ("HAPPINESS:4", "SADNESS:2"); room las guarda con el Converters
    //las filas viejas solo traen el nombre ("HAPPINESS") y usan la columna intensity
    val emotions: List<String>,
    //intensidad mas alta de 1 a 5 entre las emociones del check-in
    val intensity: Int,
    val note: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    //ciudad que se mostro al guardar; null sin red o sin ubicacion
    val cityName: String? = null,
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
        emotions = MoodFactory.encodeParts(MoodFactory.partsOf(mood)),
        intensity = MoodFactory.partsOf(mood).maxOfOrNull { it.intensity } ?: SingleEmotion.DEFAULT_INTENSITY,
        note = note,
        latitude = latitude,
        longitude = longitude,
        cityName = cityName,
        timestamp = timestamp,
    )
}

//de fila de la tabla a modelo de dominio: una emocion es SingleEmotion, varias son CompoundMood
fun CheckInEntity.toDomain(): MoodCheckInModel {
    //si llega un nombre que ya no existe en el enum, se ignora en vez de cerrar la app
    val parts = MoodFactory.decodeParts(emotions, intensity)
    val mood = if (parts.isEmpty()) MoodFactory.default() else MoodFactory.fromParts(parts, moodName)
    return MoodCheckInModel(
        mood = mood,
        note = note,
        latitude = latitude,
        longitude = longitude,
        cityName = cityName,
        timestamp = timestamp,
    )
}


//alias de retrocompatibilidad con codigo que use MoodCheckInEntity
typealias MoodCheckInEntity = CheckInEntity
