package com.moviles.ark.domain.adapters

import com.moviles.ark.domain.models.FortuneModel

//interfaz del adaptador de servicio para obtener frases de sabiduria remotas
interface FortuneServiceAdapter {
    suspend fun fetchFortune(): Result<FortuneModel>
}
