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

    //el titulo ya existia y se guardo con sufijo
    fun renamed(typedTitle: String, finalTitle: String) = "\"$typedTitle\" already exists. Saved as \"$finalTitle\""

    //una galleta comida volvio al jarro
    fun backInJar(title: String) = "\"$title\" is back in the jar"

    //aviso antes de guardar cuando el titulo se va a guardar con sufijo
    fun willBeSavedAs(finalTitle: String) = "Will be saved as \"$finalTitle\""
}
