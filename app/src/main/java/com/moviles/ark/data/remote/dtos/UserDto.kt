package com.moviles.ark.data.remote.dtos

import com.moviles.ark.domain.models.UserModel
import com.moviles.ark.domain.models.UserProfile

//dto del documento users/{uid} en firestore
//guarda name, birthDate, age (calculada) y email para compatibilidad con kotlin, flutter y bigquery
//la clave nunca viaja: se queda en firebase auth
data class UserDto(
    val name: String = "",
    val birthDate: String = "",
    val age: Int = 0,
    val email: String = ""
)

//de modelo de registro a dto, calculando la edad a partir de la fecha de nacimiento
fun UserModel.toDto(): UserDto {
    return UserDto(
        name = name.trim(),
        birthDate = birthDate.trim(),
        age = getCalculatedAge(),
        email = email.trim()
    )
}

//de dto a datos del perfil; la fecha de creacion la entrega firebase auth, no firestore
fun UserDto.toProfile(memberSince: Long): UserProfile {
    return UserProfile(
        name = name,
        email = email,
        memberSince = memberSince,
        birthDate = birthDate,
        age = age
    )
}

//campos que se escriben al editar el perfil: nombre y la fecha (o la edad, en perfiles viejos que solo tienen edad)
//la edad solo va si se conoce, para no borrar la que ya estaba en firestore
fun UserDto.editableFields(): List<String> {
    val fields = mutableListOf("name")
    if (birthDate.isNotBlank()) fields.add("birthDate")
    if (age > 0) fields.add("age")
    return fields
}
