package com.moviles.ark.domain.models

import com.moviles.ark.domain.strategies.DefaultRecommendationStrategy
import com.moviles.ark.domain.strategies.FrequencyBasedRecommendationStrategy
import com.moviles.ark.domain.strategies.MoodBasedRecommendationStrategy
import com.moviles.ark.domain.strategies.RecommendationContext
import com.moviles.ark.domain.strategies.RecommendationStrategy
import java.util.TimeZone

//por que se eligio la estrategia; la tarjeta del home muestra la etiqueta y la explicacion
enum class RecommendationReason(val label: String, val explanation: String) {
    //hubo check-in hoy: se recomienda segun el animo
    TODAY_MOOD("Based on your mood", "Picked for how you said you feel today"),
    //no hubo check-in hoy, pero ya hay historial: se recomiendan las mas usadas
    USAGE_HISTORY("Your go-to tool", "One of the tools you finish most often"),
    //usuario nuevo o sin datos recientes: orden normal del catalogo
    DEFAULT("Suggested for today", "A good tool to start with. Check in to get one for your mood")
}

//resultado de la decision: que estrategia usar, con que datos y por que
data class RecommendationDecision(
    val strategy: RecommendationStrategy,
    val context: RecommendationContext,
    val reason: RecommendationReason
)

// mira el contexto actual del usuario y elige la estrategia de recomendacion
// si hizo check-in hoy por mood
// si no pero termino al menos 3 herramientas en los ultimos 30 dias por frecuencia estos ddatos igual los podemos cambiar más adelante si llega a ser el caso de como susen los ususraior nuestra app
// si no orden por defecto
class RecommendationContextResolver(
    private val zone: TimeZone = TimeZone.getDefault(),
    //se puede cambiar para fijar "ahora"
    private val now: () -> Long = { System.currentTimeMillis() }
) {

    //arma el contexto con los datos del usuario y decide la estrategia
    fun resolve(checkIns: List<MoodCheckInModel>, interactions: List<ToolInteractionModel>): RecommendationDecision {
        val today = dayOf(now())

        //el check-in mas reciente de hoy (hora local) si trae al menos una emocion
        val todayCheckIn = checkIns
            .filter { dayOf(it.timestamp) == today && it.isValidMood() } // uso dayof para hacer bien la comparacion para el tema de dif horarias e
            .maxByOrNull { it.timestamp } // Se queda con el más reciente por si hizo varios

        //cuantas veces termino cada herramienta en los ultimos 30 dias
        val usageCounts = interactions
            .filter { it.complete && today - dayOf(it.timestamp.time) in 0 until USAGE_WINDOW_DAYS } // Solo si complelto la herramienta pooruqe puede uqe enre pero al final no
            .groupingBy { it.toolId }  // miramos que tan lejos fue con el tiempo y luego miro si esta entre 0 y 30 y si si pues la incluyo
            .eachCount() // contamos ya qeu agrupamos por herramienta

        val context = RecommendationContext(usageCounts = usageCounts, latestCheckIn = todayCheckIn)
        return when {
            todayCheckIn != null -> RecommendationDecision(MoodBasedRecommendationStrategy(), context, RecommendationReason.TODAY_MOOD)
            usageCounts.values.sum() >= MIN_COMPLETED_INTERACTIONS ->
                RecommendationDecision(FrequencyBasedRecommendationStrategy(), context, RecommendationReason.USAGE_HISTORY)
            else -> RecommendationDecision(DefaultRecommendationStrategy(), context, RecommendationReason.DEFAULT)
        }
    }


    //plan b cuando no se pudieron leer los datos del usuario: orden por defecto del catalogo
    fun fallback(): RecommendationDecision =
        RecommendationDecision(DefaultRecommendationStrategy(), RecommendationContext(), RecommendationReason.DEFAULT)

    //numero del dia en la hora local; se suma el desfase de la zona para que el dia cambie a medianoche de colombia
    private fun dayOf(millis: Long): Long = (millis + zone.getOffset(millis)) / DAY_MILLIS

    companion object {
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        //ventana del historial de uso
        const val USAGE_WINDOW_DAYS = 30

        //minimo de herramientas terminadas para que la frecuencia diga algo
        const val MIN_COMPLETED_INTERACTIONS = 3
    }
}
