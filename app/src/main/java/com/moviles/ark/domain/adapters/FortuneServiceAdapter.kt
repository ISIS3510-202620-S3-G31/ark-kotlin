package com.moviles.ark.domain.adapters

import com.moviles.ark.domain.models.FortuneModel

//adaptador de las dos apis de frases
interface FortuneServiceAdapter {
    //una frase de galleta de dumbapis
    suspend fun fetchDumbFortune(): Result<FortuneModel>

    //un consejo de adviceslip
    suspend fun fetchAdviceSlip(): Result<FortuneModel>

    //2 frases de cada api al mismo tiempo
    suspend fun fetchTwoAndTwo(): List<FortuneModel>
}
