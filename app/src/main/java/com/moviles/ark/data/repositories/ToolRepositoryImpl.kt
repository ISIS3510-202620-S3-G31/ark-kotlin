package com.moviles.ark.data.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.moviles.ark.data.local.daos.ToolDao
import com.moviles.ark.data.local.entities.toDomain
import com.moviles.ark.data.local.entities.toEntity
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.data.remote.dtos.ToolDto
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

//catalogo de herramientas con room como unica fuente de verdad (#132)
//la ui solo escucha la tabla tools; firestore solo se usa en refreshTools para traer lo nuevo y guardarlo en room
class ToolRepositoryImpl(
    private val toolDao: ToolDao,
    private val firestore: FirebaseFirestore
) : ToolRepository {

    override fun getTools(): Flow<List<Tool>> {
        return toolDao.getTools()
            //la primera vez la tabla esta vacia: se llena con el catalogo por defecto antes de emitir
            .onStart { seedIfEmpty() }
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
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
            //solo se reemplaza room si llego algo: una coleccion vacia no borra el catalogo del telefono
            if (remoteTools.isNotEmpty()) {
                toolDao.replaceTools(remoteTools.map { it.toEntity() })
            }
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(
                componentName = "ToolRepository",
                action = "refreshTools",
                throwable = error
            )
        }
    }

    //insertTools usa REPLACE, asi que si dos pantallas siembran a la vez no se duplica nada
    private suspend fun seedIfEmpty() {
        if (toolDao.getTools().first().isEmpty()) {
            toolDao.insertTools(sampleTools.map { it.toEntity() })
        }
    }

    companion object {
        private const val TOOLS_COLLECTION = "tools"

        //catalogo por defecto del tool hub (frontend-kotlin); con el se llena room la primera vez y lo usan los previews
        val sampleTools = listOf(
            Tool("custom_breathing", "Custom breathing", "Build the rhythm that fits you.", "calm_down", "touch", "ic_tool_custom_breathing"),
            Tool("blow_it_out", "Blow it out", "Blow out the tension, one breath at a time.", "release", "voice", "ic_tool_blow_it_out"),
            Tool("scream_tank", "Scream tank", "Let it out. Nobody is listening but you.", "release", "voice", "ic_tool_scream_tank"),
            Tool("body_mapping", "Body mapping", "Mark where the feeling lives.", "reflect", "touch", "ic_tool_body_mapping"),
            Tool("emotion_detective", "Emotion detective", "Not sure what you feel? Let's find out.", "reflect", "text", "ic_tool_emotion_detective"),
            Tool("achievement_jar", "Achievement jar", "Save the good stuff. Read it when you need it.", "celebrate", "text", "ic_tool_achievement_jar"),
            Tool("photo_of_the_day", "Photo of the day", "One photo a day, one new memory.", "celebrate", "photo", "ic_tool_photo_of_the_day")
        )
    }
}
