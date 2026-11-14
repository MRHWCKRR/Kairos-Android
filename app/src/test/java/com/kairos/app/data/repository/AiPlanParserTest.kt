package com.kairos.app.data.repository

import org.junit.Assert.*
import org.junit.Test

class AiPlanParserTest {
    @Test fun planKeepsReviewedTasksAndExplicitWeeklyCommitments() {
        val parsed = AiRepository.parsePlan("""{"sections":[{"title":"Research","tasks":[{"title":"Read chapter"}]}],"recurringEvents":[{"title":"Class","day":1,"start":"09:00","end":"10:00","category":"class"}]}""")
        assertEquals("Read chapter", parsed.sections.single().tasks.single().title)
        assertEquals("Class", parsed.recurringEvents.single().title)
        assertEquals(1, parsed.recurringEvents.single().day)
        assertEquals("09:00", parsed.recurringEvents.single().start)
        val other = AiRepository.parsePlan("""{"sections":[{"title":"Research","tasks":[{"title":"Read chapter"}]}]}""")
        assertNotEquals(parsed.sections.single().id, other.sections.single().id)
    }
    @Test fun malformedPlanOrOvernightCommitmentCannotBeSaved() {
        assertThrows(IllegalArgumentException::class.java) { AiRepository.parsePlan("""{"sections":[{"title":"","tasks":[]}]}""") }
        assertThrows(IllegalArgumentException::class.java) { AiRepository.parsePlan("""{"sections":[{"title":"Research","tasks":[{"title":"Read"}]}],"recurringEvents":[{"title":"Class","day":7,"start":"10:00","end":"09:00"}]}""") }
    }
}
