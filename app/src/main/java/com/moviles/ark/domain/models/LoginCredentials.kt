package com.moviles.ark.domain.models

//correo y clave que el usuario escribe para entrar, con las reglas del formulario de login
//la clave solo pide 6 caracteres (el minimo de firebase auth): las cuentas viejas se crearon antes de la regla de 8
data class LoginCredentials(
    val email: String,
    val password: String
) {
    fun isValid(): Boolean = email.isNotBlank() && email.matches(UserModel.EMAIL_REGEX) && password.length >= MIN_PASSWORD_LENGTH

    //el primer error al tocar "Log in", o null si se puede intentar entrar
    fun submitError(): String? = when {
        email.isBlank() || password.isBlank() -> UserModel.MISSING_FIELDS_ERROR
        !email.matches(UserModel.EMAIL_REGEX) -> UserModel.CHECK_FIELDS_ERROR
        else -> null
    }

    //error del campo de correo al tocar "Log in"
    fun emailSubmitError(): String? =
        if (email.isBlank() || password.isBlank() || email.matches(UserModel.EMAIL_REGEX)) null else UserModel.INVALID_EMAIL_ERROR

    companion object {
        const val MIN_PASSWORD_LENGTH = 6

        fun emailErrorWhileTyping(email: String): String? = when {
            email.isNotBlank() && email.contains("@") && email.contains(".") && !email.matches(UserModel.EMAIL_REGEX) -> UserModel.INVALID_EMAIL_ERROR
            email.length >= 6 && !email.contains("@") -> "Email must contain @"
            else -> null
        }

        fun passwordErrorWhileTyping(password: String): String? =
            if (password.isNotBlank() && password.length < MIN_PASSWORD_LENGTH) "Password must be at least $MIN_PASSWORD_LENGTH characters" else null
    }
}
