package com.moviles.ark.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moviles.ark.domain.models.Avatar
import com.moviles.ark.domain.models.UserProfile

//tabla del perfil del usuario guardado en el telefono
//asi el perfil se ve al instante y sin internet; firestore (users/{uid}) se actualiza cuando hay conexion
//una fila por cuenta que haya iniciado sesion en este telefono
@Entity(tableName = "users")
data class UserEntity(
    //uid de firebase auth
    @PrimaryKey
    val uid: String,
    val name: String,
    val email: String,
    //fecha de nacimiento en formato YYYY-MM-DD
    val birthDate: String = "",
    //0 si todavia no se conoce o valor de respaldo
    val age: Int = 0,
    //dias seguidos con check-in la ultima vez que se calculo la racha
    val streakDays: Int = 0,
    //fecha en milisegundos del ultimo check-in contado en la racha; null si no hay
    val lastCheckInAt: Long? = null,
    //fecha de creacion de la cuenta en milisegundos (la entrega firebase auth)
    val createdAt: Long = 0L,
    //ruta de la foto de perfil dentro del almacenamiento privado de la app
    val avatarPath: String? = null,
    //false si el usuario cambio su perfil y todavia no se subio a firestore
    val isSynced: Boolean = true,
    //avatar de la lista que eligio la cuenta
    val avatarId: String = Avatar.DEFAULT_ID,
    //true si eligio mostrar su foto en vez del avatar
    val usesPhoto: Boolean = false,
    //url en storage de la foto que esta en avatarPath; null si esa foto todavia no se ha subido
    val photoUrl: String? = null,
    //false si cambio el avatar o la foto y todavia no se subio a firestore y storage
    val isAvatarSynced: Boolean = true,
    //cuando edito nombre o fecha en este telefono (milisegundos): marca cual edicion es la que se subio
    val profileEditedAt: Long = 0L,
    //lo mismo para el avatar o la foto de perfil
    val avatarEditedAt: Long = 0L,
    //nombre, fecha y edad que este telefono vio en la nube la ultima vez; al subir un cambio pendiente se compara
    //con la nube: si ya no coincide, otro telefono lo cambio mientras este estaba sin internet y gana ese cambio
    val profileBase: String? = null,
    //lo mismo para el avatar o la foto de perfil (avatarId, avatarSource y la foto)
    val avatarBase: String? = null,
)

//de fila de la tabla a modelo de dominio
fun UserEntity.toDomain(): UserProfile {
    return UserProfile(
        name = name,
        email = email,
        memberSince = createdAt,
        birthDate = birthDate,
        age = age,
        streakDays = streakDays,
        lastCheckInAt = lastCheckInAt,
        avatarPath = avatarPath,
        avatarId = avatarId,
        usesPhoto = usesPhoto,
    )
}
