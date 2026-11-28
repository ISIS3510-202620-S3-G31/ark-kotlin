package com.moviles.ark.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.moviles.ark.domain.models.FortuneCardModel

//tabla de imagenes guardadas, solo una imagen por frase y por usuario
//localImageUri es la imagen en la galeria y remoteImageUrl la copia en firebase storage
@Entity(tableName = "saved_fortune_cards", indices = [Index(value = ["userId", "cookieId"], unique = true)])
data class SavedFortuneCardEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val cookieId: Int, val userId: String, val quote: String, val localImageUri: String, val remoteImageUrl: String? = null, val savedAt: Long, val isSynced: Boolean = false) {
    //de fila de room a modelo, si no hay imagen local se usa la de la nube
    fun toModel(): FortuneCardModel = FortuneCardModel(id = id, cookieId = cookieId, quote = quote, imageUri = localImageUri.ifEmpty { remoteImageUrl ?: "" }, savedAt = savedAt, isBackedUp = isSynced, remoteImageUrl = remoteImageUrl)
}
