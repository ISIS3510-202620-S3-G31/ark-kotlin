package com.moviles.ark.domain.models

//sugerencia segun el clima de afuera; es una funcionalidad aparte de las estrategias de recomendacion
//(mood, frecuencia, default): no cambia el orden del catalogo, solo llena la tarjeta del clima del home
//frio o lluvia -> respiracion para calmarse adentro; despejado y de dia -> foto del dia
class WeatherSuggestionEngine(
    private val weather: WeatherModel
) {

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
