package com.moviles.ark.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moviles.ark.domain.models.UserProfile

//tabla del perfil del usuario guardado en el telefono (#119)
//asi el perfil se ve al instante y sin internet; firestore (users/{uid}) se actualiza cuando hay conexion
//una fila por cuenta que haya iniciado sesion en este telefono
@Entity(tableName = "users")
data class UserEntity(
    //uid de firebase auth
    @PrimaryKey
    val uid: String,
    val name: String,
    val email: String,
    //0 si todavia no se conoce
    val age: Int = 0,
    //dias seguidos con check-in la ultima vez que se calculo la racha
    val streakDays: Int = 0,
    //fecha en milisegundos del ultimo check-in contado en la racha; null si no hay
    val lastCheckInAt: Long? = null,
    //fecha de creacion de la cuenta en milisegundos (la entrega firebase auth)
    val createdAt: Long = 0L,
    //ruta del avatar dentro del almacenamiento privado de la app; solo vive en el telefono
    val avatarPath: String? = null,
    //false si el usuario cambio su nombre o edad y todavia no se subio a firestore
    val isSynced: Boolean = true,
)

//de fila de la tabla a modelo de dominio
fun UserEntity.toDomain(): UserProfile {
    return UserProfile(
        name = name,
        email = email,
        memberSince = createdAt,
        age = age,
        streakDays = streakDays,
        lastCheckInAt = lastCheckInAt,
        avatarPath = avatarPath,
    )
}
