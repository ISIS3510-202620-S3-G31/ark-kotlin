package com.moviles.ark.data.remote.dtos

import com.google.firebase.firestore.Exclude

//dto del documento users/{uid}/cookies/{cookieId} en firestore
//solo viajan los datos de la galleta: la ruta del archivo en el telefono y lastOpenedAt son de cada telefono
data class CookieDto(
    //id del documento (el mismo uuid de room y del jpg en storage); no se guarda como campo
    @get:Exclude
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val text: String = "",
    //ACTIVE o EATEN
    val state: String = "ACTIVE",
    val hasImage: Boolean = false,
    //url de descarga del jpg en storage; vacia si no tiene imagen
    val imageUrl: String = "",
    //username de quien la regalo; null si es propia
    val receivedFrom: String? = null,
    val createdAt: Long = 0L,
    //el cambio mas reciente gana entre dos telefonos (last write wins)
    val updatedAt: Long = 0L,
)
