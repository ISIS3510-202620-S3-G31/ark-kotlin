package com.moviles.ark.domain.models

//datos del usuario que se muestran en el perfil
//es distinto de User: User sirve para registrarse y trae la clave, este nunca la tiene
data class UserProfile(
    val name: String,
    val email: String,
    //fecha de creacion de la cuenta en milisegundos (asi la entrega firebase auth)
    val memberSince: Long
)
