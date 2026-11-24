package com.moviles.ark.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

//aqui se usa room: tabla cookies con los datos de cada galleta del cookie jar
//la imagen no va en la base: es un archivo privado (CookiePrivateImageStore) y aqui solo se guarda su ruta
//el indice unico (userId, titleKey) es la ultima defensa contra titulos repetidos del mismo usuario
@Entity(
    tableName = "cookies",
    indices = [Index(value = ["userId", "titleKey"], unique = true)]
)
data class CookieEntity(
    //uuid generado en el telefono: el mismo en room, en el archivo privado y en la nube
    @PrimaryKey
    val id: String,
    //uid de firebase del dueno, como en photo_entries
    val userId: String,
    val title: String,
    //titulo normalizado (minusculas, sin tildes, recortado) para comparar repetidos
    val titleKey: String,
    val text: String,
    //ACTIVE o EATEN
    val state: String,
    //distingue "no tiene imagen" de "la imagen esta en la nube pero no en el telefono"
    val hasImage: Boolean,
    //ruta del jpg en filesDir/cookies; null si no tiene o si se desalojo
    val localImagePath: String?,
    //url en firebase storage; null hasta subirla
    val remoteImageUrl: String?,
    //ultima vez que se abrio, para el desalojo lru de imagenes
    val lastOpenedAt: Long,
    //username de quien la regalo; null si es propia
    val receivedFrom: String?,
    val createdAt: Long,
    val updatedAt: Long,
    //false mientras haya algo por subir
    val isSynced: Boolean,
)

//consulta liviana del listado: sin el texto completo
data class CookieListRow(
    val id: String,
    val title: String,
    val state: String,
    val hasImage: Boolean,
    val receivedFrom: String?,
    val isSynced: Boolean,
)
