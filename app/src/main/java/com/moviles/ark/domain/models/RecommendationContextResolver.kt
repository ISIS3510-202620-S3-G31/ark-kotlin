package com.moviles.ark.domain.models

import com.moviles.ark.domain.strategies.DefaultRecommendationStrategy
import com.moviles.ark.domain.strategies.FrequencyBasedRecommendationStrategy
import com.moviles.ark.domain.strategies.MoodBasedRecommendationStrategy
import com.moviles.ark.domain.strategies.RecommendationContext
import com.moviles.ark.domain.strategies.RecommendationStrategy
import java.util.TimeZone

//por que se eligio la estrategia; el carrusel del home (#16) lo puede usar para sus etiquetas
enum class RecommendationReason {
    //hubo check-in hoy: se recomienda segun el animo
    TODAY_MOOD,
    //no hubo check-in hoy, pero ya hay historial: se recomiendan las mas usadas
    USAGE_HISTORY,
    //usuario nuevo o sin datos recientes: orden normal del catalogo
    DEFAULT
}

//resultado de la decision: que estrategia usar, con que datos y por que
data class RecommendationDecision(
    val strategy: RecommendationStrategy,
    val context: RecommendationContext,
    val reason: RecommendationReason
)

//logica de decision del home (#23): mira el contexto actual del usuario y elige la estrategia de recomendacion (#22)
//1. si hizo check-in hoy -> por animo (MoodBasedRecommendationStrategy)
//2. si no, pero termino al menos 3 herramientas en los ultimos 30 dias -> por frecuencia (FrequencyBasedRecommendationStrategy)
//3. si no -> orden por defecto (DefaultRecommendationStrategy)
class RecommendationContextResolver(
    private val zone: TimeZone = TimeZone.getDefault(),
    //se puede cambiar para fijar "ahora"
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    private val moodBased = MoodBasedRecommendationStrategy()
    private val frequencyBased = FrequencyBasedRecommendationStrategy()
    private val default = DefaultRecommendationStrategy()

    //arma el contexto con los datos del usuario y decide la estrategia
    fun resolve(checkIns: List<CheckInModel>, interactions: List<ToolInteraction>): RecommendationDecision {
        val today = dayOf(now())

        //el check-in mas reciente de hoy (hora local), si trae al menos una emocion
        val todayCheckIn = checkIns
            .filter { dayOf(it.timestamp) == today && it.isValidMood() }
            .maxByOrNull { it.timestamp }

        //cuantas veces termino cada herramienta en los ultimos 30 dias
        val usageCounts = interactions
            .filter { it.complete && today - dayOf(it.timestamp.time) in 0 until USAGE_WINDOW_DAYS }
            .groupingBy { it.toolId }
            .eachCount()

        val context = RecommendationContext(usageCounts = usageCounts, latestCheckIn = todayCheckIn)
        return when {
            todayCheckIn != null -> RecommendationDecision(moodBased, context, RecommendationReason.TODAY_MOOD)
            usageCounts.values.sum() >= MIN_COMPLETED_INTERACTIONS ->
                RecommendationDecision(frequencyBased, context, RecommendationReason.USAGE_HISTORY)
            else -> RecommendationDecision(default, context, RecommendationReason.DEFAULT)
        }
    }

    //atajo para el HomeViewModel (#13): decide y ordena el catalogo con la estrategia elegida
    fun recommend(
        tools: List<Tool>,
        checkIns: List<CheckInModel>,
        interactions: List<ToolInteraction>
    ): List<Tool> {
        val decision = resolve(checkIns, interactions)
        return decision.strategy.recommend(tools, decision.context)
    }

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
