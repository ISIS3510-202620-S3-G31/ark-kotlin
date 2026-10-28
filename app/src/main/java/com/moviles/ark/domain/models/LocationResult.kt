package com.moviles.ark.domain.models

//por que hay o no hay coordenadas; la pantalla muestra un texto distinto en cada caso
enum class LocationStatus {
    //coordenadas tomadas ahora
    CAPTURED,
    //sin permiso o con la ubicacion apagada en ajustes
    DISABLED,
    //ubicacion encendida pero el gps no dio una posicion a tiempo
    UNAVAILABLE
}

//coordenadas en null cuando no se pudo leer; el check-in se guarda igual
data class LocationResult(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val status: LocationStatus
)
