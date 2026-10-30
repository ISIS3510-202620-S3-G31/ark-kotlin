package com.moviles.ark.domain.models

import com.moviles.ark.domain.composite.MoodComponent

//modelo de dominio que representa un registro de check-in emocional del usuario con coordenadas gps
data class MoodCheckInModel(
    val mood: MoodComponent,
    val note: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    //ciudad donde se hizo el check-in; null sin ubicacion o si no habia red para pedir el nombre
    val cityName: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    //reglas de negocio
    fun isValidMood(): Boolean {
        return mood.getEmotions().isNotEmpty()
    }
    fun isValidNote(): Boolean {
        return note.length <= MAX_NOTE_LENGTH
    }
    fun hasLocation(): Boolean {
        return latitude != null && longitude != null
    }

    //antes el feedback de una herramienta tambien guardaba un check-in con esta nota; esos no son el check-in del dia
    fun isPostToolFeedback(): Boolean = isPostToolNote(note)

    //texto del chip de ubicacion: solo la ciudad, nunca las coordenadas
    fun locationLabel(): String = when {
        !cityName.isNullOrBlank() -> cityName
        hasLocation() -> LOCATION_SAVED_LABEL
        else -> NO_LOCATION_LABEL
    }

    //el primer error del check-in, o null si se puede guardar
    fun validationError(): String? = when {
        !isValidMood() -> SELECT_EMOTION_ERROR
        !isValidNote() -> "Note cannot exceed $MAX_NOTE_LENGTH characters"
        else -> null
    }

    companion object {
        //cuando faltan estos caracteres o menos el contador de la nota se resalta
        const val NOTE_WARNING_REMAINING = 40
        const val SELECT_EMOTION_ERROR = "Please select at least one emotion"
        const val ALREADY_CHECKED_IN_ERROR = "You have already completed your daily check-in today"

        //un solo check-in por dia de calendario en la hora local: cuenta desde la medianoche de hoy
        fun startOfToday(now: Long = System.currentTimeMillis()): Long = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        //se corta lo que pase del limite, por ejemplo al pegar un texto largo
        fun limitNote(note: String): String = note.take(MAX_NOTE_LENGTH)

        fun remainingNoteChars(note: String): Int = (MAX_NOTE_LENGTH - note.length).coerceAtLeast(0)

        fun isNoteNearLimit(note: String): Boolean = remainingNoteChars(note) <= NOTE_WARNING_REMAINING

        //nota con la que el feedback viejo guardaba su check-in; room y firestore la usan para no contarlos
        const val POST_TOOL_NOTE_PREFIX = "Post-tool feedback"
        const val LOCATION_SAVED_LABEL = "Location saved"
        const val NO_LOCATION_LABEL = "No location"

        fun isPostToolNote(note: String): Boolean = note.startsWith(POST_TOOL_NOTE_PREFIX)

        //estandar de 280 caracteres para micro-journaling: evita fatiga cognitiva en el usuario
        //y previene desbordamientos de payload en los documentos de room y firestore
        const val MAX_NOTE_LENGTH = 280
    }
}


//alias de retrocompatibilidad con codigo que use CheckInModel
typealias CheckInModel = MoodCheckInModel
