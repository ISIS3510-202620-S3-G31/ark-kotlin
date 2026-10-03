package com.moviles.ark.domain.models

import com.moviles.ark.domain.composite.Emotion
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

//motor de insights (#28): analiza en el telefono el historial del usuario y devuelve hallazgos utiles
//no usa red ni android; recibe los datos ya leidos de la base local
class UsefulInsightsEngine(
    private val zone: TimeZone = TimeZone.getDefault(),
    private val now: () -> Long = { System.currentTimeMillis() }
) {

    //momentos del dia en hora local
    private enum class DayPart(val label: String, val from: Int, val until: Int) {
        NIGHT("at night", 0, 5),
        MORNING("in the morning", 5, 12),
        AFTERNOON("in the afternoon", 12, 18),
        EVENING("in the evening", 18, 24);

        companion object {
            fun of(hour: Int): DayPart = entries.first { hour >= it.from && hour < it.until }
        }
    }

    //devuelve los hallazgos del mas util al menos util
    fun generateInsights(
        checkIns: List<CheckInModel>,
        interactions: List<ToolInteraction> = emptyList(),
        tools: List<Tool> = emptyList()
    ): List<InsightModel> {
        val today = dayOf(now())
        val toolNames = tools.associate { it.id to it.name }
        return listOfNotNull(
            toolMoodCorrelation(checkIns, interactions, today, toolNames),
            moodTrend(checkIns, today),
            timeOfDayPattern(checkIns, today),
            frequentEmotion(checkIns, today),
            favoriteTool(interactions, today, toolNames)
        )
    }

    //compara el malestar de los dias en que se uso una herramienta contra los dias en que no
    private fun toolMoodCorrelation(
        checkIns: List<CheckInModel>,
        interactions: List<ToolInteraction>,
        today: Long,
        toolNames: Map<String, String>
    ): InsightModel? {
        val recent = checkIns.filter { today - dayOf(it.timestamp) in 0 until LONG_WINDOW_DAYS }
        if (recent.isEmpty()) return null

        val distressByDay: Map<Long, Double> = recent
            .groupBy { dayOf(it.timestamp) }
            .mapValues { (_, dayCheckIns) -> dayCheckIns.map { distressOf(it).toDouble() }.average() }

        val daysByTool: Map<String, Set<Long>> = interactions
            .filter { today - dayOf(it.timestamp.time) in 0 until LONG_WINDOW_DAYS }
            .groupBy { it.toolId }
            .mapValues { (_, toolInteractions) -> toolInteractions.map { dayOf(it.timestamp.time) }.toSet() }

        var bestToolId: String? = null
        var bestReduction = 0.0
        for ((toolId, toolDays) in daysByTool) {
            val withTool = distressByDay.filterKeys { it in toolDays }.values
            val withoutTool = distressByDay.filterKeys { it !in toolDays }.values
            if (withTool.size < MIN_DAYS_EACH_SIDE || withoutTool.size < MIN_DAYS_EACH_SIDE) continue
            val averageWithout = withoutTool.average()
            if (averageWithout <= 0.0) continue
            val reduction = (averageWithout - withTool.average()) / averageWithout
            if (reduction >= MIN_REDUCTION && reduction > bestReduction) {
                bestToolId = toolId
                bestReduction = reduction
            }
        }

        val toolId = bestToolId ?: return null
        val name = toolName(toolId, toolNames)
        return InsightModel(
            title = "$name helps you",
            message = "On days you used $name, your difficult feelings were ${percent(bestReduction)}% lower than on other days.",
            advice = "Keep $name close for tough days.",
            actionLabel = "Open $name"
        )
    }

    //compara esta semana (ultimos 7 dias) con la anterior
    private fun moodTrend(checkIns: List<CheckInModel>, today: Long): InsightModel? {
        val thisWeek = checkIns.filter { today - dayOf(it.timestamp) in 0 until 7 }
        val lastWeek = checkIns.filter { today - dayOf(it.timestamp) in 7 until 14 }
        if (thisWeek.size < MIN_CHECK_INS_PER_WEEK || lastWeek.size < MIN_CHECK_INS_PER_WEEK) return null

        val shareNow = thisWeek.count { isDifficult(it) }.toDouble() / thisWeek.size
        val shareBefore = lastWeek.count { isDifficult(it) }.toDouble() / lastWeek.size
        val change = shareNow - shareBefore
        if (abs(change) < MIN_TREND_CHANGE) return null

        return if (change < 0) {
            InsightModel(
                title = "A lighter week",
                message = "${percent(shareNow)}% of your check-ins this week were difficult, down from ${percent(shareBefore)}% last week.",
                advice = "Whatever you are doing is working. Keep checking in.",
                actionLabel = "Check in"
            )
        } else {
            InsightModel(
                title = "A heavier week",
                message = "${percent(shareNow)}% of your check-ins this week were difficult, up from ${percent(shareBefore)}% last week.",
                advice = "Be gentle with yourself. A calming tool can help.",
                actionLabel = "Find a tool"
            )
        }
    }

    //busca si los check-ins dificiles se concentran en un momento del dia
    private fun timeOfDayPattern(checkIns: List<CheckInModel>, today: Long): InsightModel? {
        val difficult = checkIns.filter { today - dayOf(it.timestamp) in 0 until LONG_WINDOW_DAYS && isDifficult(it) }
        if (difficult.size < MIN_DIFFICULT_CHECK_INS) return null

        val countsByPart = difficult.groupingBy { DayPart.of(hourOf(it.timestamp)) }.eachCount()
        val top = countsByPart.entries
            .sortedWith(compareByDescending<Map.Entry<DayPart, Int>> { it.value }.thenBy { it.key.ordinal })
            .first()
        if (top.value.toDouble() / difficult.size < MIN_TIME_OF_DAY_SHARE) return null

        val part = top.key
        return InsightModel(
            title = "Tough moments come ${part.label}",
            message = "${top.value} of your last ${difficult.size} difficult check-ins happened ${part.label}.",
            advice = "Try a calming tool ${part.label}, before the feeling builds up.",
            actionLabel = "Find a tool"
        )
    }

    //la emocion que mas aparecio en los check-ins de la semana
    private fun frequentEmotion(checkIns: List<CheckInModel>, today: Long): InsightModel? {
        val thisWeek = checkIns.filter { today - dayOf(it.timestamp) in 0 until 7 }
        if (thisWeek.size < MIN_CHECK_INS_FOR_EMOTION) return null

        val counts = thisWeek.flatMap { it.mood.getEmotions().distinct() }.groupingBy { it }.eachCount()
        val top = counts.entries
            .sortedWith(compareByDescending<Map.Entry<Emotion, Int>> { it.value }.thenBy { it.key.ordinal })
            .firstOrNull() ?: return null
        if (top.value.toDouble() / thisWeek.size < MIN_EMOTION_SHARE) return null

        val name = top.key.name.lowercase().replaceFirstChar { it.uppercase() }
        val message = "$name showed up in ${top.value} of your ${thisWeek.size} check-ins this week."
        return if (top.key in DIFFICULT_EMOTIONS) {
            InsightModel(
                title = "$name has been around",
                message = message,
                advice = "Naming a feeling is the first step. A Release or Calm down tool can help with it.",
                actionLabel = "Find a tool"
            )
        } else {
            InsightModel(
                title = "$name has been around",
                message = message,
                advice = "Notice what made those moments good and keep making room for it.",
                actionLabel = "Check in"
            )
        }
    }

    //la herramienta que mas uso en el ultimo mes
    private fun favoriteTool(interactions: List<ToolInteraction>, today: Long, toolNames: Map<String, String>): InsightModel? {
        val recent = interactions.filter { today - dayOf(it.timestamp.time) in 0 until LONG_WINDOW_DAYS }
        if (recent.size < MIN_SESSIONS) return null

        val top = recent
            .groupBy { it.toolId }
            .map { (toolId, toolInteractions) -> Triple(toolId, toolInteractions.size, toolInteractions.maxOf { it.timestamp.time }) }
            .sortedWith(compareByDescending<Triple<String, Int, Long>> { it.second }.thenByDescending { it.third })
            .first()
        if (top.second < MIN_USES) return null

        val name = toolName(top.first, toolNames)
        return InsightModel(
            title = "Your go-to tool",
            message = "You used $name ${top.second} times in the last $LONG_WINDOW_DAYS days, more than any other tool.",
            advice = "Tools you trust work best when you start them early, before a feeling peaks.",
            actionLabel = "Open $name"
        )
    }

    private fun dayOf(millis: Long): Long = (millis + zone.getOffset(millis)) / DAY_MILLIS

    private fun hourOf(millis: Long): Int {
        val calendar = Calendar.getInstance(zone)
        calendar.timeInMillis = millis
        return calendar.get(Calendar.HOUR_OF_DAY)
    }

    private fun isDifficult(checkIn: CheckInModel): Boolean =
        checkIn.mood.getEmotions().any { it in DIFFICULT_EMOTIONS }

    private fun distressOf(checkIn: CheckInModel): Float =
        if (isDifficult(checkIn)) checkIn.mood.getIntensity() else 0f

    private fun toolName(toolId: String, toolNames: Map<String, String>): String =
        toolNames[toolId] ?: toolId.split("_").joinToString(" ").replaceFirstChar { it.uppercase() }

    private fun percent(value: Double): Int = Math.round(value * 100).toInt()

    companion object {
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        private val DIFFICULT_EMOTIONS = setOf(Emotion.FEAR, Emotion.SADNESS, Emotion.ANGER, Emotion.DISGUST)
        private const val LONG_WINDOW_DAYS = 30
        private const val MIN_DAYS_EACH_SIDE = 2
        private const val MIN_REDUCTION = 0.2
        private const val MIN_CHECK_INS_PER_WEEK = 2
        private const val MIN_TREND_CHANGE = 0.2
        private const val MIN_DIFFICULT_CHECK_INS = 4
        private const val MIN_TIME_OF_DAY_SHARE = 0.6
        private const val MIN_CHECK_INS_FOR_EMOTION = 3
        private const val MIN_EMOTION_SHARE = 0.5
        private const val MIN_SESSIONS = 3
        private const val MIN_USES = 2
    }
}
