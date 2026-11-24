package com.moviles.ark.domain.models

import kotlin.math.abs

//modelo de la galleta de la fortuna para la aplicacion
data class FortuneModel(
    val id: Int,
    val quote: String,
    val category: String = "Wisdom",
    val luckyNumber: Int = (1..99).random(),
    val isFavorite: Boolean = false,
    val savedAt: Long = System.currentTimeMillis()
) {
    companion object {
        //calcula un id unico y determinista a partir de la frase para evitar duplicados sin colisiones
        fun generateId(quote: String): Int = abs(quote.hashCode())
    }
}

//resultado al romper una galleta indicando si vino de internet o de la reserva local
data class FortuneCrackResult(
    val fortune: FortuneModel,
    val isFromOfflineReserve: Boolean,
    val remainingChances: Int
)
