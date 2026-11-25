package com.moviles.ark.domain.exceptions

import java.io.IOException

//no hay espacio en el telefono para guardar la imagen de una galleta; la galleta no se crea
class NotEnoughSpaceException(cause: Throwable? = null) : IOException(MESSAGE, cause) {
    companion object {
        const val MESSAGE = "Not enough space on this phone to save the picture"
    }
}

//la imagen elegida no se pudo leer (archivo danado o que no es una imagen)
class InvalidCookieImageException(cause: Throwable? = null) : IOException(MESSAGE, cause) {
    companion object {
        const val MESSAGE = "Couldn't read that picture. Try another one"
    }
}

//amigos y regalos: la red se cayo o no respondio a tiempo (timeout); no se cambio nada en la nube
class ConnectionLostException(cause: Throwable? = null) : IOException("Connection lost", cause)

//el username ya es de otra cuenta
class UsernameTakenException(val username: String) : IOException("Username taken")

//nadie tiene ese username (o esa persona bloqueo a quien busca: se responde igual)
class UsernameNotFoundException : IOException("Username not found")

//el regalo ya no esta en la nube: se acepto, se rechazo, se cancelo o vencio
class GiftUnavailableException : IOException("Gift no longer available")
