package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.PhotoEntryModel
import kotlinx.coroutines.flow.Flow

//fotos del dia guardadas en el telefono
//la implementacion real copia el jpg a context.filesDir y lo registra en room (PhotoEntryDao)
//cada usuario solo ve y guarda sus propias fotos
interface PhotoEntryRepository {
    //fotos del usuario con sesion tomadas entre dos momentos; vuelve a emitir cada vez que se guarda una
    fun getPhotosBetween(fromMillis: Long, toMillis: Long): Flow<List<PhotoEntryModel>>

    //guarda la foto que entrego la camara; sourceUri es la direccion de la foto como texto
    suspend fun savePhoto(sourceUri: String, caption: String, takenAt: Long): Result<PhotoEntryModel>
}
