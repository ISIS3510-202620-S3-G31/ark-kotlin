package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.ScreenTimeEntry
import com.moviles.ark.domain.models.ToolLoadLatency

//eventos de analitica para las preguntas de negocio
//el dominio no sabe que detras esta firebase analytics; la implementacion vive en data
interface AnalyticsRepository {
    //evento tool_load_latency (tool_id, latency_ms, entry_point, first_open), pregunta de negocio #1
    fun logToolLoadLatency(latency: ToolLoadLatency)

    //evento tool_entry_completed (tool_id, tool_format), pregunta de negocio #4
    fun logToolEntryCompleted(toolId: String, toolFormat: String)

    //evento app_screen_time (day, duration_ms, start_hour), pregunta de negocio del tiempo de pantalla diario
    fun logScreenTime(entry: ScreenTimeEntry)

    //asocia los eventos siguientes a la cuenta (uid de firebase auth) para poder sumar por usuario; null al cerrar sesion
    fun setUserId(userId: String?)
}
