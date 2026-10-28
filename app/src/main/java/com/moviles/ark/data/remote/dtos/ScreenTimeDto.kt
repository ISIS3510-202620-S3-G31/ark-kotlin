package com.moviles.ark.data.remote.dtos

import com.moviles.ark.domain.models.ScreenTimeEntry
import java.util.Date

//dto del documento users/{uid}/screen_time/{id} en firestore
//un pedazo de tiempo con la app en pantalla; sumando los pedazos de un dia sale el tiempo de pantalla de ese dia
//la app manda un pedazo cada minuto mientras sigue abierta y otro al salir
data class ScreenTimeDto(
    val userId: String = "",
    //dia local en formato yyyy-MM-dd
    val day: String = "",
    val durationMs: Long = 0L,
    //hora local (0 a 23) en que empezo el pedazo
    val startHour: Int = 0,
    //modelo del telefono (Build.MODEL); en el emulador empieza por sdk_gphone
    val deviceModel: String = "",
    //version de android, por ejemplo "16"
    val osVersion: String = "",
    //cuando se envio; firestore lo guarda como Timestamp
    val timestamp: Date = Date(0)
)

//de modelo de dominio a dto; el telefono y la fecha los pone quien guarda, el modelo no los conoce
fun ScreenTimeEntry.toDto(userId: String, deviceModel: String, osVersion: String, timestamp: Date): ScreenTimeDto {
    return ScreenTimeDto(
        userId = userId,
        day = day,
        durationMs = durationMs,
        startHour = startHour,
        deviceModel = deviceModel,
        osVersion = osVersion,
        timestamp = timestamp
    )
}
