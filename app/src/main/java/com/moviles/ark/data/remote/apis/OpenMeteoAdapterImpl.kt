package com.moviles.ark.data.remote.apis

import com.moviles.ark.domain.adapters.WeatherServiceAdapter
import com.moviles.ark.domain.models.WeatherModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

//adapta el api gratis de open-meteo (sin llave) a WeatherServiceAdapter
class OpenMeteoAdapterImpl(
    private val baseUrl: String = BASE_URL
) : WeatherServiceAdapter {

    override suspend fun getCurrentWeather(latitude: Double, longitude: Double): Result<WeatherModel> = withContext(Dispatchers.IO) {
        runCatching {
            //Locale.US para que las coordenadas salgan con punto y no con coma
            val endpoint = String.format(
                Locale.US,
                "%s?latitude=%.4f&longitude=%.4f&current=temperature_2m,precipitation,weather_code,is_day",
                baseUrl, latitude, longitude
            )
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                check(connection.responseCode == HttpURLConnection.HTTP_OK) { "Open-Meteo answered ${connection.responseCode}" }
                parseCurrent(connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }
        }
    }

    companion object {
        private const val BASE_URL = "https://api.open-meteo.com/v1/forecast"
        private const val TIMEOUT_MS = 8000

        //respuesta de open-meteo -> modelo de la app; si falta un campo lanza excepcion y el home no muestra la tarjeta
        fun parseCurrent(body: String): WeatherModel {
            val current = JSONObject(body).getJSONObject("current")
            return WeatherModel(
                temperatureC = current.getDouble("temperature_2m"),
                precipitationMm = current.optDouble("precipitation", 0.0),
                weatherCode = current.getInt("weather_code"),
                isDay = current.optInt("is_day", 1) == 1
            )
        }
    }
}
