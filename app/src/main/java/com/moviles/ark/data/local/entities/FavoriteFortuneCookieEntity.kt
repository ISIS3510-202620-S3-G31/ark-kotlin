package com.moviles.ark.data.local.entities

import androidx.room.Entity
import com.moviles.ark.domain.models.FortuneModel

//tabla de frases favoritas, cada usuario tiene las suyas
//isSynced es false mientras falte subir el cambio a firestore
//isDeleted es true si se quito pero falta borrarla de firestore, la app ya no la muestra
@Entity(tableName = "favorite_fortune_cookies", primaryKeys = ["userId", "id"])
data class FavoriteFortuneCookieEntity(val userId: String, val id: Int, val quote: String, val category: String, val savedAt: Long, val isSynced: Boolean = false, val isDeleted: Boolean = false) {
    //de fila de room a modelo
    fun toModel(): FortuneModel = FortuneModel(id = id, quote = quote, category = category, isFavorite = true, savedAt = savedAt)
}
