package com.fitcheckai.core.analytics

import com.fitcheckai.core.model.MetricEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryMetricsTrackerTest {

    @Test
    fun `keeps only the most recent events`() {
        val tracker = InMemoryMetricsTracker()

        repeat(600) { tracker.track(MetricEvent(name = "e$it")) }

        assertEquals(500, tracker.events.value.size)
        assertEquals("e599", tracker.events.value.last().name)
    }
}
