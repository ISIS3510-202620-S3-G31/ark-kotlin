package com.moviles.ark.domain.models

//una imagen de galleta guardada en el telefono, con lo que hace falta para decidir si se puede desalojar
data class StoredCookieImage(
    val cookieId: String,
    val sizeBytes: Long,
    val lastOpenedAt: Long,
    //false si todavia no se ha subido: borrarla la perderia para siempre
    val isSynced: Boolean,
)

//presupuesto de imagenes del cookie jar en el telefono: android no pone tope a filesDir, asi que lo pone la app
//50 mb normalmente (~125 imagenes de ~400 kb) y 15 mb si al telefono le quedan menos de 500 mb libres
//al pasarse se desaloja por lru segun lastOpenedAt: primero la menos usada, no la mas vieja
object CookieImageBudget {
    const val NORMAL_BUDGET_BYTES = 50L * 1024 * 1024
    const val LOW_SPACE_BUDGET_BYTES = 15L * 1024 * 1024
    const val LOW_SPACE_THRESHOLD_BYTES = 500L * 1024 * 1024

    fun budgetFor(usableSpaceBytes: Long): Long =
        if (usableSpaceBytes < LOW_SPACE_THRESHOLD_BYTES) LOW_SPACE_BUDGET_BYTES else NORMAL_BUDGET_BYTES

    //las imagenes que hay que borrar del telefono para quedar dentro del presupuesto, en orden
    //nunca las que no se han subido ni las del jarro de hoy; si con las demas no alcanza, se queda por encima
    fun evictions(
        images: List<StoredCookieImage>,
        usedBytes: Long,
        budgetBytes: Long,
        protectedIds: Set<String>,
    ): List<StoredCookieImage> {
        if (usedBytes <= budgetBytes) return emptyList()
        var remaining = usedBytes
        val result = mutableListOf<StoredCookieImage>()
        for (image in images.filter { it.isSynced && it.cookieId !in protectedIds }.sortedBy { it.lastOpenedAt }) {
            if (remaining <= budgetBytes) break
            result += image
            remaining -= image.sizeBytes
        }
        return result
    }
}
