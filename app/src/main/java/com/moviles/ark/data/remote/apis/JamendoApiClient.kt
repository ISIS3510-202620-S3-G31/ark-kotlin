package com.moviles.ark.data.remote.apis

import com.moviles.ark.data.remote.dtos.JamendoTrackDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

//cliente http para consumir el api rest de jamendo music
class JamendoApiClient(
    private val clientId: String = CLIENT_ID
) {
    //consulta las pistas relajantes / instrumentales desde el api de jamendo
    //jamendo a veces responde "success" con la lista vacia: se reintenta un par de veces antes de rendirse
    //si no hay internet lanza la excepcion para que el adapter use las pistas guardadas en cache
    suspend fun fetchAmbientTracks(limit: Int = 10): List<JamendoTrackDto> = withContext(Dispatchers.IO) {
        var tracks = emptyList<JamendoTrackDto>()
        for (attempt in 1..MAX_ATTEMPTS) {
            tracks = requestTracks(limit)
            if (tracks.isNotEmpty()) break
            if (attempt < MAX_ATTEMPTS) delay(RETRY_DELAY_MS * attempt)
        }
        tracks
    }

    private fun requestTracks(limit: Int): List<JamendoTrackDto> {
        //durationbetween deja pistas de 2 a 8 minutos: ni jingles cortos ni archivos enormes para la cache
        val endpoint = "https://api.jamendo.com/v3.0/tracks/?client_id=$clientId&format=json&limit=$limit" +
            "&tags=ambient+instrumental&durationbetween=${MIN_DURATION_SECONDS}_${MAX_DURATION_SECONDS}&audioformat=mp32"
        val tracksList = mutableListOf<JamendoTrackDto>()

        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) throw IOException("jamendo respondio $responseCode")

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val responseString = reader.use { it.readText() }

            val jsonObject = JSONObject(responseString)
            val resultsArray = jsonObject.optJSONArray("results") ?: return tracksList

            for (i in 0 until resultsArray.length()) {
                val trackJson = resultsArray.getJSONObject(i)
                val id = trackJson.optString("id", "")
                val name = trackJson.optString("name", "Ambient Track")
                val duration = trackJson.optInt("duration", 0)
                val artistName = trackJson.optString("artist_name", "Jamendo Artist")
                //audio es la cancion completa (no un preview de 30 s); se puede reproducir por streaming
                val audioUrl = trackJson.optString("audio", "")
                val albumImage = trackJson.optString("album_image", "")

                if (id.isNotBlank() && audioUrl.isNotBlank()) {
                    tracksList.add(
                        JamendoTrackDto(
                            id = id,
                            name = name,
                            duration = duration,
                            artistName = artistName,
                            audioUrl = audioUrl,
                            albumImage = albumImage
                        )
                    )
                }
            }
        } finally {
            connection.disconnect()
        }

        return tracksList.distinctBy { it.id }
    }

    companion object {
        const val CLIENT_ID = "a74e6c84"
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 400L
        private const val MIN_DURATION_SECONDS = 120
        private const val MAX_DURATION_SECONDS = 480
    }
}
