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

    private val ACCENT_MARKS = Regex("\\p{Mn}+")
    private val SPACES = Regex("\\s+")
    private val SPACES_AROUND_COMMA = Regex("\\s*,\\s*")

    //arma el nombre con las partes que traiga el geocoder; las que vienen null, vacias o "null" se saltan
    fun format(
        subLocality: String?,
        locality: String?,
        subAdminArea: String?,
        adminArea: String?,
        countryCode: String?,
        countryName: String? = null
    ): String? {
        val neighborhoodName = clean(subLocality)
        val cityName = chooseCityName(locality, subAdminArea, adminArea)
        val countryText = chooseCountryText(countryCode, countryName)

        val parts = mutableListOf<String>()
        if (neighborhoodName != null) parts.add(neighborhoodName)
        if (cityName != null) parts.add(cityName)
        if (countryText != null) parts.add(countryText)

        val partsWithoutRepeats = removeRepeatedParts(parts)
        if (partsWithoutRepeats.isEmpty()) return null

        val fullName = partsWithoutRepeats.joinToString(SEPARATOR)
        //si se corta por el tope no debe quedar terminando en ", "
        val shortName = fullName.take(MAX_LENGTH)
        return shortName.trimEnd(' ', ',')
    }

    //la ciudad es locality y, si no hay, el municipio o el departamento
    private fun chooseCityName(locality: String?, subAdminArea: String?, adminArea: String?): String? {
        val cleanLocality = clean(locality)
        if (cleanLocality != null) return cleanLocality
        val cleanMunicipality = clean(subAdminArea)
        if (cleanMunicipality != null) return cleanMunicipality
        return clean(adminArea)
    }

    //el pais va con su codigo de dos letras en mayuscula; si no hay codigo, con el nombre
    private fun chooseCountryText(countryCode: String?, countryName: String?): String? {
        val cleanCode = clean(countryCode)
        if (cleanCode != null) return cleanCode.uppercase(Locale.ROOT)
        return clean(countryName)
    }

    //una parte repetida (localidad = ciudad) se escribe una vez, con la forma de la parte mas general;
    //por eso una parte solo se queda si ninguna parte de despues es el mismo nombre
    private fun removeRepeatedParts(parts: List<String>): List<String> {
        val result = mutableListOf<String>()
        for (i in parts.indices) {
            var repeatedLater = false
            for (j in i + 1 until parts.size) {
                if (comparisonKey(parts[i]) == comparisonKey(parts[j])) {
                    repeatedLater = true
                }
            }
            if (!repeatedLater) result.add(parts[i])
        }
        return result
    }

    //clave para comparar: minusculas, sin tildes y sin espacios de sobra; "Bogota" y " Bogotá" dan lo mismo
    fun comparisonKey(name: String): String {
        //NFD separa la tilde de la letra ("á" queda "a" + tilde) y asi se puede borrar la tilde sola
        val separatedAccents = Normalizer.normalize(name, Normalizer.Form.NFD)
        val withoutAccents = separatedAccents.replace(ACCENT_MARKS, "")
        val lowerCase = withoutAccents.lowercase(Locale.ROOT)
        val singleSpaces = lowerCase.replace(SPACES, " ")
        val tightCommas = singleSpaces.replace(SPACES_AROUND_COMMA, ",")
        return tightCommas.trim()
    }

    fun isSamePlace(first: String?, second: String?): Boolean {
        val firstName = clean(first)
        val secondName = clean(second)
        if (firstName == null || secondName == null) return false
        return comparisonKey(firstName) == comparisonKey(secondName)
    }

    //lo que se guarda: null en vez de "", "null" o espacios; si ya hay un nombre guardado del mismo lugar
    //se reusa ese mismo texto, asi un lugar queda escrito de una sola forma y no cuenta como dos
    fun canonical(newName: String?, savedNames: List<String>): String? {
        val cleanName = clean(newName)
        if (cleanName == null) return null
        val shortName = cleanName.take(MAX_LENGTH)

        for (savedName in savedNames) {
            if (isSamePlace(savedName, shortName)) {
                return clean(savedName)
            }
        }
        return shortName
    }

    //texto sin espacios de sobra; null si no dice nada
    fun clean(text: String?): String? {
        if (text == null) return null
        val trimmed = text.replace(SPACES, " ").trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.equals("null", ignoreCase = true)) return null
        return trimmed
    }
}

//ultimo lugar que respondio el geocoder: el temblor del gps mueve las coordenadas unos metros,
//y dentro de ese radio se reusa el mismo nombre en vez de pedir otro que podria salir distinto
data class ResolvedPlace(
    val latitude: Double,
    val longitude: Double,
    val name: String
) {
    fun covers(latitude: Double, longitude: Double): Boolean {
        val distance = distanceMeters(this.latitude, this.longitude, latitude, longitude)
        return distance <= SAME_PLACE_RADIUS_METERS
    }

    companion object {
        //el gps tiembla decenas de metros y la ubicacion por red hasta un par de cuadras
        const val SAME_PLACE_RADIUS_METERS = 200.0
        private const val EARTH_RADIUS_METERS = 6_371_000.0

        //distancia sobre la tierra entre dos coordenadas (formula de haversine)
        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val latDifference = Math.toRadians(lat2 - lat1)
            val lonDifference = Math.toRadians(lon2 - lon1)
            val sinHalfLat = sin(latDifference / 2)
            val sinHalfLon = sin(lonDifference / 2)
            val cosLat1 = cos(Math.toRadians(lat1))
            val cosLat2 = cos(Math.toRadians(lat2))

            var haversine = sinHalfLat.pow(2) + cosLat1 * cosLat2 * sinHalfLon.pow(2)
            //por redondeo puede salir un poquito fuera de 0..1 y asin fallaria
            if (haversine < 0.0) haversine = 0.0
            if (haversine > 1.0) haversine = 1.0

            return 2 * EARTH_RADIUS_METERS * asin(sqrt(haversine))
        }
    }
}
