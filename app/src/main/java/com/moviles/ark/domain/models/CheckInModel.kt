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
        return note.length <= 280
    }
    fun hasLocation(): Boolean {
        return latitude != null && longitude != null
    }
}

//alias para compatibilidad
typealias MoodCheckIn = CheckInModel
