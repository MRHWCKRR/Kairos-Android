package com.kairos.app.data.repository

import org.junit.Assert.*
import org.junit.Test

class TaskChecklistMergeTest {
    private fun item(id: String = "i", title: String = "Step", completed: Boolean = false) =
        mapOf<String, Any?>("id" to id, "title" to title, "completed" to completed)
    private fun task(id: String = "t", checklist: Any? = listOf(item())) = mapOf<String, Any?>("id" to id, "checklist" to checklist)
    private fun doc(vararg tasks: Map<String, Any?>) = mapOf<String, Any?>("boards" to listOf(mapOf("id" to "b", "sections" to listOf(mapOf("id" to "s", "tasks" to tasks.toList())))))
    private fun rejected(block: () -> Unit) { try { block(); fail("Expected conflict") } catch (_: SharedDataConflict) {} }
    @Suppress("UNCHECKED_CAST")
    private fun checklist(doc: Map<String, Any?>): List<Map<String, Any?>> {
        val board = (doc["boards"] as List<Map<String, Any?>>).single()
        val section = (board["sections"] as List<Map<String, Any?>>).single()
        return (section["tasks"] as List<Map<String, Any?>>).first()["checklist"] as List<Map<String, Any?>>
    }
    @Test fun disjointItemChangesPreserveUnknownMetadata() {
        val base = doc(task())
        val raw = doc(task(checklist = listOf(item() + ("future" to mapOf("attachment" to "a")))))
        val edited = doc(task(checklist = listOf(item(title = "Edited"))))
        val remote = doc(task(checklist = listOf(item(completed = true) + ("future" to mapOf("attachment" to "b")))))
        val merged = checklist(SharedDataMerge.merge(base, edited, remote, raw)).single()
        assertEquals("Edited", merged["title"])
        assertEquals(true, merged["completed"])
        assertEquals(mapOf("attachment" to "b"), merged["future"])
    }
    @Test fun deletionRejectsConcurrentUnknownItemChanges() {
        val base = doc(task())
        val raw = doc(task(checklist = listOf(item() + ("future" to "a"))))
        val remote = doc(task(checklist = listOf(item() + ("future" to "b"))))
        rejected { SharedDataMerge.merge(base, doc(task(checklist = emptyList<Any>())), remote, raw) }
        assertEquals(emptyList<Any>(), checklist(SharedDataMerge.merge(base, doc(task(checklist = emptyList<Any>())), raw, raw)))
    }
    @Test fun conflictsOnSameItemLeafAndAllowsIdsRepeatedAcrossDifferentTasks() {
        val base = doc(task(), task("other"))
        val merged = SharedDataMerge.merge(base, doc(task(checklist = listOf(item(title = "Edited"))), task("other")), base)
        assertEquals("Edited", checklist(merged).single()["title"])
        rejected { SharedDataMerge.merge(doc(task()), doc(task(checklist = listOf(item(title = "Mine")))), doc(task(checklist = listOf(item(title = "Theirs"))))) }
    }
    @Test fun nullAndMissingChecklistsBehaveAsEmptyAndUnchangedNullStaysNull() {
        val base = doc(task(checklist = null))
        assertEquals(base, SharedDataMerge.merge(base, doc(task(checklist = emptyList<Any>())), base))
        assertEquals(listOf(item()), checklist(SharedDataMerge.merge(base, doc(task()), base)))
        val raw = doc(mapOf("id" to "t"))
        assertEquals(listOf(item()), checklist(SharedDataMerge.merge(base, doc(task()), raw, raw)))
    }
    @Test fun duplicateIdsWithinOneChecklistRejectedEvenOnUnchangedTask() {
        val bad = doc(task(checklist = listOf(item(), item())))
        rejected { SharedDataMerge.merge(bad, bad, bad) }
    }
}
