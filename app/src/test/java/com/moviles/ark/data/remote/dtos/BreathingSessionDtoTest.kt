package com.moviles.ark.data.remote.dtos

import com.moviles.ark.data.local.entities.BreathingSessionEntity
import com.moviles.ark.data.local.entities.toEntity
import com.moviles.ark.domain.models.BreathingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

//pruebas de lo que se sube a firestore por cada sesion de Custom Breathing terminada
class BreathingSessionDtoTest {

    @Test
    fun sessionSavedWithUserIsPendingToUpload() {
        val session = BreathingSession(completedCycles = 5, durationSeconds = 95, timestamp = 1_700_000_000_000L, pattern = "4-7-8")

        val entity = session.toEntity("user123")

        assertEquals("user123", entity.userId)
        assertFalse(entity.isSynced)
        assertEquals(5, entity.completedCycles)
    }

    @Test
    fun dtoHasTheFieldsThatBigQueryReads() {
        val entity = BreathingSessionEntity(
            completedCycles = 7,
            durationSeconds = 133,
            timestamp = 1_700_000_000_000L,
            patternType = "RELAXING",
            musicSeconds = 60,
            userId = "anonymous"
        )

        val dto = entity.toDto(userId = "user123", deviceModel = "Pixel 8")

        assertEquals("user123", dto.userId)
        assertEquals(Date(1_700_000_000_000L), dto.endedAt)
        assertEquals(7, dto.completedCycles)
        assertEquals(133, dto.durationSeconds)
        assertEquals("RELAXING", dto.patternType)
        assertTrue(dto.withMusic)
        assertEquals(60, dto.musicSeconds)
        assertEquals("Pixel 8", dto.deviceModel)
    }

    @Test
    fun sessionWithoutMusicIsSavedWithWithMusicFalse() {
        val entity = BreathingSessionEntity(completedCycles = 2, durationSeconds = 30, timestamp = 1L, patternType = "BOX", musicSeconds = 0)

        val dto = entity.toDto(userId = "user123", deviceModel = "Pixel 8")

        assertFalse(dto.withMusic)
        assertEquals(0, dto.musicSeconds)
    }

    @Test
    fun patternTypeAndMusicGoFromSessionToTable() {
        val session = BreathingSession(completedCycles = 4, durationSeconds = 64, timestamp = 1L, patternType = "QUICK_CALM", musicSeconds = 20)

        val entity = session.toEntity("user123")

        assertEquals("QUICK_CALM", entity.patternType)
        assertEquals(20, entity.musicSeconds)
    }

    @Test
    fun documentIdIsTheSameEveryTimeSoUploadsDoNotDuplicate() {
        val entity = BreathingSessionEntity(completedCycles = 3, durationSeconds = 40, timestamp = 1_700_000_000_000L)

        assertEquals("1700000000000", entity.documentId())
        assertEquals(entity.documentId(), entity.copy(id = 99).documentId())
    }
}
