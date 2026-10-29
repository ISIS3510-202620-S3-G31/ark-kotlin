package com.moviles.ark.data.repositories

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.moviles.ark.data.local.daos.ToolRecordDao
import com.moviles.ark.data.local.entities.ToolInteractionEntity
import com.moviles.ark.data.local.entities.toDomain
import com.moviles.ark.data.remote.dtos.ToolInteractionDto
import com.moviles.ark.data.remote.dtos.documentId
import com.moviles.ark.data.remote.dtos.toDomain
import com.moviles.ark.domain.models.ToolInteractionModel
import com.moviles.ark.domain.repositories.ToolInteractionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Date

//interacciones con herramientas: room guarda las que faltan por subir y el sync las borra al subirlas,
//asi que el historial completo = las de firestore + las pendientes de room; funciona sin internet
class ToolInteractionRepositoryImpl(
    private val toolRecordDao: ToolRecordDao,
    //sin sesion la interaccion se guarda como "anonymous"
    private val auth: FirebaseAuth? = null,
    private val firestore: FirebaseFirestore? = null,
    //avisa al SyncManager que hay algo por subir; no se espera
    private val onInteractionSaved: () -> Unit = {}
) : ToolInteractionRepository {

    override suspend fun getInteractionsSince(fromMillis: Long): Result<List<ToolInteractionModel>> = withContext(Dispatchers.IO) {
        runCatching {
            //first(): solo se necesita la foto actual de la tabla, no seguir escuchando cambios
            val pending = toolRecordDao.getInteractionsSince(fromMillis).first()
            val uploaded = uploadedInteractionsSince(fromMillis)
            //una fila que se subio pero aun no se borra aparece en los dos lados: se cuenta una vez
            val pendingOnly = pending.filter { it.documentId() !in uploaded.keys }
            (uploaded.values + pendingOnly.map { it.toDomain() }).sortedBy { it.timestamp }
        }
    }

    //las ya subidas, por id de documento; sin internet firestore responde con su cache del telefono
    private suspend fun uploadedInteractionsSince(fromMillis: Long): Map<String, ToolInteractionModel> {
        val userId = auth?.currentUser?.uid ?: return emptyMap()
        val store = firestore ?: return emptyMap()
        val collection = store.collection("users").document(userId).collection("tool_interactions")
        val query = if (fromMillis > 0L) collection.whereGreaterThanOrEqualTo("timestamp", Date(fromMillis)) else collection
        val snapshot = runCatching { withTimeout(SERVER_READ_TIMEOUT_MS) { query.get().await() } }.getOrNull()
            ?: runCatching { query.get(Source.CACHE).await() }.getOrNull()
            ?: return emptyMap()
        //un documento con otra forma (por ejemplo uno escrito a mano) se salta
        return snapshot.documents.mapNotNull { document ->
            runCatching { document.toObject(ToolInteractionDto::class.java) }.getOrNull()
                ?.let { document.id to it.toDomain() }
        }.toMap()
    }

    override suspend fun recordCompletion(
        toolId: String,
        durationSeconds: Int,
        interactionType: String,
        timestamp: Long,
        complete: Boolean
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val id = toolRecordDao.insertInteraction(
                ToolInteractionEntity(
                    userId = auth?.currentUser?.uid ?: "anonymous",
                    toolId = toolId,
                    duration = durationSeconds,
                    interactionType = interactionType,
                    complete = complete,
                    timestamp = Date(timestamp),
                    isSynced = false
                )
            )
            onInteractionSaved()
            id
        }
    }

    companion object {
        //si el servidor no contesta en este tiempo se usa la cache de firestore
        private const val SERVER_READ_TIMEOUT_MS = 5_000L
    }
}
