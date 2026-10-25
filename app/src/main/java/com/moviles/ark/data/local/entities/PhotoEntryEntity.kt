package com.moviles.ark.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

//tabla de fotos del dia (#26): la foto (jpg) vive en context.filesDir/photos/{userId}/ (#133) y aqui solo se guarda su ruta
@Entity(tableName = "photo_entries")
data class PhotoEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    //uid de firebase del dueno de la foto (#133); la ui solo lee las del usuario con sesion
    //al agregar esta columna la base subio a la version 4 (ArkDatabase)
    val userId: String,
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
