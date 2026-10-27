package com.moviles.ark.domain.models

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

//clase del modelo que representa al usuario y sus reglas de negocio
//las reglas de cada campo estan en el companion para que el registro y el perfil las usen igual;
//cada una devuelve el mensaje de error o null si el valor cumple
data class UserModel(
    val name: String,
    val birthDate: String,
    val email: String,
    val password: String
) {
    //reglas de negocio
    fun isValidName(): Boolean = nameError(name) == null

    fun isValidBirthDate(): Boolean = birthDateError(birthDate) == null

    //calcula la edad en anos a partir de la fecha de nacimiento para uso en la interfaz o analisis
    fun getCalculatedAge(): Int = ageOf(birthDate) ?: 0

    fun isValidEmail(): Boolean = emailError(email) == null

    fun isValidPassword(): Boolean = passwordError(password) == null

    //el primer error del formulario de registro, o null si se puede crear la cuenta
    fun registrationError(confirmPassword: String): String? = when {
        name.isBlank() || birthDate.isBlank() || email.isBlank() || password.isBlank() || confirmPassword.isBlank() -> MISSING_FIELDS_ERROR
        confirmPasswordError(password, confirmPassword) != null -> PASSWORDS_DO_NOT_MATCH_ERROR
        !isValidName() || !isValidBirthDate() || !isValidEmail() || !isValidPassword() -> CHECK_FIELDS_ERROR
        else -> null
    }

    companion object {
        const val MIN_NAME_LENGTH = 2
        //las reglas de firestore no dejan guardar nombres de mas de 100 letras
        const val MAX_NAME_LENGTH = 100
        const val MIN_AGE = 13
        const val MAX_AGE = 120
        const val MIN_PASSWORD_LENGTH = 8
        //largo del texto de una fecha YYYY-MM-DD
        const val BIRTH_DATE_LENGTH = 10
        private val DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        private val NAME_REGEX = Regex("^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ ]+$")
        val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        const val MISSING_FIELDS_ERROR = "Please fill in all fields"
        const val CHECK_FIELDS_ERROR = "Please check highlighted errors"
        const val PASSWORDS_DO_NOT_MATCH_ERROR = "Passwords do not match"
        const val INVALID_EMAIL_ERROR = "Please enter a valid email address"

        //el nombre debe tener entre 2 y 100 caracteres y solo letras y espacios
        fun nameError(name: String): String? {
            val trimmed = name.trim()
            return when {
                trimmed.length < MIN_NAME_LENGTH -> "Name must be at least $MIN_NAME_LENGTH characters"
                trimmed.length > MAX_NAME_LENGTH -> "Name cannot exceed $MAX_NAME_LENGTH characters"
                !trimmed.matches(NAME_REGEX) -> "Name can only contain letters and spaces"
                else -> null
            }
        }

        //la fecha debe ser YYYY-MM-DD, real, no futura y la persona debe tener entre 13 y 120 anos
        fun birthDateError(birthDate: String): String? {
            val age = ageOf(birthDate)
            return if (age == null || age !in MIN_AGE..MAX_AGE) {
                "Must be a valid date and at least $MIN_AGE years old (YYYY-MM-DD)"
            } else {
                null
            }
        }

        //estructura estandar de correo y sin puntos dobles
        fun emailError(email: String): String? {
            val trimmed = email.trim()
            return if (trimmed.matches(EMAIL_REGEX) && !trimmed.contains("..")) null else INVALID_EMAIL_ERROR
        }

        //minimo 8 caracteres, al menos una mayuscula y al menos un numero
        fun passwordError(password: String): String? {
            val strong = password.length >= MIN_PASSWORD_LENGTH && password.any { it.isUpperCase() } && password.any { it.isDigit() }
            return if (strong) null else "Must be at least $MIN_PASSWORD_LENGTH chars, 1 uppercase and 1 number"
        }

        fun confirmPasswordError(password: String, confirmPassword: String): String? =
            if (password == confirmPassword) null else PASSWORDS_DO_NOT_MATCH_ERROR

        //mientras se escribe solo se avisa cuando el valor ya tiene la forma completa, para no marcar error a la primera letra
        fun nameErrorWhileTyping(name: String): String? = if (name.isBlank()) null else nameError(name)

        fun birthDateErrorWhileTyping(birthDate: String): String? =
            if (birthDate.length < BIRTH_DATE_LENGTH) null else birthDateError(birthDate)

        fun emailErrorWhileTyping(email: String): String? = when {
            email.isNotBlank() && email.contains("@") && email.contains(".") -> emailError(email)
            email.length >= 6 && !email.contains("@") -> "Email must contain @"
            else -> null
        }

        fun passwordErrorWhileTyping(password: String): String? = if (password.isBlank()) null else passwordError(password)

        fun confirmPasswordErrorWhileTyping(password: String, confirmPassword: String): String? =
            if (confirmPassword.isBlank()) null else confirmPasswordError(password, confirmPassword)

        //solo numeros y guiones y maximo 10 caracteres: lo que cabe en YYYY-MM-DD
        fun cleanBirthDateInput(input: String): String =
            input.filter { it.isDigit() || it == '-' }.take(BIRTH_DATE_LENGTH)

        //edad exacta en anos a partir de YYYY-MM-DD; null si la fecha no es real o es futura
        fun ageOf(birthDate: String): Int? {
            if (!birthDate.matches(DATE_REGEX)) return null
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
            val date = runCatching { format.parse(birthDate) }.getOrNull() ?: return null
            val birthCalendar = Calendar.getInstance().apply { time = date }
            val today = Calendar.getInstance()
            if (birthCalendar.after(today)) return null
            var age = today.get(Calendar.YEAR) - birthCalendar.get(Calendar.YEAR)
            if (today.get(Calendar.DAY_OF_YEAR) < birthCalendar.get(Calendar.DAY_OF_YEAR)) {
                age--
            }
            return age
        }
    }
}
