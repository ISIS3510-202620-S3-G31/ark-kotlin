package com.moviles.ark.domain.models

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

//pregunta de negocio de stats: cuantas veces entra el usuario a estadisticas por semana
//la pantalla muestra las de esta semana y su promedio semanal; se recalcula con cada visita
data class StatsVisitSummary(
    //veces que entro esta semana (de lunes a hoy, hora local)
    val thisWeek: Int = 0,
    //promedio de visitas por semana desde la semana de su primera visita hasta esta
    val weeklyAverage: Double = 0.0,
    //cuantas semanas entran en el promedio
    val weeksTracked: Int = 0
) {
    //sin visitas guardadas la tarjeta no se muestra
    fun hasVisits(): Boolean = weeksTracked > 0

    companion object {
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        //el 1 de enero de 1970 fue jueves: sumar 3 dias deja las semanas empezando el lunes
        private const val MONDAY_OFFSET_DAYS = 3

        //visitsByDay: dia "yyyyMMdd" -> veces que entro ese dia (asi lo guarda la tabla stats_daily_visits)
        fun from(visitsByDay: Map<String, Int>, now: Long, zone: TimeZone = TimeZone.getDefault()): StatsVisitSummary {
            val visitsByWeek = visitsByDay.entries
                .mapNotNull { (dateKey, count) -> weekOfDateKey(dateKey, zone)?.let { it to count } }
                .groupBy({ it.first }, { it.second })
                .mapValues { it.value.sum() }
            if (visitsByWeek.isEmpty()) return StatsVisitSummary()

            val currentWeek = weekOf(now, zone)
            val firstWeek = minOf(visitsByWeek.keys.min(), currentWeek)
            val weeksTracked = (currentWeek - firstWeek + 1).toInt()
            return StatsVisitSummary(
                thisWeek = visitsByWeek[currentWeek] ?: 0,
                weeklyAverage = visitsByWeek.values.sum().toDouble() / weeksTracked,
                weeksTracked = weeksTracked
            )
        }

        //numero de semana (lunes a domingo) en la hora local
        private fun weekOf(millis: Long, zone: TimeZone): Long {
            val localDay = Math.floorDiv(millis + zone.getOffset(millis), DAY_MILLIS)
            return Math.floorDiv(localDay + MONDAY_OFFSET_DAYS, 7L)
        }

        private fun weekOfDateKey(dateKey: String, zone: TimeZone): Long? {
            val format = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
                this.timeZone = zone
                isLenient = false
            }
            //mediodia: lejos de la medianoche para que un cambio de hora no lo pase a otro dia
            val midnight = runCatching { format.parse(dateKey)?.time }.getOrNull() ?: return null
            return weekOf(midnight + DAY_MILLIS / 2, zone)
        }
    }
}
