package com.moviles.ark.data.remote.dtos

import com.moviles.ark.domain.models.UserModel
import com.moviles.ark.domain.models.UserProfile

//dto del documento users/{uid} en firestore
//las reglas de firestore aceptan name, email, birthDate y opcionalmente age por compatibilidad con flutter
//la clave nunca viaja: se queda en firebase auth
data class UserDto(
    val name: String = "",
    val birthDate: String = "",
    val email: String = ""
)

//de modelo de registro a dto, sin la clave
fun UserModel.toDto(): UserDto {
    return UserDto(
        name = name.trim(),
        birthDate = birthDate.trim(),
        email = email.trim()
    )
}

//de dto a datos del perfil; la fecha de creacion la entrega firebase auth, no firestore
fun UserDto.toProfile(memberSince: Long): UserProfile {
    return UserProfile(
        name = name,
        email = email,
        memberSince = memberSince,
        birthDate = birthDate
    )
}
