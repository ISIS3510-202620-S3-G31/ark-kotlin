package com.moviles.ark.ui.viewmodels

import com.moviles.ark.domain.models.WishModel
import kotlin.random.Random

//lo que se dibuja en el cielo despues de acomodar los faroles
data class SkyLayout(
    //faroles que se ven, con su lugar
    val lanterns: List<SkyLantern>,
    //faroles que se acaban de ir del cielo; la pantalla los desvanece
    val leavingLanterns: List<SkyLantern>
)

//decide que faroles se ven en el cielo y donde va cada uno
//1. maximo 20 faroles: primero los encendidos y luego los apagados mas recientes
//2. cada farol va en un lugar al azar (que sale de su id) sin tocar a los demas
//3. un farol que ya tiene lugar no se mueve; solo los nuevos buscan lugar
//es codigo de kotlin normal (sin android), corre en Dispatchers.Default
class SkyLanternPlacer(
    //cuantos faroles caben en el cielo
    private val maxLanterns: Int = WishModel.MAX_SKY_LANTERNS
) {
    //lugar que ya se le dio a cada farol mientras la herramienta esta abierta
    //asi, cuando llega un farol nuevo, los demas no cambian de lugar
    private val rememberedLanterns = mutableMapOf<String, SkyLantern>()

    //tamano del cielo en dp; lo manda la pantalla porque cada telefono es distinto
    private var skyWidthDp = DEFAULT_SKY_WIDTH_DP
    private var skyHeightDp = DEFAULT_SKY_HEIGHT_DP

    //la pantalla midio el cielo; si cambio de tamano (por ejemplo, girar el telefono) se acomoda todo de nuevo
    fun setSkySize(widthDp: Float, heightDp: Float) {
        if (widthDp == skyWidthDp && heightDp == skyHeightDp) {
            return
        }
        skyWidthDp = widthDp
        skyHeightDp = heightDp
        rememberedLanterns.clear()
    }

    //acomoda los faroles
    //newestFirstIds: ids de todos los deseos, del mas nuevo al mas viejo
    //litIds: ids de los faroles encendidos (los que estan en la cache)
    fun placeLanterns(newestFirstIds: List<String>, litIds: Set<String>): SkyLayout {
        //1. que faroles se ven
        val visibleIds = chooseVisibleIds(newestFirstIds, litIds)

        //2. los que tenian lugar y ya no se ven, se van (y dejan libre su espacio)
        val leavingLanterns = mutableListOf<SkyLantern>()
        val idsToForget = mutableListOf<String>()
        for (wishId in rememberedLanterns.keys) {
            if (!visibleIds.contains(wishId)) {
                leavingLanterns.add(rememberedLanterns.getValue(wishId))
                idsToForget.add(wishId)
            }
        }
        for (wishId in idsToForget) {
            rememberedLanterns.remove(wishId)
        }

        //3. los que ya tenian lugar se quedan donde estaban
        val placedLanterns = mutableListOf<SkyLantern>()
        for (wishId in visibleIds) {
            val rememberedLantern = rememberedLanterns[wishId]
            if (rememberedLantern != null) {
                placedLanterns.add(rememberedLantern)
            }
        }

        //4. los nuevos buscan un lugar libre
        //primero los encendidos, porque siempre se tienen que ver y asi encuentran el cielo mas vacio
        //despues los apagados; en cada grupo, del mas viejo al mas nuevo
        val newLitIds = mutableListOf<String>()
        val newUnlitIds = mutableListOf<String>()
        for (index in visibleIds.size - 1 downTo 0) {
            val wishId = visibleIds[index]
            if (!rememberedLanterns.containsKey(wishId)) {
                if (litIds.contains(wishId)) {
                    newLitIds.add(wishId)
                } else {
                    newUnlitIds.add(wishId)
                }
            }
        }
        for (wishId in newLitIds) {
            placeLitLantern(wishId, visibleIds, litIds, placedLanterns, leavingLanterns)
        }
        for (wishId in newUnlitIds) {
            placeUnlitLantern(wishId, placedLanterns)
        }

        //5. se devuelven en el mismo orden de visibleIds
        //si un farol apagado no encontro espacio, no esta en rememberedLanterns y no se dibuja
        val lanterns = mutableListOf<SkyLantern>()
        for (wishId in visibleIds) {
            val lantern = rememberedLanterns[wishId]
            if (lantern != null) {
                lanterns.add(lantern)
            }
        }
        return SkyLayout(lanterns = lanterns, leavingLanterns = leavingLanterns)
    }

    //un farol encendido nuevo siempre se tiene que ver
    //si no cabe sin tocar a otro (pasa casi nunca, solo en pantallas pequenas),
    //se saca del cielo el farol apagado mas viejo para hacerle espacio y se intenta otra vez
    private fun placeLitLantern(
        wishId: String,
        visibleIds: List<String>,
        litIds: Set<String>,
        placedLanterns: MutableList<SkyLantern>,
        leavingLanterns: MutableList<SkyLantern>
    ) {
        var newLantern = findFreeSpot(wishId, placedLanterns)
        while (newLantern == null) {
            val removedLantern = removeOldestUnlitLantern(visibleIds, litIds, placedLanterns)
            if (removedLantern == null) {
                //ya no hay apagados que sacar: se queda en su primer lugar al azar
                newLantern = firstSpot(wishId)
            } else {
                leavingLanterns.add(removedLantern)
                newLantern = findFreeSpot(wishId, placedLanterns)
            }
        }
        rememberedLanterns[wishId] = newLantern
        placedLanterns.add(newLantern)
    }

    //un farol apagado nuevo: si no cabe sin tocar a otro (pasa casi nunca), no se dibuja
    private fun placeUnlitLantern(wishId: String, placedLanterns: MutableList<SkyLantern>) {
        val newLantern = findFreeSpot(wishId, placedLanterns)
        if (newLantern != null) {
            rememberedLanterns[wishId] = newLantern
            placedLanterns.add(newLantern)
        }
    }

    //saca del cielo el farol apagado mas viejo que se esta viendo y lo devuelve; null si no hay ninguno
    //visibleIds tiene los apagados al final, del mas nuevo al mas viejo, asi que se recorre de atras hacia adelante
    private fun removeOldestUnlitLantern(
        visibleIds: List<String>,
        litIds: Set<String>,
        placedLanterns: MutableList<SkyLantern>
    ): SkyLantern? {
        for (index in visibleIds.size - 1 downTo 0) {
            val wishId = visibleIds[index]
            val lantern = rememberedLanterns[wishId]
            if (!litIds.contains(wishId) && lantern != null) {
                rememberedLanterns.remove(wishId)
                placedLanterns.remove(lantern)
                return lantern
            }
        }
        return null
    }

    //maximo 20: primero los encendidos (siempre se tienen que ver) y luego los apagados mas recientes
    private fun chooseVisibleIds(newestFirstIds: List<String>, litIds: Set<String>): List<String> {
        val chosenIds = mutableListOf<String>()

        //primero los encendidos
        for (wishId in newestFirstIds) {
            if (litIds.contains(wishId) && chosenIds.size < maxLanterns) {
                chosenIds.add(wishId)
            }
        }

        //despues se completa con los apagados, del mas nuevo al mas viejo
        for (wishId in newestFirstIds) {
            if (!litIds.contains(wishId) && chosenIds.size < maxLanterns) {
                chosenIds.add(wishId)
            }
        }
        return chosenIds
    }

    //busca un lugar al azar donde el farol no toque a ninguno de los que ya estan; null si no encuentra
    //el azar sale del id del deseo: el mismo deseo siempre prueba los mismos lugares en el mismo orden
    private fun findFreeSpot(wishId: String, placedLanterns: List<SkyLantern>): SkyLantern? {
        val random = Random(wishId.hashCode())
        //entre 2 y 3.5 segundos para subir y bajar flotando
        val floatDuration = 2000 + random.nextInt(1500)

        //primer intento: el mismo lugar que da firstSpot
        var candidate = SkyLantern(
            wishId = wishId,
            x = random.nextFloat(),
            y = random.nextFloat(),
            floatDurationMillis = floatDuration
        )

        var attempts = 1
        while (touchesAnyLantern(candidate, placedLanterns) && attempts < MAX_ATTEMPTS) {
            //toca a otro: se prueba otro lugar al azar
            candidate = candidate.copy(x = random.nextFloat(), y = random.nextFloat())
            attempts++
        }

        if (touchesAnyLantern(candidate, placedLanterns)) {
            //no hubo espacio despues de muchos intentos
            return null
        }
        return candidate
    }

    //primer lugar al azar del farol (sale de su id) y su ritmo para flotar
    private fun firstSpot(wishId: String): SkyLantern {
        val random = Random(wishId.hashCode())
        //entre 2 y 3.5 segundos para subir y bajar flotando
        val floatDuration = 2000 + random.nextInt(1500)
        return SkyLantern(
            wishId = wishId,
            x = random.nextFloat(),
            y = random.nextFloat(),
            floatDurationMillis = floatDuration
        )
    }

    //true si el farol toca a alguno de los que ya estan
    private fun touchesAnyLantern(lantern: SkyLantern, placedLanterns: List<SkyLantern>): Boolean {
        for (placedLantern in placedLanterns) {
            if (touches(lantern, placedLantern)) {
                return true
            }
        }
        return false
    }

    //true si dos faroles se tocan
    //cada farol es un rectangulo: desde su esquina de arriba a la izquierda, con su ancho y su alto
    //al alto se le suma lo que sube y baja flotando, y a los dos un pequeno espacio de separacion
    private fun touches(first: SkyLantern, second: SkyLantern): Boolean {
        val firstLeft = first.x * skyWidthDp
        val firstTop = first.y * skyHeightDp
        val firstWidth = first.widthDp() + GAP_DP
        val firstHeight = first.heightDp() + FLOAT_DP + GAP_DP

        val secondLeft = second.x * skyWidthDp
        val secondTop = second.y * skyHeightDp
        val secondWidth = second.widthDp() + GAP_DP
        val secondHeight = second.heightDp() + FLOAT_DP + GAP_DP

        //se tocan si se cruzan de lado a lado y tambien de arriba a abajo
        val crossSideways = firstLeft < secondLeft + secondWidth && secondLeft < firstLeft + firstWidth
        val crossUpDown = firstTop < secondTop + secondHeight && secondTop < firstTop + firstHeight
        return crossSideways && crossUpDown
    }

    companion object {
        //cuantos lugares al azar se prueban por farol antes de rendirse
        private const val MAX_ATTEMPTS = 300
        //cuanto sube y baja un farol al flotar, en dp (el mismo de la pantalla)
        private const val FLOAT_DP = 8f
        //espacio minimo entre dos faroles, en dp
        private const val GAP_DP = 6f
        //tamano del cielo mientras la pantalla todavia no lo ha medido (un telefono mediano)
        private const val DEFAULT_SKY_WIDTH_DP = 312f
        private const val DEFAULT_SKY_HEIGHT_DP = 300f
    }
}
