package com.moviles.ark.domain.models

import java.util.TimeZone

//ultima recomendacion que se calculo con internet; se guarda en disco para usarla sin conexion
//solo guarda el orden de los ids, asi sirve aunque el catalogo cambie despues
data class CachedRecommendation(
    val reason: RecommendationReason,
    val toolIds: List<String>,
    val createdAt: Long
) {
    //solo vale el mismo dia (hora local): el animo de ayer no sirve para recomendar hoy
    //y si despues de guardarla hubo un check-in nuevo, ese check-in manda
    fun isFresh(now: Long, latestCheckInAt: Long? = null, zone: TimeZone = TimeZone.getDefault()): Boolean =
        toolIds.isNotEmpty() && dayOf(createdAt, zone) == dayOf(now, zone) &&
            (latestCheckInAt == null || latestCheckInAt <= createdAt)

    //ordena el catalogo actual como estaba guardado; las herramientas nuevas quedan al final en su orden normal
    fun applyTo(toolModels: List<ToolModel>): List<ToolModel> {
        val position = toolIds.withIndex().associate { (index, id) -> id to index }
        return toolModels.sortedBy { position[it.id] ?: Int.MAX_VALUE }
    }

    private fun dayOf(millis: Long, zone: TimeZone): Long = (millis + zone.getOffset(millis)) / DAY_MILLIS

    companion object {
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}
