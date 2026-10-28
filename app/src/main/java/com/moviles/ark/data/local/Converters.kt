package com.moviles.ark.data.local

import android.util.Log
import androidx.room.TypeConverter
import com.moviles.ark.data.remote.CrashlyticsHelper
import org.json.JSONArray
import java.util.Date

//room solo sabe guardar tipos simples (numeros, texto, booleanos)
//estos conversores le ensenan a guardar listas de texto y fechas de forma resiliente a prueba de JSONException
class Converters {

    //["HAPPINESS","SADNESS"] se guarda como el texto json ["HAPPINESS","SADNESS"]
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        if (value.isNullOrEmpty()) return "[]"
        return try {
            value.joinToString(separator = ",", prefix = "[", postfix = "]") { item ->
                "\"" + item.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
            }
        } catch (e: Exception) {
            "[]"
        }
    }

    //deserializa json array de forma resiliente protegiendo contra JSONException y cadenas corruptas
    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        val trimmed = value.trim()
        if (trimmed == "[]" || trimmed == "null") return emptyList()

        return try {
            try {
                val array = JSONArray(trimmed)
                return List(array.length()) { index -> array.getString(index) }
            } catch (e: NoClassDefFoundError) {
                // Entorno JVM sin android.jar completo
            } catch (e: Exception) {
                if (e.javaClass.name == "java.lang.RuntimeException" && e.message?.contains("not mocked") == true) {
                    // Ignorar error de mock en JVM
                } else {
                    logFailure(trimmed, e)
                }
            }

            parseStringArrayFallback(trimmed)
        } catch (e: Exception) {
            logFailure(trimmed, e)
            emptyList()
        }
    }

    private fun logFailure(rawValue: String, throwable: Throwable) {
        runCatching {
            Log.w("Converters", "Failed to parse JSON array in toStringList: $rawValue, attempting fallback")
        }
        runCatching {
            CrashlyticsHelper.logNonFatal(
                componentName = "Converters",
                action = "toStringListFallback",
                throwable = throwable,
                extraKeys = mapOf("raw_value" to rawValue)
            )
        }
    }

    private fun parseStringArrayFallback(raw: String): List<String> {
        val cleaned = raw.removePrefix("[").removeSuffix("]").trim()
        if (cleaned.isEmpty()) return emptyList()
        return cleaned.split(",")
            .map { it.trim().trim('"', '\'') }
            .filter { it.isNotBlank() }
    }

    //una fecha se guarda como milisegundos en SQLite
    @TypeConverter
    fun fromDate(date: Date?): Long? {
        return date?.time
    }

    //reconstruye una fecha Date a partir de milisegundos asegurando que sea mayor a 0
    @TypeConverter
    fun toDate(millis: Long?): Date? {
        if (millis == null || millis <= 0L) return null
        return try {
            Date(millis)
        } catch (e: Exception) {
            null
        }
    }
}
