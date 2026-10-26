package com.moviles.ark.domain.models

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

//clase del modelo que representa al usuario y sus reglas de negocio
data class UserModel(
    val name: String,
    val birthDate: String,
    val email: String,
    val password: String
) {
    //reglas de negocio
    //el nombre debe tener entre 2 y 100 caracteres segun el estandar w3c y firestore
    fun isValidName(): Boolean {
        val trimmed = name.trim()
        return trimmed.length in MIN_NAME_LENGTH..MAX_NAME_LENGTH
    }

    //valida que la fecha tenga formato yyyy-mm-dd, sea una fecha real, no sea en el futuro y la persona tenga al menos 13 anos
    fun isValidBirthDate(): Boolean {
        if (!birthDate.matches(DATE_REGEX)) return false
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
        val date = runCatching { format.parse(birthDate) }.getOrNull() ?: return false

        val birthCalendar = Calendar.getInstance().apply { time = date }
        val today = Calendar.getInstance()

        //no puede ser una fecha en el futuro
        if (birthCalendar.after(today)) return false

        //calcular la edad exacta para asegurar cumplimiento legal y de bienestar
        var calculatedAge = today.get(Calendar.YEAR) - birthCalendar.get(Calendar.YEAR)
        if (today.get(Calendar.DAY_OF_YEAR) < birthCalendar.get(Calendar.DAY_OF_YEAR)) {
            calculatedAge--
        }

        return calculatedAge in MIN_AGE..MAX_AGE
    }

    //calcula la edad en anos a partir de la fecha de nacimiento para uso en la interfaz o analisis
    fun getCalculatedAge(): Int {
        if (!birthDate.matches(DATE_REGEX)) return 0
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
        val date = runCatching { format.parse(birthDate) }.getOrNull() ?: return 0
        val birthCalendar = Calendar.getInstance().apply { time = date }
        val today = Calendar.getInstance()
        var calculatedAge = today.get(Calendar.YEAR) - birthCalendar.get(Calendar.YEAR)
        if (today.get(Calendar.DAY_OF_YEAR) < birthCalendar.get(Calendar.DAY_OF_YEAR)) {
            calculatedAge--
        }
        return if (calculatedAge >= 0) calculatedAge else 0
    }

    //valida que el correo tenga estructura estandar y no tenga puntos dobles
    fun isValidEmail(): Boolean {
        val trimmed = email.trim()
        val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        return trimmed.matches(emailRegex) && !trimmed.contains("..")
    }

    //valida longitud minima de 8 caracteres, al menos una mayuscula y al menos un numero
    fun isValidPassword(): Boolean {
        if (password.length < 8) {
            return false
        }
        var hasUppercase = false
        var hasNumber = false
        for (caracter in password) {
            if (caracter.isUpperCase()) {
                hasUppercase = true
            }
            if (caracter.isDigit()) {
                hasNumber = true
            }
        }
        return hasUppercase && hasNumber
    }

    companion object {
        const val MIN_NAME_LENGTH = 2
        const val MAX_NAME_LENGTH = 100
        const val MIN_AGE = 13
        const val MAX_AGE = 120
        private val DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    }
}
