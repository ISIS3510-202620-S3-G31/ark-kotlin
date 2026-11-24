package com.moviles.ark.data.repositories

import com.moviles.ark.data.local.daos.WishDao
import com.moviles.ark.data.local.entities.WishEntity
import com.moviles.ark.data.local.entities.toModel
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.domain.models.WishModel
import com.moviles.ark.domain.repositories.WishRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

//repositorio del farol de deseos con room (local storage)
//la app siempre lee y escribe en room, asi todo funciona sin internet
//cada cambio deja isSynced = false y avisa con onWishChanged; el sync lo sube a firestore despues
class WishRepositoryImpl(
    private val wishDao: WishDao,
    //uid del usuario con sesion; null si no hay sesion
    private val userIdProvider: () -> String?,
    //avisa que hay algo nuevo por subir; no se espera
    private val onWishChanged: () -> Unit = {}
) : WishRepository {

    //faroles encendidos: ids de los deseos abiertos hace poco; el primero es el que se uso hace mas tiempo
    //es una version sencilla mientras tanto: el issue de caching la cambia por un LruCache de verdad
    private val litWishIds = MutableStateFlow<List<String>>(emptyList())

    override fun getWishes(): Flow<List<WishModel>> {
        //sin sesion no se muestran deseos de nadie
        val userId = userIdProvider()
        if (userId == null) {
            return flowOf(emptyList())
        }

        //room manda las filas y aqui se pasan a modelos para las pantallas
        return wishDao.observeWishes(userId).map { entities ->
            val wishes = mutableListOf<WishModel>()
            for (entity in entities) {
                wishes.add(entity.toModel())
            }
            wishes
        }
    }

    override fun getLitWishIds(): Flow<Set<String>> {
        //las pantallas piden un set, asi que la lista se convierte cada vez que cambia
        return litWishIds.map { ids -> ids.toSet() }
    }

    override suspend fun openWish(wishId: String): WishModel? {
        val userId = userIdProvider()
        if (userId == null) {
            return null
        }

        val entity = wishDao.getWish(wishId, userId)
        if (entity == null) {
            return null
        }

        //al abrirlo, su farol se enciende
        lightLantern(wishId)
        return entity.toModel()
    }

    override suspend fun saveWish(text: String): Result<WishModel> {
        try {
            val userId = userIdProvider()
            if (userId == null) {
                return Result.failure(IllegalStateException("No user is logged in"))
            }

            val now = System.currentTimeMillis()
            val newWish = WishEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                text = text.trim(),
                createdAt = now,
                updatedAt = now,
                //todavia no esta en firestore
                isSynced = false,
                isDeleted = false
            )
            wishDao.insertWish(newWish)
            onWishChanged()

            //un deseo recien guardado llega al cielo encendido
            lightLantern(newWish.id)
            return Result.success(newWish.toModel())
        } catch (e: Exception) {
            logError("saveWish", e)
            return Result.failure(e)
        }
    }

    override suspend fun updateWish(wishId: String, newText: String): Result<Unit> {
        try {
            val userId = userIdProvider()
            if (userId == null) {
                return Result.failure(IllegalStateException("No user is logged in"))
            }

            //cambia el texto y lo deja pendiente de subir
            val changedRows = wishDao.updateText(
                id = wishId,
                userId = userId,
                text = newText.trim(),
                updatedAt = System.currentTimeMillis()
            )
            if (changedRows == 0) {
                return Result.failure(IllegalStateException("The wish does not exist"))
            }

            onWishChanged()
            return Result.success(Unit)
        } catch (e: Exception) {
            logError("updateWish", e)
            return Result.failure(e)
        }
    }

    override suspend fun deleteWish(wishId: String): Result<Unit> {
        try {
            val userId = userIdProvider()
            if (userId == null) {
                return Result.failure(IllegalStateException("No user is logged in"))
            }

            //no se borra la fila de una vez: se marca como borrada para que el sync tambien la borre de firestore
            //y luego borra la fila del telefono. se hace asi aunque nunca se haya subido, porque puede que justo
            //se este subiendo en ese momento; borrar en firestore algo que no existe no da error
            wishDao.markAsDeleted(
                id = wishId,
                userId = userId,
                updatedAt = System.currentTimeMillis()
            )
            onWishChanged()

            //un deseo borrado tampoco puede quedar encendido
            turnOffLantern(wishId)
            return Result.success(Unit)
        } catch (e: Exception) {
            logError("deleteWish", e)
            return Result.failure(e)
        }
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

    //apaga un farol
    private fun turnOffLantern(wishId: String) {
        val newLitIds = litWishIds.value.toMutableList()
        newLitIds.remove(wishId)
        litWishIds.value = newLitIds
    }

    //guarda el error en crashlytics para poder revisarlo despues
    private fun logError(action: String, error: Exception) {
        CrashlyticsHelper.logNonFatal(
            componentName = "WishRepository",
            action = action,
            throwable = error
        )
    }
}
