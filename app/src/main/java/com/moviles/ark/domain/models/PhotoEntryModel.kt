package com.moviles.ark.domain.models

//modelo de datos de una foto del dia guardada en el telefono (#25, #26, #31)
data class PhotoEntryModel(
    val id: Long = 0,
    //ruta del archivo dentro del almacenamiento privado de la app
    val localFilePath: String,
    //nota opcional que el usuario escribe sobre la foto
    val caption: String = "",
    //cuando se tomo, en milisegundos
    val takenAt: Long
) {
    //reglas de negocio
    fun isValidCaption(): Boolean {
        return caption.length <= MAX_CAPTION_LENGTH
    }

    companion object {
        const val MAX_CAPTION_LENGTH = 140
    }
}
