package com.moviles.ark.data.repositories

import com.moviles.ark.domain.models.WishModel
import com.moviles.ark.domain.repositories.WishRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

//version temporal del repositorio del farol: guarda los deseos solo en memoria para poder probar las vistas
//se borran al cerrar la app; la reemplaza la version con room (local storage) y el lru (caching)
class InMemoryWishRepository : WishRepository {

    //todos los deseos, del mas nuevo al mas viejo
    private val wishes = MutableStateFlow<List<WishModel>>(emptyList())

    //ids de los faroles encendidos; el primero de la lista es el que se uso hace mas tiempo
    private val litWishIds = MutableStateFlow<List<String>>(emptyList())

    override fun getWishes(): Flow<List<WishModel>> {
        return wishes
    }

    override fun getLitWishIds(): Flow<Set<String>> {
        //las pantallas piden un set, asi que la lista se convierte cada vez que cambia
        return litWishIds.map { ids -> ids.toSet() }
    }

    override suspend fun openWish(wishId: String): WishModel? {
        val wish = findWish(wishId)
        if (wish != null) {
            lightLantern(wishId)
        }
        return wish
    }

    override suspend fun saveWish(text: String): Result<WishModel> {
        val now = System.currentTimeMillis()
        val newWish = WishModel(
            id = UUID.randomUUID().toString(),
            text = text.trim(),
            createdAt = now,
            updatedAt = now
        )
        //el nuevo va de primero porque es el mas reciente
        val newList = mutableListOf(newWish)
        newList.addAll(wishes.value)
        wishes.value = newList

        //un deseo recien guardado llega al cielo encendido
        lightLantern(newWish.id)
        return Result.success(newWish)
    }

    override suspend fun updateWish(wishId: String, newText: String): Result<Unit> {
        val newList = mutableListOf<WishModel>()
        for (wish in wishes.value) {
            if (wish.id == wishId) {
                val editedWish = wish.copy(text = newText.trim(), updatedAt = System.currentTimeMillis())
                newList.add(editedWish)
            } else {
                newList.add(wish)
            }
        }
        wishes.value = newList
        return Result.success(Unit)
    }

    override suspend fun deleteWish(wishId: String): Result<Unit> {
        val newList = mutableListOf<WishModel>()
        for (wish in wishes.value) {
            if (wish.id != wishId) {
                newList.add(wish)
            }
        }
        wishes.value = newList

        //un deseo borrado tampoco puede quedar encendido
        val newLitIds = litWishIds.value.toMutableList()
        newLitIds.remove(wishId)
        litWishIds.value = newLitIds
        return Result.success(Unit)
    }

    private fun findWish(wishId: String): WishModel? {
        for (wish in wishes.value) {
            if (wish.id == wishId) {
                return wish
            }
        }
        return null
    }

    //enciende un farol: pasa a ser el mas reciente y, si ya hay demasiados encendidos, se apaga el mas viejo
    private fun lightLantern(wishId: String) {
        val newLitIds = litWishIds.value.toMutableList()
        newLitIds.remove(wishId)
        newLitIds.add(wishId)
        if (newLitIds.size > WishModel.MAX_LIT_LANTERNS) {
            newLitIds.removeAt(0)
        }
        litWishIds.value = newLitIds
    }
}
