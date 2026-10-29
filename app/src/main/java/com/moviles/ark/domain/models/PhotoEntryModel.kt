package com.moviles.ark.domain.models

//modelo de datos de una foto del dia guardada en el telefono
data class PhotoEntryModel(
    val id: Long = 0,
    //ruta del archivo dentro del almacenamiento privado de la app
    val localFilePath: String,
    //nota opcional que el usuario escribe sobre la foto
    val caption: String = "",
    //cuando se tomo, en milisegundos
    val takenAt: Long,
    //url de descarga de la copia en firebase storage; null si aun no se ha subido
    val remoteUrl: String? = null
) {
    //reglas de negocio
    fun isValidCaption(): Boolean {
        return caption.length <= MAX_CAPTION_LENGTH
    }

    companion object {
        const val MAX_CAPTION_LENGTH = 140
    }
}


//alias de retrocompatibilidad con codigo que use PhotoEntry
typealias PhotoEntry = PhotoEntryModel
