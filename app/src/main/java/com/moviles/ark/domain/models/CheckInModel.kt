package com.moviles.ark.domain.models

import com.moviles.ark.domain.composite.MoodComponent

//modelo de dominio que representa un registro de check-in emocional del usuario con coordenadas gps
data class CheckInModel(
    val mood: MoodComponent,
    val note: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
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

    companion object {
        //estandar de 280 caracteres para micro-journaling: evita fatiga cognitiva en el usuario
        //y previene desbordamientos de payload en los documentos de room y firestore
        const val MAX_NOTE_LENGTH = 280
    }
}
