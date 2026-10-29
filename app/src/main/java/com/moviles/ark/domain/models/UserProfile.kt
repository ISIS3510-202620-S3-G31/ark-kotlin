package com.moviles.ark.domain.models

import java.util.TimeZone

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
    fun isValidName(): Boolean = UserModel.nameError(name) == null

    fun isValidBirthDate(): Boolean = UserModel.birthDateError(birthDate) == null

    fun isValidAge(): Boolean {
        return age in MIN_AGE..MAX_AGE
    }

    //el error al guardar la edicion del perfil, o null si se puede guardar
    //el del nombre es el mismo del registro: dice si es muy corto, muy largo o si trae numeros o simbolos
    fun editError(): String? = UserModel.nameError(name) ?: when {
        !isValidBirthDate() -> "Enter a valid birth date (YYYY-MM-DD); you need to be $MIN_AGE to $MAX_AGE years old"
        else -> null
    }

    //calcula la edad a partir de la fecha de nacimiento; si no hay fecha, usa la edad guardada
    fun getCalculatedAge(): Int = UserModel.ageOf(birthDate) ?: age

    //si no hay nombre (sin internet la primera vez), se usa la parte del correo antes de la @
    fun displayName(): String = name.ifBlank { email.substringBefore("@") }

    //primera letra de las dos primeras palabras: "David Forero" -> "DF", "lau3" -> "L"
    fun initials(): String {
        return displayName().trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
    }

    //dias de calendario con ark en la hora local: registrarse hoy da 1, manana da 2
    //se suma el desfase de la zona horaria para que el dia cambie a medianoche local y no de londres
    fun daysWithArk(now: Long, zone: TimeZone = TimeZone.getDefault()): Int {
        if (memberSince <= 0L) return 0
        val today = (now + zone.getOffset(now)) / DAY_MILLIS
        val firstDay = (memberSince + zone.getOffset(memberSince)) / DAY_MILLIS
        return (today - firstDay + 1).toInt().coerceAtLeast(1)
    }

    companion object {
        const val MIN_NAME_LENGTH = UserModel.MIN_NAME_LENGTH
        const val MAX_NAME_LENGTH = UserModel.MAX_NAME_LENGTH
        const val MIN_AGE = UserModel.MIN_AGE
        const val MAX_AGE = UserModel.MAX_AGE
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}
