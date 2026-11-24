package com.moviles.ark

import com.moviles.ark.data.local.entities.StatsDailyVisitEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsVisitTest {

    @Test
    fun statsDailyVisitEntity_initialValues() {
        val now = 1700000000000L
        val visit = StatsDailyVisitEntity(
            userId = "user_123",
            dateKey = "20261028",
            count = 1,
            lastUpdatedAt = now,
            isSynced = false
        )

        assertEquals("user_123", visit.userId)
        assertEquals("20261028", visit.dateKey)
        assertEquals(1, visit.count)
        assertEquals(now, visit.lastUpdatedAt)
        assertFalse(visit.isSynced)
    }

    @Test
    fun statsDailyVisitEntity_incrementCountAndMarkSynced() {
        val visit = StatsDailyVisitEntity(
            userId = "user_123",
            dateKey = "20261028",
            count = 1,
            lastUpdatedAt = 1700000000000L,
            isSynced = false
        )

        val updated = visit.copy(count = visit.count + 1, isSynced = true)
        assertEquals(2, updated.count)
        assertTrue(updated.isSynced)
    }
}
