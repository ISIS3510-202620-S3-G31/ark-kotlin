package com.moviles.ark.domain.models

//sugerencia segun el clima de afuera; es una funcionalidad aparte de las estrategias de recomendacion
//(mood, frecuencia, default): no cambia el orden del catalogo, solo llena la tarjeta del clima del home
//frio o lluvia -> respiracion para calmarse adentro; despejado y de dia -> foto del dia;
//nublado y templado -> respiracion, con la temperatura en el motivo para que se vea que si leyo el clima
class WeatherSuggestionEngine(
    private val weather: WeatherModel
) {

    //siempre hay sugerencia; si la herramienta del clima no esta en el catalogo se usa la neutral por defecto
    fun suggest(toolModels: List<ToolModel>): WeatherSuggestion {
        val condition = condition()
        val tool = toolModels.firstOrNull { it.id == toolFor(condition) }
            ?: return defaultNeutralSuggestion(toolModels)
        return WeatherSuggestion(tool, condition, reason(condition))
    }

    //la lluvia pesa mas que el frio: si llueve y hace frio se muestra como lluvia
    private fun condition(): WeatherCondition = when {
        weather.isRainy() -> WeatherCondition.RAINY
        weather.isCold() -> WeatherCondition.COLD
        weather.isClearAndSunny() -> WeatherCondition.SUNNY
        else -> WeatherCondition.NEUTRAL
    }

    private fun toolFor(condition: WeatherCondition): String = when (condition) {
        WeatherCondition.RAINY, WeatherCondition.COLD, WeatherCondition.NEUTRAL -> CALM_TOOL_ID
        WeatherCondition.SUNNY -> SUNNY_TOOL_ID
    }

    //texto corto de la tarjeta del home
    private fun reason(condition: WeatherCondition): String {
        val degrees = "${weather.roundedTemperature()}°C"
        return when (condition) {
            WeatherCondition.RAINY -> "Rainy and $degrees outside"
            WeatherCondition.COLD -> "Cold day, $degrees outside"
            WeatherCondition.SUNNY -> "Sunny and $degrees outside"
            WeatherCondition.NEUTRAL -> "Mild and $degrees outside"
        }
    }

    companion object {
        const val CALM_TOOL_ID = "custom_breathing"
        const val SUNNY_TOOL_ID = "photo_of_the_day"
        const val NEUTRAL_REASON = "Tune your practice to today's climate and rhythm."

        //tarjeta neutral por defecto: se usa mientras carga, sin ubicacion o sin internet ni cache del clima
        fun defaultNeutralSuggestion(toolModels: List<ToolModel>): WeatherSuggestion {
            val tool = toolModels.firstOrNull { it.id == CALM_TOOL_ID }
                ?: ToolModel.DEFAULT_CATALOG.first { it.id == CALM_TOOL_ID }
            return WeatherSuggestion(tool, WeatherCondition.NEUTRAL, NEUTRAL_REASON)
        }
    }
}
