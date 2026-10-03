package com.moviles.ark.domain.models

import java.util.TimeZone

//racha del perfil (#96): cuantos dias seguidos tiene el usuario con al menos un check-in
//1. se pasa cada check-in a su dia en la hora local
//2. se cuenta hacia atras desde hoy; si hoy todavia no hay check-in, se cuenta desde ayer
//   (la racha sigue viva hasta que termina el dia sin check-in)
class CheckInStreak(
    private val zone: TimeZone = TimeZone.getDefault(),
    //se puede cambiar para fijar "ahora"
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    fun daysInARow(checkInTimestamps: List<Long>): Int {
        val daysWithCheckIn = checkInTimestamps.map { dayOf(it) }.toSet()
        val today = dayOf(now())
        var day = if (today in daysWithCheckIn) today else today - 1
        var streak = 0
        while (day in daysWithCheckIn) {
            streak++
            day--
        }
        return streak
    }

    //numero del dia en la hora local; se suma el desfase de la zona para que el dia cambie a medianoche de colombia
    private fun dayOf(millis: Long): Long = (millis + zone.getOffset(millis)) / DAY_MILLIS

    companion object {
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        //ventana que se le pide al repositorio; una racha mas larga que esto se corta aqui
        const val LOOKBACK_DAYS = 366
    }
}
