package com.kairos.app.ui.components

import com.kairos.app.data.models.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PlannerSearchTest {
    private val today = LocalDate.of(2026, 11, 6)
    private fun plan(vararg tasks: KairosTask) = KairosPlan(boards = listOf(
        KairosBoard("b", "Biology", listOf(KairosSection("s", "Assignments", tasks.toList())))
    ))
    private fun results(plan: KairosPlan, query: String = "", status: PlannerSearchStatus = PlannerSearchStatus.All) =
        PlannerSearch.search(PlannerSearch.index(plan, today), query, status)

    @Test fun normalizedTokensMatchTitleAndContextInAnyOrder() {
        val found = results(plan(KairosTask("t", "Café résumé")), "RESUME biology cafe")
        assertEquals(listOf("t"), found.rows.map { it.target.taskId })
        assertEquals(0, results(plan(KairosTask("t", "Café")), "cafe missing").total)
    }

    @Test fun filtersUseStrictDatesAndExcludeBoards() {
        val plan = plan(
            KairosTask("past", "Past", date = "2026-11-05"),
            KairosTask("today", "Today", date = "2026-11-06"),
            KairosTask("done", "Done", true, "2026-11-01"),
            KairosTask("invalid", "Invalid", date = "2026-02-30")
        )
        assertEquals(listOf("past"), results(plan, status = PlannerSearchStatus.Overdue).rows.map { it.target.taskId })
        assertEquals(3, results(plan, status = PlannerSearchStatus.Unfinished).total)
        assertEquals(listOf("done"), results(plan, status = PlannerSearchStatus.Completed).rows.map { it.target.taskId })
        assertEquals(5, results(plan).total)
    }

    @Test fun archivesAndAmbiguousIdentitiesAreExcluded() {
        val valid = KairosBoard("b", "Board", listOf(
            KairosSection("s", "Visible", listOf(KairosTask("duplicate", "Hidden"), KairosTask("ok", "Visible"))),
            KairosSection("hidden", "Archived", listOf(KairosTask("duplicate", "Duplicate archived")), archived = true),
            KairosSection("dupsection", "One", listOf(KairosTask("a", "Hidden"))),
            KairosSection("dupsection", "Two", listOf(KairosTask("c", "Hidden")))
        ))
        val plan = KairosPlan(boards = listOf(valid, KairosBoard("archive", "Archive", archived = true),
            KairosBoard("duplicateboard", "One"), KairosBoard("duplicateboard", "Two")))
        assertEquals(listOf(null, "ok"), results(plan).rows.map { it.target.taskId })
        assertEquals(0, results(plan.copy(boards = listOf(valid.copy(archived = true)))).total)
    }

    @Test fun blankQueriesBrowseAndResultsAreBoundedWithAccurateTotals() {
        val plan = plan(*(1..125).map { KairosTask("t$it", "Task $it") }.toTypedArray())
        val result = results(plan)
        assertEquals(126, result.total)
        assertEquals(100, result.rows.size)
        assertEquals(125, results(plan, status = PlannerSearchStatus.Unfinished).total)
        assertEquals(125, results(plan, "Task" + " ".repeat(496) + "absent").total)
    }

    @Test fun todayUsesProfileTimezoneAndInvalidTimezoneFallsBack() {
        val instant = Instant.parse("2026-11-06T00:30:00Z")
        assertEquals(LocalDate.of(2026, 11, 5), instant.atZone(PlannerSearch.zone("America/Los_Angeles")).toLocalDate())
        assertEquals(LocalDate.of(2026, 11, 6), instant.atZone(PlannerSearch.zone("Asia/Tokyo")).toLocalDate())
        assertNotNull(PlannerSearch.zone("not-a-timezone"))
    }

    @Test fun updatedPlannerCannotResolveDeletedOrMovedTarget() {
        val oldTarget = results(plan(KairosTask("t", "Read"))).rows.single { !it.isBoard }.target
        val changed = plan(KairosTask("other", "Read"))
        assertFalse(PlannerSearch.index(changed, today).any { it.target == oldTarget })
    }
}
