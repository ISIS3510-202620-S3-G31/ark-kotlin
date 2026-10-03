package com.moviles.ark.data.remote.dtos

//dto para serializar cada emocion con su respectiva intensidad
data class EmotionDto(
    val name: String = "",
    val intensity: Int = 3
)

//dto para transferir y serializar el check-in en firebase firestore
data class CheckInDto(
    val userId: String = "",
    val timestamp: Long = 0L,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val emotions: List<EmotionDto> = emptyList(),
    val note: String = ""
)
