package com.moviles.ark.domain.models

import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.domain.composite.SingleEmotion
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

//modelo de dominio principal de estadisticas usando directamente los enums y tipos nativos
data class StatsModel(
    val totalCheckIns: Int,
    val topEmotion: Emotion?,
    val weeklyIntensities: Map<String, Float>,
    val weeklyEmotions: Map<String, Emotion>,
    val toolUsagePercentages: Map<String, Float>,
    val emotionFrequencies: Map<Emotion, Int> = emptyMap(),
    val lastUpdated: Long = 0L
) {
    //sin check-ins ni herramientas usadas se muestra la vista vacia
    fun isEmpty(): Boolean = totalCheckIns == 0 && toolUsagePercentages.isEmpty()

    //emociones de la mas frecuente a la menos, con su porcentaje del total
    fun emotionDistribution(): List<EmotionShare> {
        val total = emotionRecordCount().coerceAtLeast(1)
        return emotionFrequencies.entries
            .sortedByDescending { it.value }
            .map { (emotion, count) ->
                val fraction = (count.toFloat() / total).coerceIn(0f, 1f)
                EmotionShare(emotion, count, (count.toFloat() / total * 100f).toInt(), fraction)
            }
    }

    //cuantas emociones se registraron en total
    fun emotionRecordCount(): Int = emotionFrequencies.values.sum()

    companion object {
        //cuando se cargaron estas stats de firestore ("Today at 1:05 PM", "Yesterday at...", "Oct 28 at...")
        //es la fecha de los datos, no la de abrir la pantalla ni la de tocar refrescar
        fun lastUpdatedLabel(lastUpdated: Long, now: Long = System.currentTimeMillis()): String {
            if (lastUpdated <= 0L) return "never"
            val time = SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(lastUpdated))
            val dayKey = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            val yesterday = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -1) }.time
            val day = when (dayKey.format(Date(lastUpdated))) {
                dayKey.format(Date(now)) -> "Today"
                dayKey.format(yesterday) -> "Yesterday"
                else -> SimpleDateFormat("MMM d", Locale.ENGLISH).format(Date(lastUpdated))
            }
            return "$day at $time"
        }

        //alto de la barra de un dia: la intensidad del dia sobre la escala de 1 a 5
        fun intensityRatio(intensity: Float): Float = (intensity / SingleEmotion.MAX_INTENSITY).coerceIn(0f, 1f)
    }
}

//una emocion en la distribucion: cuantas veces aparece y que porcentaje es
data class EmotionShare(
    val emotion: Emotion,
    val count: Int,
    val percentage: Int,
    //lo mismo que percentage pero de 0 a 1, para el largo de la barra
    val fraction: Float
)
