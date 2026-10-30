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

    //texto de la tarjeta del home: el clima de afuera y por que esa herramienta
    private fun reason(condition: WeatherCondition): String {
        val degrees = "${weather.roundedTemperature()}°C"
        return when (condition) {
            WeatherCondition.RAINY -> "Rainy and $degrees outside: a calm breathing break indoors"
            WeatherCondition.COLD -> "Cold day, $degrees outside: a calm breathing break indoors"
            WeatherCondition.SUNNY -> "Sunny and $degrees outside: a good moment for today's photo"
            WeatherCondition.NEUTRAL -> "Mild and $degrees outside: a calm breathing break fits today"
        }
    }

    companion object {
        const val CALM_TOOL_ID = "custom_breathing"
        const val SUNNY_TOOL_ID = "photo_of_the_day"
        const val NEUTRAL_REASON = "Weather unavailable right now: a calm breathing break fits any day"
        const val LOADING_REASON = "Checking today's weather..."

        //tarjeta neutral por defecto: sin ubicacion o sin internet ni cache del clima
        fun defaultNeutralSuggestion(toolModels: List<ToolModel>, reason: String = NEUTRAL_REASON): WeatherSuggestion {
            val tool = toolModels.firstOrNull { it.id == CALM_TOOL_ID }
                ?: ToolModel.DEFAULT_CATALOG.first { it.id == CALM_TOOL_ID }
            return WeatherSuggestion(tool, WeatherCondition.NEUTRAL, reason)
        }

        //la misma tarjeta mientras llega el clima, para que no diga que no hay clima antes de tiempo
        fun loadingSuggestion(toolModels: List<ToolModel>): WeatherSuggestion = defaultNeutralSuggestion(toolModels, LOADING_REASON)
    }
}
