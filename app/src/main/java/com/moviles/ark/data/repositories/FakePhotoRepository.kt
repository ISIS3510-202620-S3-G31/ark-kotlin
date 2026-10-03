package com.moviles.ark.data.repositories

import com.moviles.ark.domain.models.PhotoEntryModel
import com.moviles.ark.domain.repositories.PhotoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

//repositorio falso en memoria para la foto del dia, mientras llega el real con room y archivos (#26)
//las fotos se pierden al cerrar la app
class FakePhotoRepository(initialPhotos: List<PhotoEntryModel> = emptyList()) : PhotoRepository {
    private val photos = MutableStateFlow(initialPhotos)

    override fun getPhotosBetween(fromMillis: Long, toMillis: Long): Flow<List<PhotoEntryModel>> =
        photos.map { list -> list.filter { it.takenAt >= fromMillis && it.takenAt < toMillis } }

    //no copia el archivo: guarda la misma direccion que entrego la camara
    override suspend fun savePhoto(sourceUri: String, caption: String, takenAt: Long): Result<PhotoEntryModel> {
        val entry = PhotoEntryModel(
            id = (photos.value.maxOfOrNull { it.id } ?: 0) + 1,
            localFilePath = sourceUri,
            caption = caption,
            takenAt = takenAt
        )
        photos.value = photos.value + entry
        return Result.success(entry)
    }
}
