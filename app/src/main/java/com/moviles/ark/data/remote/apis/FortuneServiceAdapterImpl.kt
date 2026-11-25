package com.moviles.ark.data.remote.apis

import com.moviles.ark.domain.adapters.FortuneServiceAdapter
import com.moviles.ark.domain.models.FortuneModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

//adaptador que consume las dos apis publicas dumbapis y adviceslip en un hilo secundario
class FortuneServiceAdapterImpl : FortuneServiceAdapter {

    //obtiene una frase tradicional de galleta desde dumbapis
    override suspend fun fetchDumbFortune(): Result<FortuneModel> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://dumbapis.com/fortune")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val json = JSONObject(text)
                val quote = json.getString("fortune").trim()
                val id = FortuneModel.generateId(quote)
                Result.success(FortuneModel(id = id, quote = quote, category = "Wisdom"))
            } else {
                connection.disconnect()
                Result.failure(Exception("error dumbapis codigo ${connection.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    //obtiene un consejo de vida desde adviceslip
    override suspend fun fetchAdviceSlip(): Result<FortuneModel> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.adviceslip.com/advice")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val json = JSONObject(text)
                val slip = json.getJSONObject("slip")
                val quote = slip.getString("advice").trim()
                val id = FortuneModel.generateId(quote)
                Result.success(FortuneModel(id = id, quote = quote, category = "Advice"))
            } else {
                connection.disconnect()
                Result.failure(Exception("error adviceslip codigo ${connection.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    //obtiene una frase remota generica para compatibilidad
    override suspend fun fetchFortune(): Result<FortuneModel> = fetchAdviceSlip()

    //trae 2 frases de dumbapis y 2 frases de adviceslip en paralelo
    override suspend fun fetchTwoAndTwo(): List<FortuneModel> = withContext(Dispatchers.IO) {
        val dumb1 = async { fetchDumbFortune().getOrNull() }
        val dumb2 = async { fetchDumbFortune().getOrNull() }
        val advice1 = async { fetchAdviceSlip().getOrNull() }
        val advice2 = async { fetchAdviceSlip().getOrNull() }
        val results = awaitAll(dumb1, dumb2, advice1, advice2)
        results.filterNotNull()
    }
}
