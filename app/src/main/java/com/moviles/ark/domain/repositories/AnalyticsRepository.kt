package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.ToolLoadLatency

//eventos de analitica para las preguntas de negocio
//el dominio no sabe que detras esta firebase analytics; la implementacion vive en data
interface AnalyticsRepository {
    //evento tool_load_latency (tool_id, latency_ms), pregunta de negocio #1 (#8)
    fun logToolLoadLatency(latency: ToolLoadLatency)

    //evento tool_entry_completed (tool_id, tool_format), pregunta de negocio #4 (#34)
    fun logToolEntryCompleted(toolId: String, toolFormat: String)
}
