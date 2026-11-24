package com.moviles.ark.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

//tabla en room para guardar la trazabilidad de las tarjetas exportadas y subidas a la nube
@Entity(tableName = "saved_fortune_cards")
data class SavedFortuneCardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val cookieId: Int,
    val userId: String,
    val quote: String,
    val category: String,
    val luckyNumber: Int,
    val localImageUri: String,
    val remoteImageUrl: String? = null,
    val savedAt: Long,
    val isSynced: Boolean = false
)
