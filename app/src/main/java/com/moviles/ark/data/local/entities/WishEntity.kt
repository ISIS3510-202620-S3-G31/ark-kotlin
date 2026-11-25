package com.moviles.ark.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moviles.ark.domain.models.WishModel

//tabla wishes: los deseos del farol de deseos
//room es la fuente de verdad: la app siempre lee y escribe aqui, y el sync sube los cambios a firestore despues
@Entity(tableName = "wishes")
data class WishEntity(
    //uuid creado en el telefono; es el mismo id del documento en firestore
    @PrimaryKey
    val id: String,
    //uid de firebase del dueno; cada usuario solo ve sus deseos
    val userId: String,
    //el texto del deseo
    val text: String,
    //cuando se escribio, en milisegundos
    val createdAt: Long,
    //la ultima vez que cambio, en milisegundos; si el deseo cambio en dos lados gana el mas reciente
    val updatedAt: Long,
    //false mientras haya algo por subir a firestore (deseo nuevo, editado o borrado)
    val isSynced: Boolean,
    //true si el usuario lo borro pero falta borrarlo de firestore; la app ya no lo muestra
    val isDeleted: Boolean
)

//de fila de room a modelo de dominio, que es lo que usan las pantallas
fun WishEntity.toModel(): WishModel {
    return WishModel(
        id = id,
        text = text,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
