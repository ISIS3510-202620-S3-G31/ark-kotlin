package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.PhotoEntryModel
import kotlinx.coroutines.flow.Flow

//fotos del dia guardadas en el telefono
//la implementacion real guarda el jpg en la galeria (MediaStore) y lo registra en room (PhotoEntryDao)
//cada usuario solo ve y guarda sus propias fotos
interface PhotoEntryRepository {
    //fotos del usuario con sesion tomadas entre dos momentos; vuelve a emitir cada vez que se guarda una
    fun getPhotosBetween(fromMillis: Long, toMillis: Long): Flow<List<PhotoEntryModel>>

    //guarda la foto que entrego la camara; sourceUri es la direccion de la foto como texto
    suspend fun savePhoto(sourceUri: String, caption: String, takenAt: Long): Result<PhotoEntryModel>
    //elimina la foto del almacenamiento local y de la base de datos
    suspend fun deletePhoto(photoId: Long): Result<Unit>
    //trae de firestore las fotos de ese rango que no esten en el telefono (otro telefono o app reinstalada)
    //sin red no hace nada: se queda con lo que ya hay en room
    suspend fun refreshFromCloud(fromMillis: Long, toMillis: Long)

    //true si el usuario ya dio permiso de camara
    fun hasCameraPermission(): Boolean

    //crea el archivo donde la camara deja la foto y devuelve su direccion; null si no se pudo crear
    fun createCaptureUri(): String?
}