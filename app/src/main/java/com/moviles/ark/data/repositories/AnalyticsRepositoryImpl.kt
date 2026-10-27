package com.moviles.ark.data.repositories

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.moviles.ark.domain.models.ScreenTimeEntry
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
            param(PARAM_ENTRY_POINT, latency.entryPoint.id)
            //analytics no tiene booleanos: 1 si es la primera herramienta desde que arranco la app, 0 si no
            param(PARAM_FIRST_OPEN, if (latency.firstOpen) 1L else 0L)
        }
    }

    override fun logScreenTime(entry: ScreenTimeEntry) {
        analytics.logEvent(EVENT_APP_SCREEN_TIME) {
            param(PARAM_DAY, entry.day)
            param(PARAM_DURATION_MS, entry.durationMs)
            param(PARAM_START_HOUR, entry.startHour.toLong())
        }
    }

    override fun setUserId(userId: String?) {
        analytics.setUserId(userId)
    }

    override fun logToolEntryCompleted(toolId: String, toolFormat: String) {
        analytics.logEvent(EVENT_TOOL_ENTRY_COMPLETED) {
            param(PARAM_TOOL_ID, toolId)
            param(PARAM_TOOL_FORMAT, toolFormat)
        }
    }

    companion object {
        //nombres de la pregunta de negocio #1
        const val EVENT_TOOL_LOAD_LATENCY = "tool_load_latency"
        const val PARAM_TOOL_ID = "tool_id"
        const val PARAM_LATENCY_MS = "latency_ms"
        const val PARAM_ENTRY_POINT = "entry_point"
        const val PARAM_FIRST_OPEN = "first_open"

        //nombres de la pregunta de negocio del tiempo de pantalla diario
        const val EVENT_APP_SCREEN_TIME = "app_screen_time"
        const val PARAM_DAY = "day"
        const val PARAM_DURATION_MS = "duration_ms"
        const val PARAM_START_HOUR = "start_hour"

        //nombres de la pregunta de negocio #4
        const val EVENT_TOOL_ENTRY_COMPLETED = "tool_entry_completed"
        const val PARAM_TOOL_FORMAT = "tool_format"
    }
}
