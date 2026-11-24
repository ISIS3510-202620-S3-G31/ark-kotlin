package com.moviles.ark.domain.models

import com.moviles.ark.domain.exceptions.NotEnoughSpaceException

//reglas del jarro: cuantas galletas se dibujan y cuales salen hoy
object CookieJar {
    //el jarro dibuja maximo 10 galletas; el resto se ve en el listado
    const val CAPACITY = 10

    //las 10 del dia: sorteo con semilla uid + fecha, asi el mismo dia salen las mismas aunque se cierre la app
    //o se cambie de telefono. cada galleta recibe un numero al azar fijo para ese dia (semilla + su id) y salen
    //las 10 con el numero mas bajo; comer o crear una galleta no cambia el sorteo de las demas
    //dayKey es la fecha del telefono en formato yyyy-MM-dd
    fun dailyPick(cookies: List<CookieModel>, userId: String, dayKey: String): List<CookieModel> {
        val daySeed = mix("$userId|$dayKey".hashCode().toLong())
        return cookies
            .filter { it.isActive }
            .sortedWith(compareBy<CookieModel> { mix(daySeed xor mix(it.id.hashCode().toLong())) }.thenBy { it.id })
            .take(CAPACITY)
    }

    //revuelve los bits de un numero (splitmix64): dos entradas parecidas dan salidas que no se parecen,
    //asi ids casi iguales no quedan juntos en el sorteo
    private fun mix(value: Long): Long {
        var z = value + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }
}

//textos de los avisos (snackbars) del cookie jar; la app esta en ingles
object CookieMessages {
    //crear con red
    const val ADDED = "Cookie added to your jar 🍪"
    //crear sin red
    const val SAVED_OFFLINE = "Cookie saved on this phone. It will upload when you're back online"
    const val NOT_ENOUGH_SPACE = NotEnoughSpaceException.MESSAGE
    const val EATEN = "Cookie eaten 🍪"
    const val UNDO = "Undo"
    const val RETRY = "Retry"

    //conectividad eventual: se perdio la red con la herramienta abierta y cuando vuelve
    const val WENT_OFFLINE = "You're offline. Your cookie jar still works; gifts and friends will wait"
    const val BACK_ONLINE = "Back online. Syncing your cookies…"
    //ya no queda nada por subir
    const val ALL_BACKED_UP = "All your cookies are backed up ✓"
    //borrar para siempre sin red
    const val DELETED_OFFLINE = "Deleted from this phone. It will be deleted from the cloud when you're back online"
    //la imagen no esta en el telefono y no hay red
    const val PICTURE_NEEDS_INTERNET = "Connect to the internet to see this picture"
    //fallo la descarga de la imagen
    const val PICTURE_FAILED = "Couldn't load the picture. Check your connection"
    //telefono nuevo (nunca bajo sus galletas) sin red
    const val COOKIES_IN_CLOUD = "Your cookies are in the cloud. Connect to the internet to bring them here"
    //la bajada de cambios fallo con red (red lenta)
    const val SYNC_FAILED = "Couldn't sync right now. We'll keep trying"

    //en el detalle, en lugar de la imagen que no esta en el telefono
    const val PICTURE_WHEN_ONLINE = "The picture will show when you're back online"
    const val PICTURE_NOT_LOADED = "Couldn't load the picture"

    //llegaron galletas de otro telefono de la misma cuenta
    fun arrivedFromOtherDevices(count: Int) =
        if (count == 1) "1 cookie arrived from your other devices" else "$count cookies arrived from your other devices"

    //dos telefonos crearon el mismo titulo sin red
    fun existedOnTwoPhones(title: String, finalTitle: String) = "\"$title\" existed on two phones. One is now \"$finalTitle\""

    //arriba del jarro mientras haya galletas sin subir
    fun waitingToUpload(count: Int) =
        if (count == 1) "⏳ 1 cookie waiting to upload" else "⏳ $count cookies waiting to upload"

    //el titulo ya existia y se guardo con sufijo
    fun renamed(typedTitle: String, finalTitle: String) = "\"$typedTitle\" already exists. Saved as \"$finalTitle\""

    //una galleta comida volvio al jarro
    fun backInJar(title: String) = "\"$title\" is back in the jar"

    //aviso antes de guardar cuando el titulo se va a guardar con sufijo
    fun willBeSavedAs(finalTitle: String) = "Will be saved as \"$finalTitle\""
}
