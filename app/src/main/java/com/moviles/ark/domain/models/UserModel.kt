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

    //el error de cada campo al tocar "Sign Up"; la cuenta solo se crea si todos son null
    fun registrationErrors(confirmPassword: String) = RegistrationErrors(
        name = nameError(name),
        birthDate = birthDateError(birthDate),
        email = emailError(email),
        password = passwordError(password),
        confirmPassword = confirmPasswordError(password, confirmPassword)
    )

    companion object {
        const val MIN_NAME_LENGTH = 2
        //el mismo limite en el registro y en el perfil; firestore acepta hasta 100, asi que cabe
        const val MAX_NAME_LENGTH = 50
        const val MIN_AGE = 13
        const val MAX_AGE = 120
        const val MIN_PASSWORD_LENGTH = 8
        //largo del texto de una fecha YYYY-MM-DD
        const val BIRTH_DATE_LENGTH = 10
        //forma de esa fecha caracter por caracter: d es un digito y el guion se escribe igual
        private const val BIRTH_DATE_PATTERN = "dddd-dd-dd"
        private val DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        private val NAME_REGEX = Regex("^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ ]+$")
        val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        const val ALL_FIELDS_REQUIRED = "All fields are required"
        const val INVALID_EMAIL_ERROR = "Email needs an @ and a domain, like name@example.com"
        const val EMAIL_REQUIRED_ERROR = "Enter your email"
        const val EMAIL_ALREADY_REGISTERED_ERROR = "This email is already registered"
        const val NO_INTERNET_ERROR = "No internet connection. Ark needs internet to create your account."
        const val AIRPLANE_MODE_ERROR = "Airplane mode is on. Ark needs internet to create your account."

        //sin internet no se puede crear la cuenta; el mensaje no da detalles tecnicos
        fun noInternetError(isAirplaneMode: Boolean): String = if (isAirplaneMode) AIRPLANE_MODE_ERROR else NO_INTERNET_ERROR

        //el nombre debe tener entre 2 y 50 caracteres y solo letras y espacios
        //los caracteres se revisan primero: con "5" el problema es el numero, no que sea corto
        fun nameError(name: String): String? {
            val trimmed = name.trim()
            return when {
                trimmed.isEmpty() -> "Enter your name"
                !trimmed.matches(NAME_REGEX) -> "Name can only contain letters and spaces, no numbers or symbols"
                trimmed.length < MIN_NAME_LENGTH -> "Name must be at least $MIN_NAME_LENGTH characters"
                trimmed.length > MAX_NAME_LENGTH -> "Name cannot exceed $MAX_NAME_LENGTH characters"
                else -> null
            }
        }

        //cada fecha invalida dice su razon: vacia, mal escrita, inexistente, futura, muy antigua o menor de 13
        fun birthDateError(birthDate: String): String? {
            val trimmed = birthDate.trim()
            if (trimmed.isEmpty()) return "Enter your date of birth"
            if (!trimmed.matches(DATE_REGEX)) return "Write the date as YYYY-MM-DD, for example 2005-03-14"
            val birthCalendar = calendarOf(trimmed) ?: return "That date does not exist, check the day and month"
            val age = ageBetween(birthCalendar, Calendar.getInstance())
            return when {
                age == null -> "Date of birth cannot be in the future"
                age > MAX_AGE -> "That date is more than $MAX_AGE years ago, check the year"
                age < MIN_AGE -> "You must be at least $MIN_AGE years old to use Ark"
                else -> null
            }
        }

        //estructura estandar de correo (una @ y un dominio con punto) y sin puntos dobles
        fun emailError(email: String): String? {
            val trimmed = email.trim()
            return when {
                trimmed.isEmpty() -> EMAIL_REQUIRED_ERROR
                !trimmed.matches(EMAIL_REGEX) || trimmed.contains("..") -> INVALID_EMAIL_ERROR
                else -> null
            }
        }

        //minimo 8 caracteres, al menos una mayuscula y al menos un numero
        fun passwordError(password: String): String? {
            val strong = password.length >= MIN_PASSWORD_LENGTH && password.any { it.isUpperCase() } && password.any { it.isDigit() }
            return when {
                password.isEmpty() -> "Enter a password"
                !strong -> "Must be at least $MIN_PASSWORD_LENGTH chars, 1 uppercase and 1 number"
                else -> null
            }
        }

        fun confirmPasswordError(password: String, confirmPassword: String): String? = when {
            confirmPassword.isEmpty() -> "Confirm your password"
            password != confirmPassword -> "Passwords do not match"
            else -> null
        }

        //mientras se escribe, un campo vacio no marca error (aun no lo han llenado);
        //con algo escrito se aplica la regla completa desde la primera letra
        fun nameErrorWhileTyping(name: String): String? = if (name.isBlank()) null else nameError(name)

        //el campo del nombre no deja escribir mas de 50 caracteres (tambien si se pega un texto largo)
        fun limitName(name: String): String = name.take(MAX_NAME_LENGTH)

        //la fecha a medio escribir no marca error si va bien ("2005-0"); si ya no puede ser YYYY-MM-DD, si
        fun birthDateErrorWhileTyping(birthDate: String): String? {
            val trimmed = birthDate.trim()
            val incompleteButFine = trimmed.length < BIRTH_DATE_LENGTH && trimmed.withIndex().all { (index, char) ->
                if (BIRTH_DATE_PATTERN[index] == 'd') char.isDigit() else char == BIRTH_DATE_PATTERN[index]
            }
            return if (trimmed.isEmpty() || incompleteButFine) null else birthDateError(trimmed)
        }

        fun emailErrorWhileTyping(email: String): String? = if (email.isBlank()) null else emailError(email)

        fun passwordErrorWhileTyping(password: String): String? = if (password.isEmpty()) null else passwordError(password)

        fun confirmPasswordErrorWhileTyping(password: String, confirmPassword: String): String? =
            if (confirmPassword.isEmpty()) null else confirmPasswordError(password, confirmPassword)

        //solo numeros y guiones y maximo 10 caracteres: lo que cabe en YYYY-MM-DD
        fun cleanBirthDateInput(input: String): String =
            input.filter { it.isDigit() || it == '-' }.take(BIRTH_DATE_LENGTH)

        //edad exacta en anos a partir de YYYY-MM-DD; null si la fecha no es real o es futura
        fun ageOf(birthDate: String): Int? {
            if (!birthDate.matches(DATE_REGEX)) return null
            val birthCalendar = calendarOf(birthDate) ?: return null
            return ageBetween(birthCalendar, Calendar.getInstance())
        }

        //fecha real del calendario o null; isLenient = false rechaza dias como el 30 de febrero
        private fun calendarOf(birthDate: String): Calendar? {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
            val date = runCatching { format.parse(birthDate) }.getOrNull() ?: return null
            return Calendar.getInstance().apply { time = date }
        }

        //anos cumplidos a hoy; se compara mes y dia (no el dia del ano) para que los bisiestos no resten un ano
        private fun ageBetween(birthCalendar: Calendar, today: Calendar): Int? {
            if (birthCalendar.after(today)) return null
            var age = today.get(Calendar.YEAR) - birthCalendar.get(Calendar.YEAR)
            val birthdayNotYet = today.get(Calendar.MONTH) < birthCalendar.get(Calendar.MONTH) ||
                (today.get(Calendar.MONTH) == birthCalendar.get(Calendar.MONTH) &&
                    today.get(Calendar.DAY_OF_MONTH) < birthCalendar.get(Calendar.DAY_OF_MONTH))
            if (birthdayNotYet) age--
            return age
        }
    }
}

//errores del formulario de registro campo por campo; null en un campo significa que cumple
data class RegistrationErrors(
    val name: String? = null,
    val birthDate: String? = null,
    val email: String? = null,
    val password: String? = null,
    val confirmPassword: String? = null
) {
    fun hasErrors(): Boolean = listOf(name, birthDate, email, password, confirmPassword).any { it != null }

    //mensaje junto al boton que nombra los campos a corregir, por si alguno quedo fuera de la pantalla
    fun summary(): String? {
        val fields = listOfNotNull(
            name?.let { "name" },
            birthDate?.let { "date of birth" },
            email?.let { "email" },
            password?.let { "password" },
            confirmPassword?.let { "confirm password" }
        )
        return if (fields.isEmpty()) null else "Please fix the fields in red: ${fields.joinToString(", ")}"
    }
}
