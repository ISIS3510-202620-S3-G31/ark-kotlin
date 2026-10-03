package com.moviles.ark.data.remote.apis

import com.moviles.ark.data.remote.dtos.JamendoTrackDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

//cliente http para consumir el api rest de jamendo music (#7)
class JamendoApiClient(
    private val clientId: String = CLIENT_ID
) {
    //consulta las pistas relajantes / instrumentales desde el api de jamendo
    suspend fun fetchAmbientTracks(limit: Int = 10): List<JamendoTrackDto> = withContext(Dispatchers.IO) {
        val endpoint = "https://api.jamendo.com/v3.0/tracks/?client_id=$clientId&format=json&limit=$limit&tags=ambient+instrumental&audioformat=mp32"
        val tracksList = mutableListOf<JamendoTrackDto>()

        var connection: HttpURLConnection? = null
        try {
            val url = URL(endpoint)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val responseString = reader.use { it.readText() }

                val jsonObject = JSONObject(responseString)
                val resultsArray = jsonObject.optJSONArray("results")

                if (resultsArray != null) {
                    for (i in 0 until resultsArray.length()) {
                        val trackJson = resultsArray.getJSONObject(i)
                        val id = trackJson.optString("id", "")
                        val name = trackJson.optString("name", "Ambient Track")
                        val duration = trackJson.optInt("duration", 0)
                        val artistName = trackJson.optString("artist_name", "Jamendo Artist")
                        val audioUrl = trackJson.optString("audio", "")
                        val albumImage = trackJson.optString("album_image", "")

                        if (audioUrl.isNotBlank()) {
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
                }
            }
        } catch (e: Exception) {
            //si no hay internet o falla la conexion devolvemos lista vacia para usar el fallback local
            e.printStackTrace()
        } finally {
            connection?.disconnect()
        }

        tracksList
    }

    companion object {
        const val CLIENT_ID = "a74e6c84"
    }
}
