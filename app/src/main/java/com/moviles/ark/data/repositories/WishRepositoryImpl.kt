package com.moviles.ark.data.repositories

import com.moviles.ark.data.local.daos.WishDao
import com.moviles.ark.data.local.entities.WishEntity
import com.moviles.ark.data.local.entities.toModel
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.domain.models.WishModel
import com.moviles.ark.domain.repositories.WishRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
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
class WishRepositoryImpl(
    private val wishDao: WishDao,
    //uid del usuario con sesion; null si no hay sesion
    private val userIdProvider: () -> String?,
    //avisa que hay algo nuevo por subir; no se espera
    private val onWishChanged: () -> Unit = {},
    //hilo para leer y escribir en la base de datos
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    //hilo para trabajo pesado de cpu, como ordenar listas
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : WishRepository {

    //faroles encendidos: ids de los deseos abiertos hace poco; el primero es el que se uso hace mas tiempo
    //es una version sencilla mientras tanto: el issue de caching la cambia por un LruCache de verdad
    private val litWishIds = MutableStateFlow<List<String>>(emptyList())

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
        //las pantallas piden un set, asi que la lista se convierte cada vez que cambia
        return litWishIds.map { ids -> ids.toSet() }
    }

    // ---------- lecturas y escrituras de una vez (suspend) ----------
    //cada funcion publica cambia al hilo de IO y la funcion privada de abajo hace el trabajo

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

    private suspend fun openWishFromRoom(wishId: String): WishModel? {
        val userId = userIdProvider()
        if (userId == null) {
            return null
        }

        //el texto del deseo se lee solo ahora, cuando el usuario toca el farol
        val entity = wishDao.getWish(wishId, userId)
        if (entity == null) {
            return null
        }

        //al abrirlo, su farol se enciende
        lightLantern(wishId)
        return entity.toModel()
    }

    private suspend fun saveWishInRoom(text: String): Result<WishModel> {
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
            //el sync lo sube despues por debajo (SyncWorker con WorkManager), aunque se cierre la app
            onWishChanged()

            //un deseo recien guardado llega al cielo encendido
            lightLantern(newWish.id)
            return Result.success(newWish.toModel())
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

            //un deseo borrado tampoco puede quedar encendido
            turnOffLantern(wishId)
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

    //enciende un farol: pasa a ser el mas reciente y, si ya hay demasiados encendidos, se apaga el mas viejo
    //update hace el cambio de una sola vez, asi dos hilos al mismo tiempo no se pisan
    private fun lightLantern(wishId: String) {
        litWishIds.update { currentIds ->
            val newIds = currentIds.toMutableList()
            newIds.remove(wishId)
            newIds.add(wishId)
            if (newIds.size > WishModel.MAX_LIT_LANTERNS) {
                newIds.removeAt(0)
            }
            newIds
        }
    }

    //apaga un farol
    private fun turnOffLantern(wishId: String) {
        litWishIds.update { currentIds ->
            val newIds = currentIds.toMutableList()
            newIds.remove(wishId)
            newIds
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
