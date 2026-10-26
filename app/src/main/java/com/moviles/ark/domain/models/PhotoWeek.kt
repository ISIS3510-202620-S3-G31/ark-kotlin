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

//un dia de la tira: "M" arriba y el nombre completo para lectores de pantalla (#152)
data class PhotoDay(
    val shortLabel: String,
    val name: String,
    val state: PhotoDayState,
    val dateMillis: Long = 0L,
    val photo: PhotoEntryModel? = null,
    val isSelected: Boolean = false
)

//reglas de la semana de fotos: muestra los dias anteriores terminando en hoy como ultimo dia (#152)
object PhotoWeek {

    //inicio de los 6 dias previos y hoy (7 dias en total) mas el fin de hoy
    //se usa Calendar para respetar la zona horaria y los cambios de hora
    fun dayStarts(now: Long, zone: TimeZone): List<Long> {
        val calendar = Calendar.getInstance(zone)
        calendar.timeInMillis = now
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        //retrocede 6 dias para que hoy sea el ultimo (septimo) dia de la tira (#152)
        calendar.add(Calendar.DAY_OF_MONTH, -6)
        val starts = mutableListOf<Long>()
        repeat(8) {
            starts.add(calendar.timeInMillis)
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        return starts
    }

    //la tira de 7 dias terminando en hoy, mostrando dias anteriores y nunca futuros (#152)
    fun build(
        photos: List<PhotoEntryModel>,
        now: Long,
        zone: TimeZone,
        selectedDateMillis: Long? = null
    ): List<PhotoDay> {
        val starts = dayStarts(now, zone)
        return (0 until 7).map { index ->
            val from = starts[index]
            val until = starts[index + 1]
            val dayPhotos = photos.filter { it.takenAt >= from && it.takenAt < until }
            val photo = dayPhotos.maxByOrNull { it.takenAt }
            val isToday = (index == 6)
            val state = when {
                photo != null -> PhotoDayState.TAKEN
                isToday -> PhotoDayState.TODAY
                else -> PhotoDayState.MISSED
            }
            val (shortLabel, name) = dayLabelAndName(from, zone)
            val isSelected = if (selectedDateMillis != null) {
                from == selectedDateMillis
            } else {
                isToday
            }
            PhotoDay(
                shortLabel = shortLabel,
                name = name,
                state = state,
                dateMillis = from,
                photo = photo,
                isSelected = isSelected
            )
        }
    }

    //obtiene la letra corta y el nombre del dia segun la fecha
    private fun dayLabelAndName(dateMillis: Long, zone: TimeZone): Pair<String, String> {
        val calendar = Calendar.getInstance(zone)
        calendar.timeInMillis = dateMillis
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "M" to "Monday"
            Calendar.TUESDAY -> "T" to "Tuesday"
            Calendar.WEDNESDAY -> "W" to "Wednesday"
            Calendar.THURSDAY -> "T" to "Thursday"
            Calendar.FRIDAY -> "F" to "Friday"
            Calendar.SATURDAY -> "S" to "Saturday"
            Calendar.SUNDAY -> "S" to "Sunday"
            else -> "?" to ""
        }
    }

    //la foto de hoy, si ya hay; si hay varias, la mas reciente
    fun todayPhoto(photos: List<PhotoEntryModel>, now: Long, zone: TimeZone): PhotoEntryModel? {
        val starts = dayStarts(now, zone)
        val todayStart = starts[6]
        val todayEnd = starts[7]
        return photos
            .filter { it.takenAt >= todayStart && it.takenAt < todayEnd }
            .maxByOrNull { it.takenAt }
    }
}
