package com.moviles.ark.domain.models

import java.util.Calendar
import java.util.TimeZone

//estado de cada dia en la tira de los ultimos 7 dias
enum class PhotoDayState {
    //ese dia hay foto
    TAKEN,
    //hoy, todavia sin foto
    TODAY,
    //dia que ya paso sin foto
    MISSED
}

//un dia de la tira: "M" arriba y el nombre completo para lectores de pantalla
data class PhotoDay(
    val shortLabel: String,
    val name: String,
    val state: PhotoDayState
)

//reglas de la tira de fotos: los ultimos 7 dias en la hora local, y el ultimo siempre es hoy
object PhotoWeek {
    private const val DAYS = 7

    //letra y nombre de cada dia segun Calendar.DAY_OF_WEEK (domingo = 1 ... sabado = 7)
    private val SHORT_LABELS = mapOf(
        Calendar.MONDAY to "M", Calendar.TUESDAY to "T", Calendar.WEDNESDAY to "W", Calendar.THURSDAY to "T",
        Calendar.FRIDAY to "F", Calendar.SATURDAY to "S", Calendar.SUNDAY to "S"
    )
    private val NAMES = mapOf(
        Calendar.MONDAY to "Monday", Calendar.TUESDAY to "Tuesday", Calendar.WEDNESDAY to "Wednesday",
        Calendar.THURSDAY to "Thursday", Calendar.FRIDAY to "Friday", Calendar.SATURDAY to "Saturday", Calendar.SUNDAY to "Sunday"
    )

    //inicio de cada uno de los ultimos 7 dias (hace 6 dias ... hoy) y el inicio de manana
    //se usa Calendar para respetar la zona horaria y los cambios de hora
    fun dayStarts(now: Long, zone: TimeZone): List<Long> {
        val calendar = startOfDay(now, zone)
        calendar.add(Calendar.DAY_OF_MONTH, -(DAYS - 1))
        val starts = mutableListOf<Long>()
        repeat(DAYS + 1) {
            starts.add(calendar.timeInMillis)
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        return starts
    }

    //la tira de 7 dias con el estado de cada uno; el ultimo es hoy
    fun build(photos: List<PhotoEntryModel>, now: Long, zone: TimeZone): List<PhotoDay> {
        val starts = dayStarts(now, zone)
        return (0 until DAYS).map { index ->
            val from = starts[index]
            val until = starts[index + 1]
            val hasPhoto = photos.any { it.takenAt >= from && it.takenAt < until }
            val state = when {
                hasPhoto -> PhotoDayState.TAKEN
                index == DAYS - 1 -> PhotoDayState.TODAY
                else -> PhotoDayState.MISSED
            }
            val dayOfWeek = Calendar.getInstance(zone).apply { timeInMillis = from }.get(Calendar.DAY_OF_WEEK)
            PhotoDay(SHORT_LABELS.getValue(dayOfWeek), NAMES.getValue(dayOfWeek), state)
        }
    }

    //la foto de hoy, si ya hay; si hay varias, la mas reciente
    fun todayPhoto(photos: List<PhotoEntryModel>, now: Long, zone: TimeZone): PhotoEntryModel? {
        val starts = dayStarts(now, zone)
        return photos
            .filter { it.takenAt >= starts[DAYS - 1] && it.takenAt < starts[DAYS] }
            .maxByOrNull { it.takenAt }
    }

    private fun startOfDay(now: Long, zone: TimeZone): Calendar = Calendar.getInstance(zone).apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}
