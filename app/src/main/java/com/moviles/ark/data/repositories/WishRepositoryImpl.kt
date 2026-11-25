package com.moviles.ark.data.repositories

import com.moviles.ark.data.local.WishMemoryCache
import com.moviles.ark.data.local.daos.WishDao
import com.moviles.ark.data.local.entities.WishEntity
import com.moviles.ark.data.local.entities.toModel
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.domain.models.WishModel
import com.moviles.ark.domain.repositories.WishRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

//repositorio del farol de deseos con room (local storage)
//la app siempre lee y escribe en room, asi todo funciona sin internet
//cada cambio deja isSynced = false y avisa con onWishChanged; el sync lo sube a firestore despues
//
//hilos (multithreading):
//- leer y escribir en room va en ioDispatcher (Dispatchers.IO), para no trabar la animacion de la llama
//- ordenar y convertir la lista completa va en defaultDispatcher (Dispatchers.Default), que es para trabajo de cpu
//- el hilo principal (Main) solo recibe el resultado listo
//
//cache (caching):
//- los faroles encendidos son los deseos que estan en memoryCache, un LruCache de maximo 8 deseos
//- al tocar un farol primero se busca en la cache; solo si no esta se va a room
//- al editar o borrar un deseo tambien se cambia la cache, asi el cielo nunca muestra texto viejo
class WishRepositoryImpl(
    private val wishDao: WishDao,
    //uid del usuario con sesion; null si no hay sesion
    private val userIdProvider: () -> String?,
    //avisa que hay algo nuevo por subir; no se espera
    private val onWishChanged: () -> Unit = {},
    //hilo para leer y escribir en la base de datos
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    //hilo para trabajo pesado de cpu, como ordenar listas
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    //cache en memoria de los faroles encendidos (LruCache de 8 deseos)
    private val memoryCache: WishMemoryCache = WishMemoryCache()
) : WishRepository {

    //de que usuario son los deseos que estan en la cache; si entra otro usuario la cache se vacia
    private var cacheUserId: String? = null

    // ---------- lecturas que se actualizan solas (Flow) ----------

    override fun getWishes(): Flow<List<WishModel>> {
        //sin sesion no se muestran deseos de nadie
        val userId = userIdProvider()
        if (userId == null) {
            return flowOf(emptyList())
        }

        //room hace la consulta en su propio hilo de fondo
        //ordenar y convertir las filas se hace en Dispatchers.Default (eso es lo que hace flowOn)
        return wishDao.observeWishes(userId)
            .map { entities -> sortNewestFirst(entities) }
            .flowOn(defaultDispatcher)
    }

    override fun getWishIds(): Flow<List<String>> {
        val userId = userIdProvider()
        if (userId == null) {
            return flowOf(emptyList())
        }

        //consulta liviana: solo ids, leidos en Dispatchers.IO
        return wishDao.observeWishIds(userId)
            .flowOn(ioDispatcher)
    }

    override fun getLitWishIds(): Flow<Set<String>> {
        //los faroles encendidos son los deseos que estan en la cache
        return memoryCache.observeLitWishIds()
    }

    // ---------- lecturas y escrituras de una vez (suspend) ----------
    //cada funcion publica cambia al hilo de IO y la funcion privada de abajo hace el trabajo

    override suspend fun warmUpCache() {
        withContext(ioDispatcher) {
            warmUpCacheFromRoom()
        }
    }

    override suspend fun openWish(wishId: String): WishModel? {
        return withContext(ioDispatcher) {
            openWishFromRoom(wishId)
        }
    }

    override suspend fun saveWish(text: String): Result<WishModel> {
        return withContext(ioDispatcher) {
            saveWishInRoom(text)
        }
    }

    override suspend fun updateWish(wishId: String, newText: String): Result<Unit> {
        return withContext(ioDispatcher) {
            updateWishInRoom(wishId, newText)
        }
    }

    override suspend fun deleteWish(wishId: String): Result<Unit> {
        return withContext(ioDispatcher) {
            deleteWishInRoom(wishId)
        }
    }

    // ---------- el trabajo de verdad (ya corre en IO) ----------

    private suspend fun warmUpCacheFromRoom() {
        val userId = userIdProvider()
        if (userId == null) {
            return
        }
        makeSureCacheIsForUser(userId)

        //si la cache ya tiene faroles (por ejemplo, el usuario salio y volvio a entrar) se deja como esta
        if (!memoryCache.isEmpty()) {
            return
        }

        //los 8 deseos mas recientes, del mas nuevo al mas viejo
        val newestWishes = wishDao.getNewestWishes(userId, WishModel.MAX_LIT_LANTERNS)

        //se meten del mas viejo al mas nuevo: asi el mas nuevo queda como el mas recientemente usado
        //y si luego se llena la cache, el primero en apagarse es el mas viejo
        for (index in newestWishes.size - 1 downTo 0) {
            val entity = newestWishes[index]
            memoryCache.put(entity.toModel())
        }
    }

    private suspend fun openWishFromRoom(wishId: String): WishModel? {
        val userId = userIdProvider()
        if (userId == null) {
            return null
        }
        makeSureCacheIsForUser(userId)

        //1. primero se busca en la cache: si el farol estaba encendido no hace falta ir a room
        val cachedWish = memoryCache.get(wishId)
        if (cachedWish != null) {
            return cachedWish
        }

        //2. no estaba (farol apagado): se lee de room
        val entity = wishDao.getWish(wishId, userId)
        if (entity == null) {
            return null
        }

        //3. se mete a la cache y su farol se enciende
        //si la cache estaba llena, el LruCache saca el que hace mas tiempo no se usa y ese farol se apaga
        val wish = entity.toModel()
        memoryCache.put(wish)
        return wish
    }

    private suspend fun saveWishInRoom(text: String): Result<WishModel> {
        try {
            val userId = userIdProvider()
            if (userId == null) {
                return Result.failure(IllegalStateException("No user is logged in"))
            }
            makeSureCacheIsForUser(userId)

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
            //el sync lo sube despues por debajo (SyncWorker con WorkManager), aunque se cierre la app
            onWishChanged()

            //un deseo recien guardado entra a la cache, asi llega al cielo encendido
            val savedWish = newWish.toModel()
            memoryCache.put(savedWish)
            return Result.success(savedWish)
        } catch (e: Exception) {
            logError("saveWish", e)
            return Result.failure(e)
        }
    }

    private suspend fun updateWishInRoom(wishId: String, newText: String): Result<Unit> {
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

            //si el deseo estaba en la cache, se cambia por el texto nuevo
            val editedEntity = wishDao.getWish(wishId, userId)
            if (editedEntity != null) {
                memoryCache.replaceIfPresent(editedEntity.toModel())
            }

            onWishChanged()
            return Result.success(Unit)
        } catch (e: Exception) {
            logError("updateWish", e)
            return Result.failure(e)
        }
    }

    private suspend fun deleteWishInRoom(wishId: String): Result<Unit> {
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

            //un deseo borrado sale de la cache: su farol ya no puede quedar encendido
            memoryCache.remove(wishId)
            return Result.success(Unit)
        } catch (e: Exception) {
            logError("deleteWish", e)
            return Result.failure(e)
        }
    }

    // ---------- ayudantes ----------

    //convierte las filas en modelos y las ordena de la mas nueva a la mas vieja
    //corre en Dispatchers.Default porque con muchos deseos es trabajo pesado
    private fun sortNewestFirst(entities: List<WishEntity>): List<WishModel> {
        val wishes = mutableListOf<WishModel>()
        for (entity in entities) {
            wishes.add(entity.toModel())
        }
        wishes.sortByDescending { wish -> wish.createdAt }
        return wishes
    }

    //la cache solo puede tener deseos de un usuario
    //si cambio el usuario (cerro sesion y entro otro), se vacia para no mostrar deseos ajenos
    private fun makeSureCacheIsForUser(userId: String) {
        if (cacheUserId != userId) {
            memoryCache.clear()
            cacheUserId = userId
        }
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
