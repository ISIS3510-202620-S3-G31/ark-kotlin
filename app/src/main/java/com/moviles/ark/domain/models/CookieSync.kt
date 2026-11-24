package com.moviles.ark.domain.models

//reglas para juntar las galletas del telefono con las de la nube (varios telefonos de la misma cuenta)
object CookieSync {

    //last write wins: la version de la nube reemplaza a la del telefono solo si es mas nueva
    fun remoteWins(localUpdatedAt: Long, remoteUpdatedAt: Long): Boolean = remoteUpdatedAt > localUpdatedAt

    //una fila que ya estaba sincronizada y ya no esta en la nube se borro desde otro telefono: se borra aqui tambien
    //si tiene cambios sin subir (o su borrado en la nube sigue pendiente) no se toca
    fun deletedInOtherPhone(wasSynced: Boolean, existsInCloud: Boolean, remoteDeletePending: Boolean): Boolean =
        wasSynced && !existsInCloud && !remoteDeletePending

    //dos telefonos sin red crearon el mismo titulo: la galleta mas nueva recibe el sufijo
    //true si la que llega de la nube es la mas nueva (el empate se decide por id para que los dos telefonos elijan igual)
    fun incomingIsNewer(incomingCreatedAt: Long, incomingId: String, localCreatedAt: Long, localId: String): Boolean {
        if (incomingCreatedAt > localCreatedAt) return true
        if (incomingCreatedAt < localCreatedAt) return false
        //se crearon en el mismo milisegundo: gana el id mayor
        return incomingId > localId
    }
}

//resultado de bajar los cambios de la nube al abrir el jarro
data class CookieRefreshResult(
    //galletas nuevas que llegaron de otros telefonos
    val arrived: Int = 0,
    //titulos repetidos entre dos telefonos: (titulo original, titulo con sufijo)
    val renamed: List<Pair<String, String>> = emptyList(),
    //borradas aqui porque se borraron en otro telefono
    val removed: Int = 0,
)

//resultado de subir las galletas pendientes
data class CookieUploadResult(
    val synced: Int = 0,
    val failed: Int = 0,
)
