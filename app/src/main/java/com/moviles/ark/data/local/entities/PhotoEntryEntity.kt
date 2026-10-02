package com.moviles.ark.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

//tabla de fotos del dia (#26): la foto (jpg) vive en context.filesDir y aqui solo se guarda su ruta
@Entity(tableName = "photo_entries")
data class PhotoEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    //ruta del archivo dentro del almacenamiento privado de la app
    val localFilePath: String,
    val caption: String = "",
    //cuando se tomo, en milisegundos
    val takenAt: Long,
    //url en firebase storage; null hasta que se suba
    val remoteUrl: String? = null,
    //false mientras no se haya subido
    val isSynced: Boolean = false,
)
