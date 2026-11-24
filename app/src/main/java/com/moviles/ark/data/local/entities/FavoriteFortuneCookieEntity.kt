package com.moviles.ark.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moviles.ark.domain.models.FortuneModel

//tabla en room para guardar las frases favoritas de la galleta y leerlas sin conexion
@Entity(tableName = "favorite_fortune_cookies")
data class FavoriteFortuneCookieEntity(
    @PrimaryKey val id: Int,
    val quote: String,
    val category: String,
    val luckyNumber: Int,
    val savedAt: Long,
    val isSynced: Boolean = false
) {
    //convierte la entidad de base de datos al modelo del dominio
    fun toDomain(): FortuneModel = FortuneModel(
        id = id,
        quote = quote,
        category = category,
        luckyNumber = luckyNumber,
        isFavorite = true,
        savedAt = savedAt
    )

    companion object {
        //convierte un modelo a entidad de base de datos para guardar en room
        fun fromDomain(model: FortuneModel, isSynced: Boolean = false): FavoriteFortuneCookieEntity = FavoriteFortuneCookieEntity(
            id = model.id,
            quote = model.quote,
            category = model.category,
            luckyNumber = model.luckyNumber,
            savedAt = model.savedAt,
            isSynced = isSynced
        )
    }
}
