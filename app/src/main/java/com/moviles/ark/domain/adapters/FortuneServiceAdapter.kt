package com.moviles.ark.domain.adapters

import com.moviles.ark.domain.models.FortuneModel

//interfaz del adaptador de servicio para obtener frases de sabiduria remotas
interface FortuneServiceAdapter {
    //obtiene una frase tradicional de galleta desde dumbapis
    suspend fun fetchDumbFortune(): Result<FortuneModel>

    //obtiene un consejo de vida desde adviceslip
    suspend fun fetchAdviceSlip(): Result<FortuneModel>

    //obtiene una frase remota generica para compatibilidad
    suspend fun fetchFortune(): Result<FortuneModel> = fetchAdviceSlip()

    //trae 2 frases de dumbapis y 2 de adviceslip en paralelo
    suspend fun fetchTwoAndTwo(): List<FortuneModel>
}
