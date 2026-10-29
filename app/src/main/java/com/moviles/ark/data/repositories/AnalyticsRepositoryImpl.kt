package com.moviles.ark.data.repositories

import android.os.Build
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moviles.ark.data.remote.dtos.toDto
import com.moviles.ark.domain.models.ScreenTimeEntry
import com.moviles.ark.domain.models.ToolLoadLatency
import com.moviles.ark.domain.repositories.AnalyticsRepository
import java.util.Date

//envia los eventos de analitica a firebase analytics
//la latencia de herramientas y el tiempo de pantalla tambien se guardan en firestore para que lleguen a bigquery en segundos
class AnalyticsRepositoryImpl(
    private val analytics: FirebaseAnalytics,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    //telefono donde se midio; el dashboard separa el emulador de los celulares reales
    private val deviceModel: String = Build.MODEL.orEmpty(),
    private val osVersion: String = Build.VERSION.RELEASE.orEmpty()
) : AnalyticsRepository {

    override fun logToolLoadLatency(latency: ToolLoadLatency) {
        analytics.logEvent(EVENT_TOOL_LOAD_LATENCY) {
            param(PARAM_TOOL_ID, latency.toolId)
            param(PARAM_LATENCY_MS, latency.latencyMs)
            param(PARAM_ENTRY_POINT, latency.entryPoint.id)
            //analytics no tiene booleanos: 1 si es la primera herramienta desde que arranco la app, 0 si no
            param(PARAM_FIRST_OPEN, if (latency.firstOpen) 1L else 0L)
        }
        saveToolLoadLatency(latency)
    }

    //sin sesion no hay carpeta users/{uid} donde guardarla; el evento de analytics se envia igual
    //no se espera la respuesta: firestore la sube en cuanto hay red y, sin red, la deja en cola hasta que vuelva
    private fun saveToolLoadLatency(latency: ToolLoadLatency) {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users")
            .document(userId)
            .collection(COLLECTION_TOOL_LOAD_LATENCY)
            .add(latency.toDto(userId, deviceModel, osVersion, Date()))
            .addOnFailureListener { Log.w("AnalyticsRepository", "Could not save tool load latency", it) }
    }

    override fun logScreenTime(entry: ScreenTimeEntry) {
        analytics.logEvent(EVENT_APP_SCREEN_TIME) {
            param(PARAM_DAY, entry.day)
            param(PARAM_DURATION_MS, entry.durationMs)
            param(PARAM_START_HOUR, entry.startHour.toLong())
        }
        saveScreenTime(entry)
    }

    //igual que la latencia: users/{uid}/screen_time llega a bigquery en segundos, analytics tarda hasta una hora
    private fun saveScreenTime(entry: ScreenTimeEntry) {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users")
            .document(userId)
            .collection(COLLECTION_SCREEN_TIME)
            .add(entry.toDto(userId, deviceModel, osVersion, Date()))
            .addOnFailureListener { Log.w("AnalyticsRepository", "Could not save screen time", it) }
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
        //coleccion users/{uid}/tool_load_latency en firestore (la lee la extension que la copia a bigquery)
        const val COLLECTION_TOOL_LOAD_LATENCY = "tool_load_latency"

        //nombres de la pregunta de negocio del tiempo de pantalla diario
        const val EVENT_APP_SCREEN_TIME = "app_screen_time"
        const val PARAM_DAY = "day"
        const val PARAM_DURATION_MS = "duration_ms"
        const val PARAM_START_HOUR = "start_hour"
        //coleccion users/{uid}/screen_time en firestore (la lee la extension que la copia a bigquery)
        const val COLLECTION_SCREEN_TIME = "screen_time"

        //nombres de la pregunta de negocio #4
        const val EVENT_TOOL_ENTRY_COMPLETED = "tool_entry_completed"
        const val PARAM_TOOL_FORMAT = "tool_format"
    }
}
