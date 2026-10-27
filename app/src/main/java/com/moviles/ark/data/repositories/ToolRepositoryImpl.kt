package com.moviles.ark.data.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.moviles.ark.data.local.ToolCatalogFileStore
import com.moviles.ark.data.local.daos.ToolDao
import com.moviles.ark.data.local.entities.toDomain
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.data.remote.dtos.ToolDto
import com.moviles.ark.data.remote.dtos.toDto
import com.moviles.ark.data.remote.dtos.toEntity
import com.moviles.ark.domain.models.Tool
import com.moviles.ark.domain.repositories.ToolRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

//catalogo de herramientas con room como unica fuente de verdad
//la ui solo escucha la tabla tools; firestore solo se usa en refreshTools para traer lo nuevo y guardarlo en room
//filesDir/tools.json guarda la misma copia para no perder el catalogo sin internet
class ToolRepositoryImpl(
    private val toolDao: ToolDao,
    private val firestore: FirebaseFirestore,
    private val catalogFileStore: ToolCatalogFileStore
) : ToolRepository {

    override fun getTools(): Flow<List<Tool>> {
        return toolDao.getTools()
            //la primera vez la tabla esta vacia: se llena con el catalogo del archivo interno antes de emitir
            .onStart { seedIfEmpty() }
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    //"Surprise me" lo usa sin red; si falla se reporta a crashlytics y el home abre la herramienta de respaldo
    override suspend fun getLocalTools(): Result<List<Tool>> = runCatching {
        getTools().first()
    }.onFailure { error ->
        if (error is kotlinx.coroutines.CancellationException) throw error
        com.moviles.ark.data.remote.CrashlyticsHelper.logNonFatal(
            componentName = "ToolRepository",
            action = "getLocalTools",
            throwable = error
        )
    }

    override suspend fun getToolById(id: String): Tool? = withContext(Dispatchers.IO) {
        seedIfEmpty()
        toolDao.getToolById(id)?.toDomain()
    }

    override suspend fun refreshTools(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            //Source.SERVER: sin internet falla en vez de devolver la copia de cache de firestore
            val snapshot = firestore.collection(TOOLS_COLLECTION).get(Source.SERVER).await()
            val remoteTools = snapshot.documents.mapNotNull { document ->
                //si el documento no trae el campo id se usa el id del documento
                document.toObject(ToolDto::class.java)?.let { dto -> if (dto.id.isBlank()) dto.copy(id = document.id) else dto }
            }
            //solo se reemplaza el catalogo si llego algo: una coleccion vacia no borra el del telefono
            if (remoteTools.isNotEmpty()) storeCatalog(remoteTools)
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(
                componentName = "ToolRepository",
                action = "refreshTools",
                throwable = error
            )
        }
    }

    override suspend fun saveCatalog(tools: List<Tool>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (tools.isEmpty()) error("The tools catalog is empty")
            storeCatalog(tools.map { it.toDto() })
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(
                componentName = "ToolRepository",
                action = "saveCatalog",
                throwable = error,
                extraKeys = mapOf("tools_count" to tools.size)
            )
        }
    }

    //room para la pantalla y filesDir/tools.json para no perderlo sin internet
    private suspend fun storeCatalog(tools: List<ToolDto>) {
        toolDao.replaceTools(tools.map { it.toEntity() })
        catalogFileStore.writeCatalog(tools)
    }

    //room se llena desde filesDir/tools.json (o assets/tools.json si el archivo falta o esta danado)
    //insertTools usa REPLACE, asi que si dos pantallas siembran a la vez no se duplica nada
    private suspend fun seedIfEmpty() {
        if (toolDao.getTools().first().isEmpty()) {
            toolDao.insertTools(catalogFileStore.readCatalog().map { it.toEntity() })
        }
    }

    companion object {
        private const val TOOLS_COLLECTION = "tools"

        //el catalogo por defecto es del dominio (Tool.DEFAULT_CATALOG); este nombre se deja para el codigo de datos que ya lo usa
        val sampleTools = Tool.DEFAULT_CATALOG
    }
}
