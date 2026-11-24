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
