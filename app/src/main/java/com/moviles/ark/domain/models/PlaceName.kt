package com.moviles.ark.domain.models

import java.text.Normalizer
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

//nombre del lugar de un check-in, siempre con el mismo formato: "Chapinero, Bogotá, CO" (localidad, ciudad, pais)
//aqui viven las reglas de formato, de comparacion y de cuando dos nombres son el mismo lugar
object PlaceName {
    //tope del texto guardado en room y firestore
    const val MAX_LENGTH = 100
    private const val SEPARATOR = ", "

    //arma el nombre con las partes que traiga el geocoder; las que vienen null, vacias o "null" se saltan
    //la ciudad es locality y, si no hay, el municipio o el departamento; el pais va con su codigo de dos letras
    //una parte repetida (localidad = ciudad) se escribe una vez, con la forma de la parte mas general
    fun format(
        subLocality: String?,
        locality: String?,
        subAdminArea: String?,
        adminArea: String?,
        countryCode: String?,
        countryName: String? = null
    ): String? {
        val city = clean(locality) ?: clean(subAdminArea) ?: clean(adminArea)
        val country = clean(countryCode)?.uppercase(Locale.ROOT) ?: clean(countryName)
        val parts = listOfNotNull(clean(subLocality), city, country)
            .reversed()
            .distinctBy { key(it) }
            .reversed()
        if (parts.isEmpty()) return null
        return parts.joinToString(SEPARATOR).take(MAX_LENGTH).trimEnd(' ', ',')
    }

    //clave para comparar: minusculas, sin tildes y sin espacios de sobra; "Bogota" y " Bogotá" dan lo mismo
    fun key(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase(Locale.ROOT)
            .replace(SPACES, " ")
            .replace(SPACES_AROUND_COMMA, ",")
            .trim()

    fun isSamePlace(first: String?, second: String?): Boolean {
        val a = clean(first) ?: return false
        val b = clean(second) ?: return false
        return key(a) == key(b)
    }

    //lo que se guarda: null en vez de "", "null" o espacios; si ya hay un nombre guardado del mismo lugar
    //se reusa ese mismo texto, asi un lugar queda escrito de una sola forma y no cuenta como dos
    fun canonical(candidate: String?, known: List<String>): String? {
        val name = clean(candidate)?.take(MAX_LENGTH) ?: return null
        return known.firstOrNull { isSamePlace(it, name) }?.let { clean(it) } ?: name
    }

    //texto sin espacios de sobra; null si no dice nada
    fun clean(text: String?): String? {
        val trimmed = text?.replace(SPACES, " ")?.trim() ?: return null
        if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true)) return null
        return trimmed
    }

    private val DIACRITICS = Regex("\\p{Mn}+")
    private val SPACES = Regex("\\s+")
    private val SPACES_AROUND_COMMA = Regex("\\s*,\\s*")
}

//ultimo lugar que respondio el geocoder: el temblor del gps mueve las coordenadas unos metros,
//y dentro de ese radio se reusa el mismo nombre en vez de pedir otro que podria salir distinto
data class ResolvedPlace(
    val latitude: Double,
    val longitude: Double,
    val name: String
) {
    fun covers(latitude: Double, longitude: Double): Boolean =
        distanceMeters(this.latitude, this.longitude, latitude, longitude) <= SAME_PLACE_RADIUS_METERS

    companion object {
        //el gps tiembla decenas de metros y la ubicacion por red hasta un par de cuadras
        const val SAME_PLACE_RADIUS_METERS = 200.0
        private const val EARTH_RADIUS_METERS = 6_371_000.0

        //distancia sobre la tierra entre dos coordenadas (haversine)
        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val h = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            return 2 * EARTH_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))
        }
    }
}
