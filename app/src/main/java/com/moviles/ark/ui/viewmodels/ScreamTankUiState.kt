package com.moviles.ark.ui.viewmodels

import com.moviles.ark.domain.models.DecibelMeter

//estado del permiso del microfono
enum class MicPermissionState {
    //todavia no se ha preguntado en esta pantalla
    UNKNOWN,
    GRANTED,
    //lo nego pero se puede volver a pedir
    DENIED,
    //lo nego con "no volver a preguntar": solo se puede activar en ajustes
    PERMANENTLY_DENIED
}

//estado de la pantalla del scream tank
data class ScreamTankUiState(
    val permission: MicPermissionState = MicPermissionState.UNKNOWN,
    val showPermissionDialog: Boolean = false,
    val isListening: Boolean = false,
    //true desde que el microfono escucho por primera vez en esta sesion
    val hasStarted: Boolean = false,
    //volumen actual de 0 a 100 y en dbfs
    val currentLevel: Int = 0,
    val currentDbfs: Double = DecibelMeter.MIN_DBFS,
    //el mas fuerte de la sesion, de 0 a 100
    val peakLevel: Int = 0,
    //segundos escuchando, sin contar las pausas
    val elapsedSeconds: Int = 0,
    val errorMessage: String? = null
)
