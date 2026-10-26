package com.moviles.ark.domain.models

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

//datos del usuario que se muestran en el perfil
//es distinto de UserModel: UserModel sirve para registrarse y trae la clave, este nunca la tiene
//el repositorio lo arma desde room (UserEntity), asi se ve sin internet
data class UserProfile(
    val name: String,
    val email: String,
    //fecha de creacion de la cuenta en milisegundos (asi la entrega firebase auth)
    val memberSince: Long,
    //fecha de nacimiento en formato YYYY-MM-DD
    val birthDate: String = "",
    //0 si todavia no se conoce o valor de respaldo por compatibilidad
    val age: Int = 0,
    //racha guardada la ultima vez que se calculo; CheckInStreak.stillAlive dice si sigue valiendo hoy
    val streakDays: Int = 0,
    //fecha en milisegundos del ultimo check-in contado en la racha
    val lastCheckInAt: Long? = null,
    //ruta del avatar en el telefono; null si el usuario no ha elegido foto
    val avatarPath: String? = null
) {
    //reglas de negocio para editar el perfil: las mismas del registro (UserModel)
    fun isValidName(): Boolean {
        val trimmed = name.trim()
        return trimmed.length in MIN_NAME_LENGTH..MAX_NAME_LENGTH && trimmed.matches(NAME_REGEX)
    }

    fun isValidBirthDate(): Boolean {
        if (!birthDate.matches(DATE_REGEX)) return false
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
        val date = runCatching { format.parse(birthDate) }.getOrNull() ?: return false
        val birthCalendar = Calendar.getInstance().apply { time = date }
        val today = Calendar.getInstance()
        if (birthCalendar.after(today)) return false
        var calculatedAge = today.get(Calendar.YEAR) - birthCalendar.get(Calendar.YEAR)
        if (today.get(Calendar.DAY_OF_YEAR) < birthCalendar.get(Calendar.DAY_OF_YEAR)) {
            calculatedAge--
        }
        return calculatedAge in MIN_AGE..MAX_AGE
    }

    fun isValidAge(): Boolean {
        return age in MIN_AGE..MAX_AGE
    }

    //calcula la edad a partir de la fecha de nacimiento; si no hay fecha, usa la edad guardada
    fun getCalculatedAge(): Int {
        if (birthDate.matches(DATE_REGEX)) {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
            val date = runCatching { format.parse(birthDate) }.getOrNull()
            if (date != null) {
                val birthCalendar = Calendar.getInstance().apply { time = date }
                val today = Calendar.getInstance()
                var calculated = today.get(Calendar.YEAR) - birthCalendar.get(Calendar.YEAR)
                if (today.get(Calendar.DAY_OF_YEAR) < birthCalendar.get(Calendar.DAY_OF_YEAR)) {
                    calculated--
                }
                if (calculated >= 0) return calculated
            }
        }
        return age
    }

    companion object {
        const val MIN_NAME_LENGTH = 3
        const val MAX_NAME_LENGTH = 40
        const val MIN_AGE = 13
        const val MAX_AGE = 100
        private val DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        private val NAME_REGEX = Regex("^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ ]+$")
    }
}
