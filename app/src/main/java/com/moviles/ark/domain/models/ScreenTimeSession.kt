package com.moviles.ark.domain.models

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

//un rato seguido con la app en pantalla: desde que se abre hasta que pasa a segundo plano
//responde la pregunta de negocio del tiempo de pantalla diario por usuario con el evento app_screen_time
data class ScreenTimeSession(
    //fechas en milisegundos (System.currentTimeMillis)
    val startMillis: Long,
    val endMillis: Long
) {
    val durationMs: Long get() = endMillis - startMillis

    //reglas de negocio: menos de un segundo no es uso real (por ejemplo la app se abrio y se cerro sola)
    //y mas de un dia seguido es un reloj mal puesto
    fun isValid(): Boolean = durationMs in MIN_DURATION_MS..MAX_DURATION_MS

    //parte la sesion en un pedazo por dia local: si el usuario estaba en la app a medianoche,
    //cada dia se queda con su parte y la suma por dia sigue siendo correcta
    fun splitByDay(timeZone: TimeZone = TimeZone.getDefault()): List<ScreenTimeEntry> {
        if (!isValid()) return emptyList()
        val entries = mutableListOf<ScreenTimeEntry>()
        var chunkStart = startMillis
        while (chunkStart < endMillis) {
            val calendar = Calendar.getInstance(timeZone, Locale.US).apply { timeInMillis = chunkStart }
            val day = String.format(
                Locale.US, "%04d-%02d-%02d",
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH)
            )
            val startHour = calendar.get(Calendar.HOUR_OF_DAY)
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val chunkEnd = minOf(endMillis, calendar.timeInMillis)
            entries.add(ScreenTimeEntry(day = day, durationMs = chunkEnd - chunkStart, startHour = startHour))
            chunkStart = chunkEnd
        }
        return entries
    }

    companion object {
        const val MIN_DURATION_MS = 1_000L
        const val MAX_DURATION_MS = 24L * 60 * 60 * 1000
    }
}

//el pedazo de una sesion que cae en un dia
data class ScreenTimeEntry(
    //dia local en formato yyyy-MM-dd, el mismo que se usa para agrupar por dia en el dashboard
    val day: String,
    val durationMs: Long,
    //hora local (0 a 23) en que empezo este pedazo, para ver a que horas se usa la app
    val startHour: Int
)
