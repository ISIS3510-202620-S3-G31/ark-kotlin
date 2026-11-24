package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.WishModel
import kotlinx.coroutines.flow.Flow

//repositorio de dominio del farol de deseos
//las pantallas solo hablan con esta interfaz; no saben si los deseos estan en memoria, en room o en firestore
interface WishRepository {
    //todos los deseos del usuario, del mas nuevo al mas viejo; se actualiza solo cuando algo cambia
    fun getWishes(): Flow<List<WishModel>>

    //ids de los deseos que estan en la cache (los faroles encendidos)
    fun getLitWishIds(): Flow<Set<String>>

    //busca un deseo y lo mete a la cache: su farol queda encendido; null si no existe
    suspend fun openWish(wishId: String): WishModel?

    //guarda un deseo nuevo (prende el farol) y lo devuelve
    suspend fun saveWish(text: String): Result<WishModel>

    //cambia el texto de un deseo que ya existe
    suspend fun updateWish(wishId: String, newText: String): Result<Unit>

    //borra un deseo
    suspend fun deleteWish(wishId: String): Result<Unit>
}
