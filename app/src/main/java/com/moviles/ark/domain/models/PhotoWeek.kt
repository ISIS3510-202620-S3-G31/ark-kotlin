package com.moviles.ark.domain.models

import java.util.Calendar
import java.util.TimeZone

//estado de cada dia en la tira de la semana
enum class PhotoDayState {
    //ese dia hay foto
    TAKEN,
    //hoy, todavia sin foto
    TODAY,
    //dia que ya paso sin foto
    MISSED,
    //dia que no ha llegado
    UPCOMING
}

//un dia de la tira: "M" arriba y el nombre completo para lectores de pantalla
data class PhotoDay(
    val shortLabel: String,
    val name: String,
    val state: PhotoDayState
)

//reglas de la semana de fotos: la semana va de lunes a domingo en la hora local
object PhotoWeek {
    private val SHORT_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")
    private val NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    //inicio de cada dia de la semana actual (lunes a domingo) y el inicio del lunes siguiente
    //se usa Calendar para respetar la zona horaria y los cambios de hora
    fun dayStarts(now: Long, zone: TimeZone): List<Long> {
        val calendar = Calendar.getInstance(zone)
        calendar.timeInMillis = now
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        //retrocede hasta el lunes
        while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            calendar.add(Calendar.DAY_OF_MONTH, -1)
        }
        val starts = mutableListOf<Long>()
        repeat(8) {
            starts.add(calendar.timeInMillis)
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        return starts
    }

    //la tira de 7 dias con el estado de cada uno
    fun build(photos: List<PhotoEntryModel>, now: Long, zone: TimeZone): List<PhotoDay> {
        val starts = dayStarts(now, zone)
        return (0 until 7).map { index ->
            val from = starts[index]
            val until = starts[index + 1]
            val hasPhoto = photos.any { it.takenAt >= from && it.takenAt < until }
            val state = when {
                hasPhoto -> PhotoDayState.TAKEN
                now >= from && now < until -> PhotoDayState.TODAY
                until <= now -> PhotoDayState.MISSED
                else -> PhotoDayState.UPCOMING
            }
            PhotoDay(SHORT_LABELS[index], NAMES[index], state)
        }
    }

    //la foto de hoy, si ya hay; si hay varias, la mas reciente
    fun todayPhoto(photos: List<PhotoEntryModel>, now: Long, zone: TimeZone): PhotoEntryModel? {
        val starts = dayStarts(now, zone)
        val today = (0 until 7).first { now >= starts[it] && now < starts[it + 1] }
        return photos
            .filter { it.takenAt >= starts[today] && it.takenAt < starts[today + 1] }
            .maxByOrNull { it.takenAt }
    }
}
