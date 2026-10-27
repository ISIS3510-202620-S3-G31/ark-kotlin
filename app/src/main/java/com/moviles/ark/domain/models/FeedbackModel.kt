package com.moviles.ark.domain.models

// Calificacion y comentario que deja el usuario al terminar una herramienta (#21, #166)
// Modelo del dominio desacoplado de Room (FeedbackEntity) y de Firestore (FeedbackDto)
// Centraliza todas las reglas de negocio e invariantes para asegurar la integridad de los datos
data class FeedbackModel(
    // Identificador local autogenerado por Room; 0 si aun no ha sido persistido
    val id: Long = 0L,
    // ID de la interaccion local calificada (ToolInteractionEntity.id); null si no se asocio
    val interactionId: Long? = null,
    // ID de la herramienta que se califica (por ejemplo "custom_breathing", "scream_tank")
    val toolId: String,
    // Calificacion de 1 a 5 estrellas
    val rating: Int,
    // Comentario u observacion opcional del usuario
    val comment: String = "",
    // Momento de registro en milisegundos
    val createdAt: Long = System.currentTimeMillis(),
    // Indicador de si ya fue sincronizado con Firestore
    val isSynced: Boolean = false
) {
    init {
        require(toolId.isNotBlank()) { "El identificador de la herramienta (toolId) no puede estar vacío" }
        require(rating in MIN_RATING..MAX_RATING) {
            "La calificación debe estar entre $MIN_RATING y $MAX_RATING estrellas (recibido: $rating)"
        }
        require(createdAt > 0L) { "La fecha de creación createdAt debe ser mayor a 0" }
        interactionId?.let {
            require(it > 0L) { "El interactionId debe ser un identificador válido mayor a 0" }
        }
    }

    // Regla de negocio: comentario saneado (sin espacios en blanco sobrantes)
    val sanitizedComment: String get() = comment.trim()

    // Regla de negocio: indica si la calificacion es positiva (4 o 5 estrellas)
    val isPositiveRating: Boolean get() = rating >= 4

    companion object {
        const val MIN_RATING = 1
        const val MAX_RATING = 5

        // Valida si un valor de rating se encuentra dentro de las reglas de negocio
        fun isValidRating(rating: Int): Boolean = rating in MIN_RATING..MAX_RATING

        // Fabrica que normaliza y sanea las entradas del usuario antes de crear el FeedbackModel
        fun create(
            toolId: String,
            rating: Int,
            comment: String = "",
            interactionId: Long? = null,
            createdAt: Long = System.currentTimeMillis()
        ): Result<FeedbackModel> = runCatching {
            FeedbackModel(
                toolId = toolId.trim(),
                rating = rating,
                comment = comment.trim(),
                interactionId = if (interactionId != null && interactionId > 0L) interactionId else null,
                createdAt = if (createdAt > 0L) createdAt else System.currentTimeMillis()
            )
        }
    }
}
