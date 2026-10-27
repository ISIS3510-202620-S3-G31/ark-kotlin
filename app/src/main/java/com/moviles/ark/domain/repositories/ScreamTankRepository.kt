package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.DecibelReading
import com.moviles.ark.domain.models.ScreamSession
import kotlinx.coroutines.flow.Flow

//repositorio de dominio del scream tank
interface ScreamTankRepository {
    //true si el usuario ya dio el permiso del microfono
    fun hasMicrophonePermission(): Boolean

    //volumen del microfono en tiempo real; escucha mientras alguien recolecta el flow y no guarda audio
    fun microphoneLevels(): Flow<DecibelReading>

    //guarda en el telefono la sesion como ToolInteraction; funciona sin internet
    suspend fun saveSession(session: ScreamSession): Result<Unit>
}
