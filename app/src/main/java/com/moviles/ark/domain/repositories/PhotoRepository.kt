package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.PhotoEntry
import kotlinx.coroutines.flow.Flow

//fotos del dia guardadas en el telefono
//la implementacion real (#26) copia el jpg a context.filesDir y lo registra en room (PhotoEntryDao)
interface PhotoRepository {
    //fotos tomadas entre dos momentos; vuelve a emitir cada vez que se guarda una
    fun getPhotosBetween(fromMillis: Long, toMillis: Long): Flow<List<PhotoEntry>>

    //guarda la foto que entrego la camara (#31); sourceUri es la direccion de la foto como texto
    suspend fun savePhoto(sourceUri: String, caption: String, takenAt: Long): Result<PhotoEntry>
}
