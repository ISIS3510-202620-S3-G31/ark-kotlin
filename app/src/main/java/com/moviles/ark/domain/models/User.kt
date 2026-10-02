package com.moviles.ark.domain.models

//clase del modelo que representa al usuario y sus reglas de negocio
data class User(
    val name: String,
    val age: Int,
    val email: String,
    val password: String
) {
    //reglas de negocio
    fun isValidName(): Boolean {
        return name.trim().length >= 2
    }
    fun isValidAge(): Boolean {
        return age in 1..120
    }
    fun isValidEmail(): Boolean {
        return email.contains("@") && email.contains(".") && !email.contains("..")
    }
    fun isValidPassword(): Boolean {
        //revisar longitud minima
        if (password.length < 8) {
            return false
        }
        var hasUppercase = false
        var hasNumber = false
        //recorrer cada caracter para buscar mayuscula y numero
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
}