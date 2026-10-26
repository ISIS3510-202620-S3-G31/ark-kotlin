package com.moviles.ark.domain.models

// Calificacion y comentario que deja el usuario al terminar una herramienta (#21, #166)
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
)
