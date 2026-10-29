package com.moviles.ark.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

//tabla de fotos del dia: la foto (jpg) vive en la galeria (MediaStore, Pictures/Ark/User_<uid>) y aqui solo se guarda su direccion
@Entity(tableName = "photo_entries")
data class PhotoEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    //uid de firebase del dueno de la foto; la ui solo lee las del usuario con sesion
    //al agregar esta columna la base subio a la version 4 (ArkDatabase)
    val userId: String,
    //direccion content:// de la foto en la galeria (o ruta de archivo en fotos viejas)
    val localFilePath: String,
    val caption: String = "",
    //cuando se tomo, en milisegundos
    val takenAt: Long,
    //url en firebase storage; null hasta que se suba
    val remoteUrl: String? = null,
    //false mientras no se haya subido
    val isSynced: Boolean = false,
)
