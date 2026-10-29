package com.moviles.ark.domain.models

import kotlin.math.roundToInt

//clima actual donde esta el usuario; no importa de que api venga
data class WeatherModel(
    val temperatureC: Double,
    //lluvia de la ultima hora en milimetros
    val precipitationMm: Double,
    //codigo wmo del clima: 0 despejado, 1-3 nubes, 51-67 llovizna y lluvia, 80-82 aguaceros, 95-99 tormenta
    val weatherCode: Int,
    val isDay: Boolean
) {
    fun isRainy(): Boolean =
        precipitationMm >= MIN_RAIN_MM || weatherCode in 51..67 || weatherCode in 80..82 || weatherCode >= 95

    fun isCold(): Boolean = temperatureC < COLD_BELOW_C

    //despejado y de dia: buen momento para salir a tomar la foto del dia
    fun isClearAndSunny(): Boolean = isDay && weatherCode <= 1 && !isRainy()

    fun roundedTemperature(): Int = temperatureC.roundToInt()

    companion object {
        //bogota casi siempre esta entre 9 y 20 grados; por debajo de 12 ya se siente frio
        const val COLD_BELOW_C = 12.0
        const val MIN_RAIN_MM = 0.1
    }
}

//que tiene el clima de especial; el home lo usa para el icono de la tarjeta
enum class WeatherCondition { RAINY, COLD, SUNNY }

//herramienta que sugiere el clima y el motivo que se muestra en el home
data class WeatherSuggestion(
    val toolModel: ToolModel,
    val condition: WeatherCondition,
    val reason: String
)
