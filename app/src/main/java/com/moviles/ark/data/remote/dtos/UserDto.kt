package com.moviles.ark.data.remote.dtos

import com.moviles.ark.domain.models.UserModel
import com.moviles.ark.domain.models.UserProfile

//dto del documento users/{uid} en firestore (#120)
//las reglas de firestore solo aceptan estos tres campos (isValidUser): no agregar mas sin cambiar las reglas
//la clave nunca viaja: se queda en firebase auth
data class UserDto(
    val name: String = "",
    val age: Int = 0,
    val email: String = ""
)

//de modelo de registro a dto, sin la clave
fun UserModel.toDto(): UserDto {
    return UserDto(
        name = name.trim(),
        age = age,
        email = email.trim()
    )
}

//de dto a datos del perfil; la fecha de creacion la entrega firebase auth, no firestore
fun UserDto.toProfile(memberSince: Long): UserProfile {
    return UserProfile(
        name = name,
        email = email,
        memberSince = memberSince
    )
}
