package com.moviles.ark.data.remote.apis

import com.moviles.ark.domain.adapters.FortuneServiceAdapter
import com.moviles.ark.domain.models.FortuneModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

//adaptador que consume la api publica de consejos y frases en un hilo secundario
class FortuneServiceAdapterImpl : FortuneServiceAdapter {

    //llamada de red en dispatchers.io para no bloquear la interfaz de usuario
    override suspend fun fetchFortune(): Result<FortuneModel> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.adviceslip.com/advice")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val json = JSONObject(responseText)
                val slip = json.getJSONObject("slip")
                val quote = slip.getString("advice").trim()
                val id = FortuneModel.generateId(quote)
                Result.success(FortuneModel(id = id, quote = quote, category = "Advice"))
            } else {
                connection.disconnect()
                Result.failure(Exception("error de servidor con codigo ${connection.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
