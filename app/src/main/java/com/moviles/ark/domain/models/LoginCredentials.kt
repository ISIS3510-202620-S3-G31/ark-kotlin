package com.moviles.ark.domain.models

//correo y clave que el usuario escribe para entrar, con las reglas del formulario de login
//la clave solo pide 6 caracteres (el minimo de firebase auth): las cuentas viejas se crearon antes de la regla de 8
data class LoginCredentials(
    val email: String,
    val password: String
) {
    fun isValid(): Boolean = emailError() == null && passwordError() == null

    //errores de cada campo al tocar "Log in"; vacio o mal escrito, cada uno dice que falta
    //el correo usa la misma regla del registro
    fun emailError(): String? = UserModel.emailError(email)

    fun passwordError(): String? = when {
        password.isEmpty() -> "Enter your password"
        password.length < MIN_PASSWORD_LENGTH -> PASSWORD_TOO_SHORT_ERROR
        else -> null
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 6
        private const val PASSWORD_TOO_SHORT_ERROR = "Password must be at least $MIN_PASSWORD_LENGTH characters"
        const val NO_INTERNET_ERROR = "No internet connection. Ark needs internet to sign in."
        const val AIRPLANE_MODE_ERROR = "Airplane mode is on. Ark needs internet to sign in."

        //sin internet no se puede entrar; el mensaje no da detalles tecnicos
        fun noInternetError(isAirplaneMode: Boolean): String = if (isAirplaneMode) AIRPLANE_MODE_ERROR else NO_INTERNET_ERROR

        //mientras se escribe un campo vacio no marca error; con algo escrito se valida desde la primera letra
        fun emailErrorWhileTyping(email: String): String? = UserModel.emailErrorWhileTyping(email)

        fun passwordErrorWhileTyping(password: String): String? =
            if (password.isNotEmpty() && password.length < MIN_PASSWORD_LENGTH) PASSWORD_TOO_SHORT_ERROR else null
    }
}
