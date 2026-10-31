package com.moviles.ark.domain.repositories

//interfaz del repositorio para resincronizar datos pendientes cuando vuelve la conexion a internet
interface SyncRepository {
    //sube a firebase las fotos e interacciones que se guardaron localmente sin conexion
    suspend fun syncPendingData(): Result<Int>

    //cuantas cosas del usuario con sesion siguen guardadas solo en el telefono (para avisar antes de cerrar sesion)
    suspend fun pendingCount(): Int = 0
}
