package com.moviles.ark.domain.exceptions

import java.io.IOException

/**
 * Excepcion que se lanza cuando se intenta autenticar sin conexion activa a internet o en modo avion (#141)
 *
 * @param message Mensaje descriptivo de la falla de conectividad
 * @param isAirplaneMode Indica si el dispositivo se encuentra con modo avion activado
 * @param cause Causa original del error de red (por ejemplo FirebaseNetworkException)
 */
class NetworkUnavailableException(
    message: String = "Active server connectivity is required for authentication.",
    val isAirplaneMode: Boolean = false,
    cause: Throwable? = null
) : IOException(message, cause)
