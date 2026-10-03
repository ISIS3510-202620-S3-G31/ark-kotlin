package com.moviles.ark.domain.repositories

//interfaz del repositorio para resincronizar datos pendientes cuando vuelve la conexion a internet (#33)
interface SyncRepository {
    //sube a firebase las fotos e interacciones que se guardaron localmente sin conexion
    suspend fun syncPendingData(): Result<Int>
}
