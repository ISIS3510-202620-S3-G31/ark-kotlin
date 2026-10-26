package com.moviles.ark.data.remote.apis

import com.moviles.ark.domain.adapters.WeatherServiceAdapter
import com.moviles.ark.domain.models.WeatherModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

//adapta el api gratis de open-meteo (sin llave) a WeatherServiceAdapter (#134, #153)
//desacopla el consumo de open-meteo en Dispatchers.IO y cachea en context.cacheDir/weather_cache/weather.json con TTL de 1 hora
class OpenMeteoAdapterImpl(
    private val cacheDir: File? = null,
    private val baseUrl: String = BASE_URL,
    private val now: () -> Long = { System.currentTimeMillis() }
) : WeatherServiceAdapter {

    private val cacheFile: File?
        get() = cacheDir?.let { File(File(it, CACHE_DIR_NAME), FILE_NAME) }

    //obtiene el clima en Dispatchers.IO: si la cache en disco tiene menos de 1 hora se sirve sin acceso a red (#153)
    override suspend fun getCurrentWeather(latitude: Double, longitude: Double): Result<WeatherModel> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Validar cache de disco con TTL = 1 hora
            val cachedWeather = getCachedWeatherIfValid()
            if (cachedWeather != null) {
                return@runCatching cachedWeather
            }

            // 2. Si la cache expiro o no existe, consultar el api de Open-Meteo
            // Locale.US para que las coordenadas salgan con punto y no con coma
            val endpoint = String.format(
                Locale.US,
                "%s?latitude=%.4f&longitude=%.4f&current=temperature_2m,precipitation,weather_code,is_day",
                baseUrl, latitude, longitude
            )
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            val body = try {
                connection.requestMethod = "GET"
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                check(connection.responseCode == HttpURLConnection.HTTP_OK) { "Open-Meteo answered ${connection.responseCode}" }
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }

            val weather = parseCurrent(body)

            // 3. Guardar respuesta en context.cacheDir/weather_cache/weather.json con timestamp
            cacheFile?.let { file ->
                runCatching {
                    file.parentFile?.mkdirs()
                    val jsonToCache = JSONObject(body).apply {
                        put(TIMESTAMP_KEY, now())
                    }
                    val temp = File(file.parentFile, "$FILE_NAME.tmp")
                    temp.writeText(jsonToCache.toString())
                    if (!temp.renameTo(file)) {
                        file.writeText(jsonToCache.toString())
                        temp.delete()
                    }
                }
            }

            weather
        }
    }

    //sirve el clima de la cache si aun es valido (TTL < 1 hora), sin requerir red ni coordenadas (#153)
    fun getCachedWeatherIfValid(): WeatherModel? {
        val file = cacheFile ?: return null
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText())
            val timestamp = json.optLong(TIMESTAMP_KEY, 0L)
            if (now() - timestamp < TTL_MILLIS) {
                parseCurrent(json.toString())
            } else {
                null
            }
        }.getOrNull()
    }

    companion object {
        private const val BASE_URL = "https://api.open-meteo.com/v1/forecast"
        private const val TIMEOUT_MS = 8000
        const val CACHE_DIR_NAME = "weather_cache"
        const val FILE_NAME = "weather.json"
        const val TIMESTAMP_KEY = "timestamp"
        const val TTL_MILLIS = 60 * 60 * 1000L // 1 hora de TTL

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
