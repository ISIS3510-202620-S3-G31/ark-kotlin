package com.moviles.ark.domain.models

//nivel de luz del lugar donde esta el usuario, a partir de los lux del sensor (#165)
//los cortes de 20 y 5000 lux son los del plan del sprint 3: menos de 20 es luz tenue y mas de 5000 es luz de dia
enum class LightLevel {
    //cuarto oscuro o casi sin luz
    DARK,
    //luz tenue, por ejemplo una lampara de noche
    DIM,
    //luz normal de una casa u oficina
    INDOOR,
    //luz del sol, afuera o junto a una ventana
    DAYLIGHT;

    companion object {
        const val DARK_MAX_LUX = 5f
        const val DIM_MAX_LUX = 20f
        const val DAYLIGHT_MIN_LUX = 5000f

        fun fromLux(lux: Float): LightLevel = when {
            lux < DARK_MAX_LUX -> DARK
            lux < DIM_MAX_LUX -> DIM
            lux < DAYLIGHT_MIN_LUX -> INDOOR
            else -> DAYLIGHT
        }
    }
}

//momento del dia segun la hora local del telefono (ritmo circadiano)
enum class DayPeriod {
    MORNING,
    AFTERNOON,
    EVENING,
    NIGHT;

    companion object {
        //hora de 0 a 23
        fun fromHour(hour: Int): DayPeriod = when (hour) {
            in 5..11 -> MORNING
            in 12..17 -> AFTERNOON
            in 18..21 -> EVENING
            else -> NIGHT
        }
    }
}

//lo que el motor de recomendaciones sabe del entorno sin que el usuario haga nada (#165)
data class EnvironmentalContext(
    //null si el telefono no tiene sensor de luz o todavia no ha medido
    val lightLevel: LightLevel? = null,
    //lux de la ultima medicion que cambio el nivel; null igual que lightLevel
    val lux: Float? = null,
    val dayPeriod: DayPeriod,
    val hasLightSensor: Boolean = true
) {
    //reglas de negocio: junta la luz con la hora
    //poca luz: el sensor dice oscuro o tenue; si no hay sensor, se usa la hora (de noche se asume poca luz)
    fun isLowLight(): Boolean {
        val level = lightLevel ?: return dayPeriod == DayPeriod.NIGHT
        return level == LightLevel.DARK || level == LightLevel.DIM
    }

    //luz de dia: mucha luz y ademas es de dia; una lampara fuerte a medianoche no cuenta como sol
    fun isBrightDaylight(): Boolean {
        return lightLevel == LightLevel.DAYLIGHT && dayPeriod != DayPeriod.NIGHT
    }
}
