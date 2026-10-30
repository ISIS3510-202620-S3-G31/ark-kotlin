package com.moviles.ark.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

//la tarjeta del clima siempre tiene una sugerencia y su motivo coincide con el clima de afuera
class WeatherSuggestionEngineTest {

    private val tools = ToolModel.DEFAULT_CATALOG

    private fun suggest(tempC: Double, code: Int, rainMm: Double = 0.0, isDay: Boolean = true) =
        WeatherSuggestionEngine(WeatherModel(tempC, rainMm, code, isDay)).suggest(tools)

    @Test
    fun rainSuggestsBreathingEvenIfCold() {
        val s = suggest(tempC = 9.0, code = 61, rainMm = 1.2)
        assertEquals(WeatherCondition.RAINY, s.condition)
        assertEquals(WeatherSuggestionEngine.CALM_TOOL_ID, s.toolModel.id)
        assertTrue(s.reason.startsWith("Rainy and 9°C"))
    }

    @Test
    fun coldDrySuggestsBreathing() {
        val s = suggest(tempC = 11.4, code = 2)
        assertEquals(WeatherCondition.COLD, s.condition)
        assertEquals(WeatherSuggestionEngine.CALM_TOOL_ID, s.toolModel.id)
    }

    @Test
    fun clearDaySuggestsTheDailyPhoto() {
        val s = suggest(tempC = 20.0, code = 0)
        assertEquals(WeatherCondition.SUNNY, s.condition)
        assertEquals(WeatherSuggestionEngine.SUNNY_TOOL_ID, s.toolModel.id)
        assertTrue(s.reason.startsWith("Sunny and 20°C"))
    }

    @Test
    fun clearNightIsNotSunny() {
        val s = suggest(tempC = 15.0, code = 0, isDay = false)
        assertEquals(WeatherCondition.NEUTRAL, s.condition)
        assertEquals(WeatherSuggestionEngine.CALM_TOOL_ID, s.toolModel.id)
    }

    @Test
    fun mildCloudyShowsTheRealTemperature() {
        val s = suggest(tempC = 17.9, code = 3)
        assertEquals(WeatherCondition.NEUTRAL, s.condition)
        assertTrue(s.reason.startsWith("Mild and 18°C"))
    }

    @Test
    fun missingToolFallsBackToTheNeutralCard() {
        val withoutPhoto = tools.filter { it.id != WeatherSuggestionEngine.SUNNY_TOOL_ID }
        val s = WeatherSuggestionEngine(WeatherModel(20.0, 0.0, 0, true)).suggest(withoutPhoto)
        assertEquals(WeatherCondition.NEUTRAL, s.condition)
        assertEquals(WeatherSuggestionEngine.NEUTRAL_REASON, s.reason)
    }
}
