package com.moviles.ark.domain.models

import com.moviles.ark.data.remote.dtos.EmotionDto
import com.moviles.ark.data.remote.dtos.MoodCheckInDto
import com.moviles.ark.data.remote.dtos.hasReadableEmotions
import com.moviles.ark.data.remote.dtos.toDomain
import com.moviles.ark.domain.composite.Emotion
import com.moviles.ark.domain.composite.MoodFactory
import com.moviles.ark.domain.composite.SingleEmotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

//las stats salen solo de lo que el usuario registro: nunca se inventa felicidad
class StatsCalculatorTest {

    private val now = System.currentTimeMillis()

    private fun checkIn(vararg parts: Pair<Emotion, Int>, daysAgo: Int = 0, note: String = "") = MoodCheckInModel(
        mood = MoodFactory.fromParts(parts.map { SingleEmotion(it.first, it.second) }),
        note = note,
        timestamp = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -daysAgo) }.timeInMillis
    )

    @Test
    fun topFeelingIsTheMostRecordedEmotionNotHappiness() {
        val stats = StatsCalculator().calculateStats(
            checkIns = listOf(checkIn(Emotion.SADNESS to 4), checkIn(Emotion.SADNESS to 2, daysAgo = 1), checkIn(Emotion.ANGER to 3, daysAgo = 2)),
            interactions = emptyList()
        )
        assertEquals(Emotion.SADNESS, stats.topEmotion)
        assertEquals(3, stats.totalCheckIns)
        assertFalse(stats.emotionFrequencies.containsKey(Emotion.HAPPINESS))
    }

    @Test
    fun noCheckInsMeansNoTopFeeling() {
        val stats = StatsCalculator().calculateStats(checkIns = emptyList(), interactions = emptyList())
        assertNull(stats.topEmotion)
        assertTrue(stats.emotionFrequencies.isEmpty())
    }

    @Test
    fun feedbackCheckInsAreNotCounted() {
        val stats = StatsCalculator().calculateStats(
            checkIns = listOf(checkIn(Emotion.FEAR to 5), checkIn(Emotion.HAPPINESS to 5, note = "Post-tool feedback: nice")),
            interactions = emptyList()
        )
        assertEquals(1, stats.totalCheckIns)
        assertEquals(Emotion.FEAR, stats.topEmotion)
    }

    @Test
    fun weeklyChartEndsTodayAndUsesEachEmotionsOwnIntensity() {
        val stats = StatsCalculator().calculateStats(
            checkIns = listOf(checkIn(Emotion.SADNESS to 2, Emotion.HAPPINESS to 5), checkIn(Emotion.ANGER to 4, daysAgo = 6), checkIn(Emotion.FEAR to 3, daysAgo = 7)),
            interactions = emptyList()
        )
        val week = StatsCalculator.lastSevenDays(now)
        assertEquals(7, week.size)
        //el dia de hace 7 dias ya no entra en la grafica
        assertEquals(Emotion.ANGER, stats.weeklyEmotions[week.first().key])
        assertEquals(4f, stats.weeklyIntensities[week.first().key])
        //hoy: empate entre tristeza y felicidad, gana la primera registrada con su propia intensidad
        assertEquals(Emotion.SADNESS, stats.weeklyEmotions[week.last().key])
        assertEquals(2f, stats.weeklyIntensities[week.last().key])
        assertFalse(stats.weeklyEmotions.containsValue(Emotion.FEAR))
    }

    @Test
    fun unreadableFirestoreCheckInDoesNotBecomeHappiness() {
        val unreadable = MoodCheckInDto(moodName = "Mixed Mood", timestamp = now, emotions = listOf(EmotionDto(name = "felicidad", intensity = 4)))
        assertFalse(unreadable.hasReadableEmotions())
        assertTrue(unreadable.toDomain().mood.getEmotions().isEmpty())

        //un documento viejo sin lista de emociones pero con el nombre del animo se lee por el nombre
        val byName = MoodCheckInDto(moodName = "Sadness", timestamp = now)
        assertTrue(byName.hasReadableEmotions())
        assertEquals(listOf(Emotion.SADNESS), byName.toDomain().mood.getEmotions())
    }

    @Test
    fun lastUpdatedLabelIsTheDataTimeNotNow() {
        val yesterday = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
        assertTrue(StatsModel.lastUpdatedLabel(yesterday, now).startsWith("Yesterday at "))
        assertTrue(StatsModel.lastUpdatedLabel(now, now).startsWith("Today at "))
        assertEquals("never", StatsModel.lastUpdatedLabel(0L, now))
    }
}
