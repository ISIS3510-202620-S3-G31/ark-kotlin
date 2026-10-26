package com.moviles.ark.data.repositories

import com.moviles.ark.domain.models.CachedRecommendation
import com.moviles.ark.domain.models.RecommendationReason
import com.moviles.ark.domain.repositories.RecommendationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

//guarda la ultima recomendacion en cacheDir/last_recommendation.json (#159)
//es cache: si android borra el archivo para liberar espacio no pasa nada, el home la vuelve a calcular
class RecommendationRepositoryImpl(
    cacheDir: File
) : RecommendationRepository {
    private val file = File(cacheDir, FILE_NAME)

    override suspend fun saveLastRecommendation(recommendation: CachedRecommendation): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject()
                .put("reason", recommendation.reason.name)
                .put("toolIds", JSONArray(recommendation.toolIds))
                .put("createdAt", recommendation.createdAt)
            //se escribe en un temporal y se renombra para no dejar un archivo a medias si la app se cierra
            val temp = File(file.parentFile, "$FILE_NAME.tmp")
            temp.writeText(json.toString())
            if (!temp.renameTo(file)) {
                file.writeText(json.toString())
                temp.delete()
            }
        }
    }

    override suspend fun getLastRecommendation(): CachedRecommendation? = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) return@runCatching null
            val json = JSONObject(file.readText())
            val ids = json.getJSONArray("toolIds")
            CachedRecommendation(
                reason = RecommendationReason.valueOf(json.getString("reason")),
                toolIds = List(ids.length()) { ids.getString(it) },
                createdAt = json.getLong("createdAt")
            )
        }.getOrNull()
    }

    companion object {
        private const val FILE_NAME = "last_recommendation.json"
    }
}
