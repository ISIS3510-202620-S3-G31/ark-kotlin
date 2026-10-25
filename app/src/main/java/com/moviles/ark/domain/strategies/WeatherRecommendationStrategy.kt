package com.moviles.ark.domain.strategies

import com.moviles.ark.domain.models.Tool
import com.moviles.ark.domain.models.WeatherCondition
import com.moviles.ark.domain.models.WeatherModel
import com.moviles.ark.domain.models.WeatherSuggestion

//estrategia segun el clima de afuera (#134); va aparte del resolver (mood, frecuencia, default)
//porque no compite con la recomendacion principal: el home la muestra en su propia tarjeta
//frio o lluvia -> respiracion para calmarse adentro; despejado y de dia -> foto del dia
class WeatherRecommendationStrategy(
    private val weather: WeatherModel
) : RecommendationStrategy {

    override fun recommend(tools: List<Tool>, context: RecommendationContext): List<Tool> {
        val targetId = condition()?.let(::toolFor) ?: return tools
        val (target, others) = tools.partition { it.id == targetId }
        return target + others
    }

    //null si el clima no dice nada especial (nublado y templado) o si la herramienta no esta en el catalogo
    fun suggest(tools: List<Tool>): WeatherSuggestion? {
        val condition = condition() ?: return null
        val tool = tools.firstOrNull { it.id == toolFor(condition) } ?: return null
        return WeatherSuggestion(tool, condition, reason(condition))
    }

    //la lluvia pesa mas que el frio: si llueve y hace frio se muestra como lluvia
    private fun condition(): WeatherCondition? = when {
        weather.isRainy() -> WeatherCondition.RAINY
        weather.isCold() -> WeatherCondition.COLD
        weather.isClearAndSunny() -> WeatherCondition.SUNNY
        else -> null
    }

    private fun toolFor(condition: WeatherCondition): String = when (condition) {
        WeatherCondition.RAINY, WeatherCondition.COLD -> CALM_TOOL_ID
        WeatherCondition.SUNNY -> SUNNY_TOOL_ID
    }

    //texto corto de la tarjeta del home
    private fun reason(condition: WeatherCondition): String {
        val degrees = "${weather.roundedTemperature()}°C"
        return when (condition) {
            WeatherCondition.RAINY -> "Rainy and $degrees outside"
            WeatherCondition.COLD -> "Cold day, $degrees outside"
            WeatherCondition.SUNNY -> "Sunny and $degrees outside"
        }
    }

    companion object {
        const val CALM_TOOL_ID = "custom_breathing"
        const val SUNNY_TOOL_ID = "photo_of_the_day"
    }
}
