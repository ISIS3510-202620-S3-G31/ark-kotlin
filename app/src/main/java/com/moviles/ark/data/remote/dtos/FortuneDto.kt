package com.moviles.ark.data.remote.dtos

import com.moviles.ark.data.local.entities.FavoriteFortuneCookieEntity
import com.moviles.ark.data.local.entities.SavedFortuneCardEntity
import com.moviles.ark.domain.models.FortuneModel
import org.json.JSONObject

//respuesta de dumbapis.com/fortune, por ejemplo {"fortune": "..."}
data class DumbFortuneDto(val fortune: String = "") {
    //de respuesta del api a modelo
    fun toModel(): FortuneModel = FortuneModel(id = FortuneModel.generateId(fortune.trim()), quote = fortune.trim(), category = "Wisdom")

    companion object {
        //lee el json que devuelve el api
        fun fromJson(text: String): DumbFortuneDto = DumbFortuneDto(JSONObject(text).getString("fortune"))
    }
}

//respuesta de api.adviceslip.com/advice, por ejemplo {"slip": {"id": 76, "advice": "..."}}
data class AdviceSlipDto(val id: Int = 0, val advice: String = "") {
    //de respuesta del api a modelo, el id sale de la frase para que no se repita con la otra api
    fun toModel(): FortuneModel = FortuneModel(id = FortuneModel.generateId(advice.trim()), quote = advice.trim(), category = "Advice")

    companion object {
        //lee el json que devuelve el api
        fun fromJson(text: String): AdviceSlipDto {
            val slip = JSONObject(text).getJSONObject("slip")
            return AdviceSlipDto(slip.optInt("id"), slip.getString("advice"))
        }
    }
}

//como se guarda una frase favorita en firestore, documento users/{uid}/favorite_fortunes/{id}
data class FavoriteFortuneDto(val id: Int = 0, val quote: String = "", val category: String = "Wisdom", val savedAt: Long = 0L) {
    //lo que se sube a firestore
    fun toMap(): Map<String, Any> = mapOf("id" to id, "quote" to quote, "category" to category, "savedAt" to savedAt)

    //de dto a fila de room, ya viene de la nube asi que esta sincronizada
    fun toEntity(userId: String): FavoriteFortuneCookieEntity = FavoriteFortuneCookieEntity(userId, id, quote, category, savedAt, isSynced = true)
}

//como se guarda una imagen de frase en firestore, documento users/{uid}/saved_fortune_cards/{cookieId}
data class FortuneCardDto(val cookieId: Int = 0, val quote: String = "", val remoteImageUrl: String = "", val savedAt: Long = 0L) {
    //lo que se sube a firestore
    fun toMap(): Map<String, Any> = mapOf("cookieId" to cookieId, "quote" to quote, "remoteImageUrl" to remoteImageUrl, "savedAt" to savedAt)

    //de dto a fila de room, la imagen solo esta en la nube hasta que se vuelva a compartir
    fun toEntity(userId: String): SavedFortuneCardEntity = SavedFortuneCardEntity(cookieId = cookieId, userId = userId, quote = quote, localImageUri = "", remoteImageUrl = remoteImageUrl, savedAt = savedAt, isSynced = true)
}

//de fila de room a dto, para subir la favorita
fun FavoriteFortuneCookieEntity.toDto(): FavoriteFortuneDto = FavoriteFortuneDto(id, quote, category, savedAt)

//de fila de room a dto, para subir la imagen con su url de storage
fun SavedFortuneCardEntity.toDto(remoteUrl: String): FortuneCardDto = FortuneCardDto(cookieId, quote, remoteUrl, savedAt)
