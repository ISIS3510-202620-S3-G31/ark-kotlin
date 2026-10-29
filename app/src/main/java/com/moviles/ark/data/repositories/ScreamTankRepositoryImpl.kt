package com.moviles.ark.data.repositories

import com.google.firebase.auth.FirebaseAuth
import com.moviles.ark.data.local.daos.ToolRecordDao
import com.moviles.ark.data.local.entities.toEntity
import com.moviles.ark.data.local.sensors.MicrophoneLevelMeter
import com.moviles.ark.domain.models.DecibelReading
import com.moviles.ark.domain.models.ScreamSession
import com.moviles.ark.domain.repositories.ScreamTankRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

//scream tank: el volumen sale del microfono en memoria y la sesion se guarda en room como ToolInteractionModel
class ScreamTankRepositoryImpl(
    private val microphoneLevelMeter: MicrophoneLevelMeter,
    private val toolRecordDao: ToolRecordDao,
    private val auth: FirebaseAuth
) : ScreamTankRepository {

    override fun hasMicrophonePermission(): Boolean = microphoneLevelMeter.hasPermission()

    override fun microphoneLevels(): Flow<DecibelReading> = microphoneLevelMeter.levels()

    override suspend fun saveSession(session: ScreamSession): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            //queda con isSynced = false y el SyncManager la sube a firestore cuando vuelva el internet
            val userId = auth.currentUser?.uid ?: "anonymous"
            toolRecordDao.insertInteraction(session.toInteraction(userId).toEntity())
            Unit
        }
    }
}
