package com.moviles.ark.data.remote.dtos

import com.google.firebase.firestore.IgnoreExtraProperties
import com.moviles.ark.domain.models.UserModel
import com.moviles.ark.domain.models.UserProfile

//dto del documento users/{uid} en firestore
//guarda name, birthDate, age (calculada) y email para compatibilidad con kotlin, flutter y bigquery
//la clave nunca viaja: se queda en firebase auth
//los campos de la foto de perfil van en UserAvatarDto; firestore no avisa por ellos al leer este dto
@IgnoreExtraProperties
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

//campos de la foto de perfil en el mismo documento users/{uid}; van aparte de UserDto para que el registro
//no los escriba vacios (las reglas solo aceptan "preset" o "photo" en avatarSource)
//flutter lee el documento con sus propios campos y no se ve afectado por estos
@IgnoreExtraProperties
data class UserAvatarDto(
    //avatar de la lista: avatar_1 ... avatar_6
    val avatarId: String = "",
    //"photo" si el usuario eligio mostrar su foto, "preset" si eligio un avatar; vacio si nunca se ha subido
    val avatarSource: String = "",
    //url de descarga de la foto en storage (users/{uid}/avatar/...); vacia si no tiene foto subida
    val photoUrl: String = ""
) {
    fun usesPhoto(): Boolean = avatarSource == SOURCE_PHOTO

    //campos a escribir: la url solo va si hay foto, asi elegir un avatar no borra la foto de la nube
    fun fields(): List<String> {
        val fields = mutableListOf("avatarId", "avatarSource")
        if (photoUrl.isNotBlank()) fields.add("photoUrl")
        return fields
    }

    companion object {
        const val SOURCE_PRESET = "preset"
        const val SOURCE_PHOTO = "photo"
    }
}
