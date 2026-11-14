package com.kairos.app.data.repository

import org.junit.Assert.*
import org.junit.Test

class AiActionsTest {
    private fun plan(task: Map<String, Any?> = emptyMap(), events: List<Map<String, Any?>> = emptyList()): Map<String, Any?> = mapOf(
        "webMetadata" to mapOf("keep" to true),
        "boards" to listOf(mapOf("id" to "b", "title" to "Study", "custom" to "board", "sections" to listOf(mapOf(
            "id" to "s", "title" to "Work", "custom" to "section", "tasks" to listOf(mapOf(
                "id" to "t", "title" to "Essay", "date" to "2026-11-05", "startTime" to "09:00", "endTime" to "10:00", "webMetadata" to "keep"
            ) + task)
        )))), "scheduleEvents" to events
    )
    private fun action(type: String, vararg args: Pair<String, Any?>) = AiActions.Action(type, mapOf(*args))
    private fun proposal(actions: List<AiActions.Action>, source: Map<String, Any?> = plan(), allowCompleted: Boolean = false) =
        AiActions.make(actions, source, "u", "c", "p", "Australia/Brisbane", "Please review", allowCompleted)
    private fun task(source: Map<String, Any?>): Map<String, Any?> = maps(maps(maps(source["boards"])[0]["sections"])[0]["tasks"])[0]
    @Suppress("UNCHECKED_CAST")
    private fun maps(value: Any?) = value as List<Map<String, Any?>>
    private fun rejected(block: () -> Unit) { assertThrows(Exception::class.java, block) }

    @Test fun strictResponseRejectsUnknownNullInvalidAndDuplicateTargets() {
        val valid = """{"reply":"Review","actions":[{"type":"set_task_reminder","args":{"taskId":"t","minutesBefore":15}}]}"""
        assertEquals(15L, AiActions.parse(valid).actions.single().args["minutesBefore"])
        rejected { AiActions.parse(valid.replace("\"minutesBefore\":15", "\"minutesBefore\":null")) }
        rejected { AiActions.parse(valid.replace("\"minutesBefore\":15", "\"minutesBefore\":15,\"extra\":true")) }
        rejected { AiActions.parse(valid.replace("\"minutesBefore\":15", "\"minutesBefore\":1.5")) }
        rejected { AiActions.parse("""{"reply":"Review","actions":null}""") }
        rejected { AiActions.parse("""{"reply":"Review","reply":"Hidden","actions":[]}""") }
        rejected { AiActions.parse(valid.replace("\"taskId\":\"t\"", "\"taskId\":\"t\",\"taskId\":\"other\"")) }
        rejected { proposal(listOf(action("complete_task", "taskId" to "t"), action("delete_task", "taskId" to "t"))) }
        rejected { proposal(listOf(action("complete_task", "taskId" to "missing"))) }
        rejected { proposal(listOf(action("update_task", "taskId" to "t", "date" to "2026-02-30"))) }
        rejected { proposal(listOf(action("update_task", "taskId" to "t", "startTime" to "24:00"))) }
    }

    @Test fun foundationCanonicalFingerprintsAndFractionalPreviewTimestampMatchIOS() {
        // Generated with Foundation JSONSerialization [.sortedKeys, .withoutEscapingSlashes] + CryptoKit.
        assertEquals("758f5d556f5892e5b35619f167f82c97b3aa353a1cb1046480b3cbbee691e94f", proposal(listOf(action("create_board", "title" to "New"))).beforeFingerprint)
        val preview = AiActions.Proposal("proposal-fixture", "u", "c", "p", "UTC", 1700000000000.25, "0".repeat(64), "Read / \"quoted\" 日本語", listOf(action("create_board", "title" to "New")), listOf(AiActions.Row("proposal-fixture-0", "New", "Create Board", "Not created", "New", false)))
        assertEquals("3cbc05cc6d1c1dd14db3c11be6af51e4f3f9c0ad4e9d55cd3e1b4346dec63d53", AiActions.payloadFingerprint(preview))
        assertEquals(preview, AiActions.fromMap(AiActions.toMap(preview)))
        assertEquals(1700000000000.25, AiActions.receipt(preview)["createdAt"])
    }

    @Test fun previewRoundTripPreservesRawFieldsAndDetectsStaleState() {
        val source = plan()
        val preview = proposal(listOf(action("set_task_reminder", "taskId" to "t", "minutesBefore" to 15)), source)
        val restored = AiActions.fromMap(AiActions.toMap(preview))
        assertEquals(preview, restored)
        val result = AiActions.prepare(restored, source)
        assertEquals("Essay", restored.rows.single().title)
        assertEquals(15L, task(result.after)["reminderMinutes"])
        assertEquals("keep", task(result.after)["webMetadata"])
        assertEquals(source["webMetadata"], result.after["webMetadata"])
        rejected { AiActions.prepare(restored, plan(mapOf("title" to "Changed remotely"))) }
        rejected { AiActions.prepare(restored.copy(rows = restored.rows.map { it.copy(after = "Different preview") }), source) }
    }

    @Test fun deterministicCreationAndReceiptsPreventIdentityReuseAndOldRetries() {
        val preview = proposal(listOf(action("create_board", "title" to "New")))
        val first = AiActions.prepare(preview, plan())
        assertEquals(first.after, AiActions.prepare(preview, plan()).after)
        assertEquals("board-${preview.id}-0-0", maps(first.after["boards"]).last()["id"])
        val receipt = AiActions.receipt(preview)
        assertTrue(AiActions.wasApplied(preview, mapOf("aiNativeActionReceipts" to listOf(receipt))))
        assertFalse(AiActions.wasApplied(preview, emptyMap()))
        rejected { AiActions.wasApplied(preview.copy(request = "Other request"), mapOf("aiNativeActionReceipts" to listOf(receipt))) }
        rejected { AiActions.wasApplied(preview, mapOf("aiNativeActionReceiptFloor" to preview.createdAt)) }
    }

    @Test fun taskScheduleLocksCompletionAndSeriesScopeAreEnforced() {
        val move = listOf(action("update_task", "taskId" to "t", "startTime" to "11:00"))
        rejected { proposal(move, plan(mapOf("scheduleLocked" to true))) }
        rejected { proposal(move, plan(mapOf("completed" to true))) }
        val done = plan(mapOf("completed" to true))
        assertEquals("12:00", task(AiActions.prepare(proposal(move, done, true), done).after)["endTime"])
        rejected { proposal(listOf(action("delete_task", "taskId" to "t")), plan(mapOf("recurrence" to recurrence()))) }
    }

    private fun recurrence(exceptions: Map<String, Any?> = emptyMap()) = mapOf("enabled" to true, "frequency" to "daily", "mode" to "fixed", "startDate" to "2026-11-05", "endType" to "never", "exceptions" to exceptions, "customRule" to "keep")

    @Test fun recurringOccurrenceMovesOnlyOriginalIdentityAndKeepsHistory() {
        val source = plan(mapOf("recurrence" to recurrence(mapOf(
            "2026-11-05" to mapOf("custom" to "keep", "focusLoggedSeconds" to 90),
            "2026-11-06" to mapOf("status" to "completed", "custom" to "history")
        ))))
        val edit = listOf(action("update_task_occurrence", "taskId" to "t", "occurrenceDate" to "2026-11-05", "date" to "2026-11-07", "startTime" to "11:00", "status" to "completed"))
        val preview = proposal(edit, source)
        val result = task(AiActions.prepare(preview, source).after)
        assertEquals("2026-11-05", result["date"])
        val rule = result["recurrence"] as Map<*, *>
        assertEquals("keep", rule["customRule"])
        val exceptions = rule["exceptions"] as Map<*, *>
        val selected = exceptions["2026-11-05"] as Map<*, *>
        assertEquals("completed", selected["status"])
        assertEquals("keep", selected["custom"])
        assertEquals(90, selected["focusLoggedSeconds"])
        assertEquals("2026-11-07", (selected["override"] as Map<*, *>)["date"])
        assertEquals("12:00", (selected["override"] as Map<*, *>)["endTime"])
        assertEquals("history", (exceptions["2026-11-06"] as Map<*, *>)["custom"])
        rejected { proposal(edit, plan(mapOf("recurrence" to recurrence(mapOf("2026-11-05" to mapOf("status" to "completed")))))) }
    }

    @Test fun recurrenceBoundsAndHistoricalExceptionsAreValidated() {
        val edit = listOf(action("update_task_occurrence", "taskId" to "t", "occurrenceDate" to "2026-11-06", "status" to "completed"))
        rejected { proposal(edit, plan(mapOf("recurrence" to (recurrence() + mapOf("endType" to "count", "count" to 1))))) }
        val detached = plan(mapOf("recurrence" to (recurrence(mapOf("2026-11-06" to mapOf("status" to "skipped", "custom" to true))) + mapOf("endType" to "count", "count" to 1))))
        val preview = proposal(edit, detached)
        assertTrue(preview.rows.single().after.contains("completed"))
        rejected { proposal(edit, plan(mapOf("recurrence" to (recurrence() + mapOf("frequency" to "unknown"))))) }
        rejected { proposal(edit, plan(mapOf("recurrence" to (recurrence() + mapOf("frequency" to null))))) }
        rejected { proposal(edit, plan(mapOf("recurrence" to (recurrence() + mapOf("preset" to "weekdays", "weekdays" to listOf("bad")))))) }
    }

    @Test fun seriesReminderClearsOnlyReminderOverrides() {
        val source = plan(mapOf("recurrence" to recurrence(mapOf("2026-11-05" to mapOf("custom" to "keep", "override" to mapOf("reminderMinutes" to 60, "date" to "2026-11-07", "unknown" to "keep"))))))
        val preview = proposal(listOf(action("set_task_reminder", "taskId" to "t", "minutesBefore" to 15)), source)
        val changed = task(AiActions.prepare(preview, source).after)
        val exception = (((changed["recurrence"] as Map<*, *>)["exceptions"] as Map<*, *>)["2026-11-05"] as Map<*, *>)
        assertEquals("keep", exception["custom"])
        val override = exception["override"] as Map<*, *>
        assertFalse(override.containsKey("reminderMinutes"))
        assertEquals("2026-11-07", override["date"])
        assertEquals("keep", override["unknown"])
    }

    @Test fun weeklyEventMoveAndDeleteAffectOnlySelectedOccurrence() {
        val event = mapOf("id" to "e", "title" to "Class", "day" to 4, "start" to "09:00", "end" to "10:00", "color" to "purple", "exceptions" to mapOf("2026-11-12" to mapOf("custom" to "keep")))
        val source = plan(events = listOf(event))
        val edit = listOf(action("update_schedule_event", "eventId" to "e", "occurrenceDate" to "2026-11-05", "date" to "2026-11-06", "start" to "11:00"))
        val preview = proposal(edit, source)
        assertTrue(preview.rows.single().after.contains("11:00–12:00"))
        val changed = AiActions.prepare(preview, source).after
        val updated = maps(changed["scheduleEvents"]).single()
        assertEquals("09:00", updated["start"])
        assertEquals("purple", updated["color"])
        assertEquals(mapOf("custom" to "keep"), (updated["exceptions"] as Map<*, *>)["2026-11-12"])
        val deletion = proposal(listOf(action("delete_schedule_event", "eventId" to "e", "occurrenceDate" to "2026-11-05")), changed)
        val skipped = maps(AiActions.prepare(deletion, changed).after["scheduleEvents"]).single()
        assertEquals(true, ((skipped["exceptions"] as Map<*, *>)["2026-11-05"] as Map<*, *>)["skipped"])
        rejected { proposal(listOf(action("delete_schedule_event", "eventId" to "e")), source) }
        rejected { proposal(edit.map { it.copy(args = it.args + ("occurrenceDate" to "2026-11-06")) }, source) }
    }

    @Test fun readOnlyImportedLockedAndAlternatingEventsRejectUnsafeEdits() {
        val event = mapOf("id" to "e", "title" to "Class", "date" to "2026-11-05", "start" to "09:00", "end" to "10:00")
        val edit = listOf(action("delete_schedule_event", "eventId" to "e"))
        for (protection in listOf(mapOf("readOnly" to true), mapOf("calendarSourceId" to "feed"), mapOf("scheduleLocked" to true), mapOf("locked" to true))) rejected { proposal(edit, plan(events = listOf(event + protection))) }
        val weekly = event.filterKeys { it != "date" } + mapOf("day" to 4, "intervalWeeks" to 2, "anchorDate" to "2026-11-05")
        rejected { proposal(listOf(action("delete_schedule_event", "eventId" to "e", "occurrenceDate" to "2026-11-12")), plan(events = listOf(weekly))) }
    }

    @Test fun guardedUndoPreservesUnrelatedEditsAndRejectsAffectedChanges() {
        val source = plan()
        val preview = proposal(listOf(action("update_task", "taskId" to "t", "newTitle" to "New essay")), source)
        val after = AiActions.prepare(preview, source).after
        val current = after + mapOf("other" to "remote")
        val undone = AiActions.undo(source, after, current)
        assertEquals("Essay", task(undone)["title"])
        assertEquals("remote", undone["other"])
        rejected { AiActions.undo(source, after, plan(mapOf("title" to "Third title"))) }
    }

    @Test fun authoredBoardSectionAndTaskLifecycleUsesExactParentIds() {
        var source = plan()
        fun apply(change: AiActions.Action): AiActions.Proposal {
            val preview = proposal(listOf(change), source)
            source = AiActions.prepare(preview, source).after
            return preview
        }
        apply(action("create_board", "title" to "New board"))
        val boardId = maps(source["boards"]).last()["id"] as String
        val sectionPreview = apply(action("create_section", "boardId" to boardId, "title" to "New section"))
        assertEquals("New board", sectionPreview.rows.single().title)
        val sectionId = maps(maps(source["boards"]).last()["sections"]).single()["id"] as String
        val taskPreview = apply(action("create_task", "sectionId" to sectionId, "title" to "New task", "startTime" to "13:00"))
        assertEquals("New section", taskPreview.rows.single().title)
        val taskId = maps(maps(maps(source["boards"]).last()["sections"]).single()["tasks"]).single()["id"] as String
        apply(action("complete_task", "taskId" to taskId))
        apply(action("delete_task", "taskId" to taskId))
        apply(action("update_section", "sectionId" to sectionId, "newTitle" to "Renamed section"))
        apply(action("delete_section", "boardId" to boardId, "sectionId" to sectionId))
        apply(action("update_board", "boardId" to boardId, "newTitle" to "Renamed board"))
        apply(action("delete_board", "boardId" to boardId))
        assertEquals(plan(), source)
    }

    @Test fun duplicatePlannerIdentitiesAndIncorrectSectionParentsAreRejected() {
        val source = plan()
        val board = maps(source["boards"]).single()
        rejected { proposal(listOf(action("complete_task", "taskId" to "t")), source + ("boards" to listOf(board, board))) }
        rejected { proposal(listOf(action("delete_section", "boardId" to "wrong", "sectionId" to "s")), source) }
        rejected { proposal(listOf(action("create_board", "title" to "New")), source + ("boards" to null)) }
    }
}
