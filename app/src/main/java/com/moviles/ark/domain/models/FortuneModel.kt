package com.moviles.ark.domain.models

import android.net.Uri
import kotlin.math.abs

//una frase de galleta de la fortuna
data class FortuneModel(val id: Int, val quote: String, val category: String = "Wisdom", val isFavorite: Boolean = false, val savedAt: Long = System.currentTimeMillis()) {
    companion object {
        //el id sale de la misma frase, asi la misma frase siempre tiene el mismo id
        fun generateId(quote: String): Int = abs(quote.hashCode())
    }
}

//de donde salio la frase de una galleta
enum class FortuneSource { INTERNET, PHONE, BUILT_IN }

//una de las 3 galletas de la mesa con su frase, de donde salio y si se armo sin internet
data class TableFortune(val fortune: FortuneModel, val source: FortuneSource, val isOffline: Boolean)

//lo que devuelve abrir una galleta
data class FortuneCrackResult(val fortune: FortuneModel, val remainingChances: Int)

//imagen de una frase guardada en la galeria y en la nube
data class FortuneCardModel(val id: Long, val cookieId: Int, val quote: String, val imageUri: String, val savedAt: Long, val isBackedUp: Boolean, val remoteImageUrl: String? = null)

//imagen lista para compartir, isNew es true si se acaba de crear
data class FortuneShareResult(val uri: Uri, val isNew: Boolean)
