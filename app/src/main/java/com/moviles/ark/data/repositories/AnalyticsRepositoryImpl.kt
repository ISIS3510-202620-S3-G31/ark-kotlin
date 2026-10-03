package com.moviles.ark.data.repositories

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.moviles.ark.domain.models.ToolLoadLatency
import com.moviles.ark.domain.repositories.AnalyticsRepository

//envia los eventos de analitica a firebase analytics
class AnalyticsRepositoryImpl(
    private val analytics: FirebaseAnalytics
) : AnalyticsRepository {

    override fun logToolLoadLatency(latency: ToolLoadLatency) {
        analytics.logEvent(EVENT_TOOL_LOAD_LATENCY) {
            param(PARAM_TOOL_ID, latency.toolId)
            param(PARAM_LATENCY_MS, latency.latencyMs)
        }
    }

    override fun logToolEntryCompleted(toolId: String, toolFormat: String) {
        analytics.logEvent(EVENT_TOOL_ENTRY_COMPLETED) {
            param(PARAM_TOOL_ID, toolId)
            param(PARAM_TOOL_FORMAT, toolFormat)
        }
    }

    companion object {
        //nombres que pide el issue #8 (pregunta de negocio #1)
        const val EVENT_TOOL_LOAD_LATENCY = "tool_load_latency"
        const val PARAM_TOOL_ID = "tool_id"
        const val PARAM_LATENCY_MS = "latency_ms"

        //nombres que pide el issue #34 (pregunta de negocio #4)
        const val EVENT_TOOL_ENTRY_COMPLETED = "tool_entry_completed"
        const val PARAM_TOOL_FORMAT = "tool_format"
    }
}
