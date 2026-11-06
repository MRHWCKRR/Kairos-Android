package com.kairos.app.ui.components

import com.kairos.app.data.models.KairosPlan
import java.text.Normalizer
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

enum class PlannerSearchStatus(val label: String) {
    All("All"), Unfinished("Unfinished"), Overdue("Overdue"), Completed("Completed")
}

data class PlannerSearchTarget(val boardId: String, val sectionId: String? = null, val taskId: String? = null) {
    // Length prefixes preserve identity even when source IDs contain separators.
    val key: String get() = listOf(boardId, sectionId, taskId).joinToString(":") { value ->
        value?.let { "${it.length}:$it" } ?: "-"
    }
}

data class PlannerSearchRow(
    val target: PlannerSearchTarget,
    val title: String,
    val path: String,
    val completed: Boolean = false,
    val overdue: Boolean = false,
    val date: String? = null
) {
    val isBoard: Boolean get() = target.taskId == null
    val label: String get() = if (isBoard) "Board" else if (completed) "Completed" else "Unfinished"
}

data class PlannerSearchResults(val rows: List<PlannerSearchRow>, val total: Int)

/** Searches only fields represented by the Android planner model. Never guesses missing schema. */
object PlannerSearch {
    const val QUERY_LIMIT = 500
    const val RESULT_LIMIT = 100

    fun zone(timezone: String): ZoneId = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())

    private fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

    private fun validId(id: String) = id.isNotBlank() && id.length <= 160

    fun index(plan: KairosPlan, today: LocalDate): List<PlannerSearchRow> {
        val boardCounts = plan.boards.groupingBy { it.id }.eachCount()
        // Count archived identities too: the existing task mutation API addresses tasks globally.
        val taskCounts = plan.boards.flatMap { it.sections }.flatMap { it.tasks }.groupingBy { it.id }.eachCount()
        return buildList {
            plan.boards.filter { !it.archived && validId(it.id) && boardCounts[it.id] == 1 }.forEach { board ->
                add(PlannerSearchRow(PlannerSearchTarget(board.id), board.title, "Planner"))
                val sectionCounts = board.sections.groupingBy { it.id }.eachCount()
                board.sections.filter { !it.archived && validId(it.id) && sectionCounts[it.id] == 1 }.forEach { section ->
                    section.tasks.filter { !it.archived && validId(it.id) && taskCounts[it.id] == 1 }.forEach { task ->
                        val date = task.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                        add(PlannerSearchRow(PlannerSearchTarget(board.id, section.id, task.id), task.title,
                            "${board.title} / ${section.title}", task.completed,
                            !task.completed && date != null && date.isBefore(today), date?.toString()))
                    }
                }
            }
        }
    }

    fun search(index: List<PlannerSearchRow>, query: String, status: PlannerSearchStatus): PlannerSearchResults {
        val tokens = normalized(query.take(QUERY_LIMIT)).split(Regex("\\s+")).filter { it.isNotBlank() }
        val matches = index.filter { row ->
            val statusMatches = when (status) {
                PlannerSearchStatus.All -> true
                PlannerSearchStatus.Unfinished -> !row.isBoard && !row.completed
                PlannerSearchStatus.Overdue -> !row.isBoard && row.overdue
                PlannerSearchStatus.Completed -> !row.isBoard && row.completed
            }
            statusMatches && tokens.all { it in normalized("${row.title} ${row.path}") }
        }
        return PlannerSearchResults(matches.take(RESULT_LIMIT), matches.size)
    }
}
