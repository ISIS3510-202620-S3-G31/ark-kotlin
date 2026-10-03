package com.moviles.ark.data.repositories

import com.moviles.ark.data.remote.apis.JamendoApiClient
import com.moviles.ark.domain.models.AmbientTrackModel
import com.moviles.ark.domain.repositories.BreathingRepository

//implementacion del repositorio de respiracion que consume el api de jamendo (#7)
class BreathingRepositoryImpl(
    private val jamendoApiClient: JamendoApiClient = JamendoApiClient()
) : BreathingRepository {

    //pistas de respaldo precargadas por si no hay internet o falla la conexion
    private val fallbackTracks = listOf(
        AmbientTrackModel(
            id = "16398",
            title = "Eau bénite et feu sacré",
            artist = "Philippe Mangold",
            durationSeconds = 245,
            audioUrl = "https://prod-1.storage.jamendo.com/?trackid=16398&format=mp32&from=HlL4l9QaCPjx43C3x5c7IQ%3D%3D%7CwgxG1iCePB53BExtHdxvsA%3D%3D",
            coverImageUrl = "https://usercontent.jamendo.com?type=album&id=2334&width=300&trackid=16398"
        ),
        AmbientTrackModel(
            id = "1985894",
            title = "432 Hz Meditation with Theta Waves",
            artist = "Gaia Meditation",
            durationSeconds = 300,
            audioUrl = "https://prod-1.storage.jamendo.com/?trackid=1985894&format=mp32&from=XZWQY%2BAsQQCAH4x5ehQWCw%3D%3D%7CHbpFh54VH%2FN%2BchW905lRfA%3D%3D",
            coverImageUrl = "https://usercontent.jamendo.com?type=album&id=502461&width=300&trackid=1985894"
        ),
        AmbientTrackModel(
            id = "1699096",
            title = "Gratitude (Full Track)",
            artist = "AudioSphere",
            durationSeconds = 244,
            audioUrl = "https://prod-1.storage.jamendo.com/?trackid=1699096&format=mp32&from=AhQmKwTTI4egZk6tAmoRBw%3D%3D%7Ct%2Bn7F0e4kZyAWPC8zQYsZw%3D%3D",
            coverImageUrl = "https://usercontent.jamendo.com?type=album&id=439588&width=300&trackid=1699096"
        ),
        AmbientTrackModel(
            id = "1920257",
            title = "Winter Dream",
            artist = "Nargo",
            durationSeconds = 297,
            audioUrl = "https://prod-1.storage.jamendo.com/?trackid=1920257&format=mp32&from=I1%2F1C4VTD3tupTXbJ7i5rg%3D%3D%7CK0sJxXnQzhk7zxH5o3GFlw%3D%3D",
            coverImageUrl = "https://usercontent.jamendo.com?type=album&id=471628&width=300&trackid=1920257"
        ),
        AmbientTrackModel(
            id = "1906544",
            title = "Ambient Harp",
            artist = "Raw Vibrations",
            durationSeconds = 396,
            audioUrl = "https://prod-1.storage.jamendo.com/?trackid=1906544&format=mp32&from=LrwbzCN2oTZG2CmaYnrKsg%3D%3D%7CZIWfstqvGvX2e1VdFeswVw%3D%3D",
            coverImageUrl = "https://usercontent.jamendo.com?type=album&id=465310&width=300&trackid=1906544"
        ),
        AmbientTrackModel(
            id = "1705713",
            title = "Blissful Sky",
            artist = "AudioSphere",
            durationSeconds = 124,
            audioUrl = "https://prod-1.storage.jamendo.com/?trackid=1705713&format=mp32&from=un%2BPPB%2BRmu%2FfPWfDjAW00A%3D%3D%7CYfi6obcT5egK%2BmEsECshRA%3D%3D",
            coverImageUrl = "https://usercontent.jamendo.com?type=album&id=439700&width=300&trackid=1705713"
        )
    )

    override suspend fun getAmbientTracks(): Result<List<AmbientTrackModel>> {
        return try {
            val dtos = jamendoApiClient.fetchAmbientTracks(limit = 10)
            if (dtos.isNotEmpty()) {
                val models = dtos.map { dto ->
                    AmbientTrackModel(
                        id = dto.id,
                        title = dto.name,
                        artist = dto.artistName,
                        audioUrl = dto.audioUrl,
                        durationSeconds = dto.duration,
                        coverImageUrl = dto.albumImage.ifBlank { null }
                    )
                }
                Result.success(models)
            } else {
                Result.success(fallbackTracks)
            }
        } catch (e: Exception) {
            Result.success(fallbackTracks)
        }
    }
}
