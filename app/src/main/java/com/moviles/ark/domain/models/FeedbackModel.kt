package com.moviles.ark.domain.models

// Calificacion y comentario que deja el usuario al terminar una herramienta
// Modelo del dominio desacoplado de Room (FeedbackEntity) y de Firestore (FeedbackDto)
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
    //reglas de negocio
    fun isValidRating(): Boolean = ratingError(rating) == null

    companion object {
        const val MIN_RATING = 1
        const val MAX_RATING = 5
        const val MAX_COMMENT_LENGTH = 280

        //la calificacion es de 1 a 5 estrellas; 0 significa que todavia no se eligio
        fun ratingError(rating: Int): String? =
            if (rating in MIN_RATING..MAX_RATING) null else "Please select a rating from $MIN_RATING to $MAX_RATING stars"
    }
}
