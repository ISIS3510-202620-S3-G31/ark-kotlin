package com.moviles.ark.data.remote.dtos

import com.moviles.ark.domain.models.ToolLoadLatency
import java.util.Date

//dto del documento users/{uid}/tool_load_latency/{id} en firestore
//es la misma medicion del evento tool_load_latency de analytics, pero por firestore llega a bigquery en segundos
//(analytics junta los eventos en el telefono y los sube en tandas de hasta una hora)
data class ToolLoadLatencyDto(
    val userId: String = "",
    val toolId: String = "",
    val latencyMs: Long = 0L,
    //recommendation, surprise_me, weather o catalog
    val entryPoint: String = "",
    //true si fue la primera herramienta que se abrio desde que arranco la app
    val firstOpen: Boolean = false,
    //modelo del telefono (Build.MODEL); en el emulador empieza por sdk_gphone
    val deviceModel: String = "",
    //version de android, por ejemplo "16"
    val osVersion: String = "",
    //cuando se midio; firestore lo guarda como Timestamp
    val timestamp: Date = Date(0)
)

//de modelo de dominio a dto; el telefono y la fecha los pone quien guarda, el modelo no los conoce
fun ToolLoadLatency.toDto(userId: String, deviceModel: String, osVersion: String, timestamp: Date): ToolLoadLatencyDto {
    return ToolLoadLatencyDto(
        userId = userId,
        toolId = toolId,
        latencyMs = latencyMs,
        entryPoint = entryPoint.id,
        firstOpen = firstOpen,
        deviceModel = deviceModel,
        osVersion = osVersion,
        timestamp = timestamp
    )
}
