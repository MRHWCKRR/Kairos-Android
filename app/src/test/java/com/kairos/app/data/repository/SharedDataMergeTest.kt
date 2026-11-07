package com.kairos.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SharedDataMergeTest {
    private fun task(id: String = "t1", title: String = "Before") =
        mapOf<String, Any?>("id" to id, "title" to title, "completed" to false, "priority" to 0)
    private fun document(vararg tasks: Map<String, Any?>) = mapOf<String, Any?>("scheduleEvents" to tasks.toList())
    private fun conflict(block: () -> Unit) {
        try { block(); fail("Expected SharedDataConflict") } catch (_: SharedDataConflict) { }
    }
    @Suppress("UNCHECKED_CAST")
    private fun tasks(document: Map<String, Any?>) = document["scheduleEvents"] as List<Map<String, Any?>>

    @Test fun preservesRichUnknownTaskAndDocumentFields() {
        val base = document(task())
        val extras = mapOf(
            "notes" to mapOf("html" to "<p>Keep</p>", "attachments" to listOf("file")),
            "checklists" to listOf(mapOf("text" to "Step", "checked" to true)),
            "recurrence" to mapOf("rule" to "FREQ=WEEKLY", "exceptions" to listOf("date")),
            "calendarMetadata" to mapOf("provider" to "icloud", "etag" to "abc")
        )
        val remote = document(task() + extras) + ("iosMetadata" to mapOf("version" to 4))
        val merged = SharedDataMerge.merge(base, document(task(title = "After")), remote)
        assertEquals(task(title = "After") + extras, tasks(merged).single())
        assertEquals(remote["iosMetadata"], merged["iosMetadata"])
    }

    @Test fun notificationSettingsMapIsNotAnIdentityCollection() {
        val base = mapOf<String, Any?>("settings" to mapOf("notifications" to mapOf("enabled" to true)))
        val edited = mapOf<String, Any?>("settings" to mapOf("notifications" to mapOf("enabled" to false)))
        val remote = mapOf<String, Any?>("settings" to mapOf("notifications" to mapOf("enabled" to true, "future" to "keep")))
        assertEquals(mapOf("settings" to mapOf("notifications" to mapOf("enabled" to false, "future" to "keep"))), SharedDataMerge.merge(base, edited, remote))
    }

    @Test fun combinesDisjointTaskChanges() {
        val merged = SharedDataMerge.merge(document(task()), document(task(title = "After")),
            document(task() + ("completed" to true)))
        assertEquals("After", tasks(merged).single()["title"])
        assertEquals(true, tasks(merged).single()["completed"])
    }

    @Test fun rejectsSameLeafConcurrentChanges() {
        conflict { SharedDataMerge.merge(document(task()), document(task(title = "Mine")), document(task(title = "Theirs"))) }
    }

    @Test fun mergesNestedSettingsAndPreservesUnknownSettings() {
        val base = mapOf("settings" to mapOf("sound" to "bell", "duration" to 25))
        val edited = mapOf("settings" to mapOf("sound" to "rain", "duration" to 25))
        val remote = mapOf("settings" to mapOf("sound" to "bell", "duration" to 50L, "ios" to mapOf("volume" to .8)))
        val result = SharedDataMerge.merge(base, edited, remote)
        assertEquals(mapOf("sound" to "rain", "duration" to 50L, "ios" to mapOf("volume" to .8)), result["settings"])
    }

    @Test fun preservesRemoteOrderAndAdditions() {
        val base = document(task("a"), task("b"))
        val edited = document(task("b"), task("a", "Edited"), task("local"))
        val remote = document(task("remote"), task("b"), task("a"))
        val merged = tasks(SharedDataMerge.merge(base, edited, remote))
        assertEquals(listOf("remote", "b", "a", "local"), merged.map { it["id"] })
        assertEquals("Edited", merged[2]["title"])
    }

    @Test fun additionRetryIsIdempotent() {
        val base = document()
        val edited = document(task())
        assertEquals(edited, SharedDataMerge.merge(base, edited, edited))
    }

    @Test fun rejectsAdditionCollidingWithDifferentRemoteRecord() {
        conflict { SharedDataMerge.merge(document(), document(task()), document(task(title = "Other"))) }
        conflict { SharedDataMerge.merge(document(), document(task()), document(task() + ("unknown" to true))) }
    }

    @Test fun deletionAndDeletionRetryAreIdempotent() {
        val base = document(task())
        assertEquals(document(), SharedDataMerge.merge(base, document(), base))
        assertEquals(document(), SharedDataMerge.merge(base, document(), document()))
    }

    @Test fun deletionRejectsChangedKnownRecord() {
        conflict { SharedDataMerge.merge(document(task()), document(), document(task(title = "Other"))) }
    }

    @Test fun rawBaselineProtectsUnknownFieldsOnDeletion() {
        val base = document(task())
        val raw = document(task() + ("notes" to "Before"))
        assertEquals(document(), SharedDataMerge.merge(base, document(), raw, raw))
        conflict { SharedDataMerge.merge(base, document(), document(task() + ("notes" to "After")), raw) }
    }

    @Test fun remotelyDeletedUntouchedItemIsNotResurrected() {
        val base = document(task("a"), task("b"))
        val edited = document(task("a"), task("b", "Changed"))
        val result = SharedDataMerge.merge(base, edited, document(task("b")))
        assertEquals(listOf("b"), tasks(result).map { it["id"] })
    }

    @Test fun remotelyDeletedEditedItemConflicts() {
        conflict { SharedDataMerge.merge(document(task()), document(task(title = "Changed")), document()) }
    }

    @Test fun duplicatesAndMissingIdentityConflictEvenIfUntouched() {
        val base = document(task())
        conflict { SharedDataMerge.merge(base, base, document(task(), task())) }
        conflict { SharedDataMerge.merge(document(task(), task()), base, base) }
        conflict { SharedDataMerge.merge(base, document(task(), task()), base) }
        conflict { SharedDataMerge.merge(base, base, document(mapOf("id" to " "))) }
        conflict { SharedDataMerge.merge(base, base, document(mapOf("title" to "Missing"))) }
    }

    @Test fun untouchedUnknownOnlyListsSurvive() {
        val base = mapOf<String, Any?>("title" to "Before")
        val remote = base + ("plugins" to listOf(mapOf("unidentified" to "keep")))
        assertEquals(remote + ("title" to "After"), SharedDataMerge.merge(base, mapOf("title" to "After"), remote))
    }

    @Test fun nonIdentityListsAreAtomic() {
        val base = mapOf<String, Any?>("tags" to listOf("one"))
        val edited = mapOf<String, Any?>("tags" to listOf("two"))
        conflict { SharedDataMerge.merge(base, edited, mapOf("tags" to listOf("three"))) }
        assertEquals(edited, SharedDataMerge.merge(base, edited, base))
    }

    @Test fun firestoreLongAndModelIntAreEquivalent() {
        val base = document(task())
        val remote = document(task() + ("priority" to 0L))
        assertEquals(1, tasks(SharedDataMerge.merge(base, document(task() + ("priority" to 1)), remote)).single()["priority"])
        assertEquals(remote, SharedDataMerge.merge(base, document(task() + ("priority" to 0L)), remote))
    }

    @Test fun missingDefaultsCanBeEditedWithoutMaterializingOtherDefaults() {
        val base = document(task())
        val raw = document(mapOf("id" to "t1", "title" to "Before"))
        val result = tasks(SharedDataMerge.merge(base, document(task() + ("completed" to true)), raw, raw)).single()
        assertTrue(result["completed"] == true)
        assertFalse(result.containsKey("priority"))
    }

    @Test fun explicitNullIsNotMissingDefault() {
        val base = document(task())
        conflict { SharedDataMerge.merge(base, document(task() + ("priority" to 1)), document(task() + ("priority" to null))) }
    }

    @Test fun rawBaselineDistinguishesRemoteFieldDeletionFromAbsentDefault() {
        val base = mapOf<String, Any?>("title" to "Before")
        conflict { SharedDataMerge.merge(base, mapOf("title" to "After"), emptyMap(), base) }
        assertEquals(mapOf("title" to "After"), SharedDataMerge.merge(base, mapOf("title" to "After"), emptyMap(), emptyMap()))
    }

    @Test fun recursivelyMergesAllRootIdentityCollectionTypes() {
        for (key in listOf("boards", "scheduleEvents", "notifications")) {
            val old = mapOf<String, Any?>("id" to "record", "title" to "Before")
            val base = mapOf<String, Any?>(key to listOf(old))
            val edited = mapOf<String, Any?>(key to listOf(old + ("title" to "After")))
            val remote = mapOf<String, Any?>(key to listOf(old + ("future" to listOf("keep"))))
            assertEquals(mapOf(key to listOf(old + mapOf("title" to "After", "future" to listOf("keep")))), SharedDataMerge.merge(base, edited, remote))
        }
    }

    @Test fun taskIdsAreUniqueAcrossSectionsInEveryInput() {
        fun plan(secondId: String) = mapOf<String, Any?>("boards" to listOf(mapOf(
            "id" to "b", "sections" to listOf(
                mapOf("id" to "s1", "tasks" to listOf(task("t1"))),
                mapOf("id" to "s2", "tasks" to listOf(task(secondId)))
            )
        )))
        val valid = plan("t2")
        val ambiguous = plan("t1")
        conflict { SharedDataMerge.merge(ambiguous, valid, valid) }
        conflict { SharedDataMerge.merge(valid, ambiguous, valid) }
        conflict { SharedDataMerge.merge(valid, valid, ambiguous) }
    }

    @Test fun sectionIdsAreUniqueAcrossBoards() {
        val doc = mapOf<String, Any?>("boards" to listOf(
            mapOf("id" to "b1", "sections" to listOf(mapOf("id" to "s"))),
            mapOf("id" to "b2", "sections" to listOf(mapOf("id" to "s")))
        ))
        conflict { SharedDataMerge.merge(doc, doc, doc) }
    }

    @Test fun concurrentAdditionsCannotCreateGlobalIdentityCollision() {
        fun plan(left: List<Map<String, Any?>>, right: List<Map<String, Any?>>) =
            mapOf<String, Any?>("boards" to listOf(mapOf("id" to "b", "sections" to listOf(
                mapOf("id" to "left", "tasks" to left), mapOf("id" to "right", "tasks" to right)
            ))))
        conflict {
            SharedDataMerge.merge(plan(emptyList(), emptyList()),
                plan(listOf(task()), emptyList()), plan(emptyList(), listOf(task())))
        }
    }

    @Test fun identityNamespacesAreSeparateByRecordKind() {
        val doc = mapOf<String, Any?>(
            "boards" to listOf(mapOf("id" to "same", "sections" to listOf(mapOf(
                "id" to "same", "tasks" to listOf(task("same"))
            )))), "notifications" to listOf(mapOf("id" to "same"))
        )
        assertEquals(doc, SharedDataMerge.merge(doc, doc, doc))
    }

    @Test fun nestedDuplicateIdentitiesConflictEvenWhenUnchanged() {
        val section = mapOf<String, Any?>("id" to "s", "tasks" to listOf(task(), task()))
        val board = mapOf<String, Any?>("id" to "b", "sections" to listOf(section))
        val doc = mapOf<String, Any?>("boards" to listOf(board))
        conflict { SharedDataMerge.merge(doc, doc, doc) }
    }

    @Test fun emptyAndMissingCollectionsAreSafe() {
        val base = document()
        assertEquals(emptyMap<String, Any?>(), SharedDataMerge.merge(base, base, emptyMap()))
        assertEquals(document(task()), SharedDataMerge.merge(base, document(task()), emptyMap()))
    }

    @Test fun changedLeafRetryPreservesUnknownFields() {
        val base = document(task())
        val edited = document(task(title = "After"))
        val remote = document(task(title = "After") + ("notes" to "Keep"))
        assertEquals(remote, SharedDataMerge.merge(base, edited, remote, base))
    }

    @Test fun nestedBoardSectionsAndTasksPreserveUnknownData() {
        fun board(t: Map<String, Any?>) = mapOf<String, Any?>("id" to "board", "sections" to listOf(mapOf("id" to "section", "tasks" to listOf(t))))
        val base = mapOf<String, Any?>("boards" to listOf(board(task())))
        val edited = mapOf<String, Any?>("boards" to listOf(board(task(title = "After"))))
        val remote = mapOf<String, Any?>("boards" to listOf(board(task() + ("notes" to "Keep")) + ("calendar" to "Keep")))
        val expected = mapOf<String, Any?>("boards" to listOf(board(task(title = "After") + ("notes" to "Keep")) + ("calendar" to "Keep")))
        assertEquals(expected, SharedDataMerge.merge(base, edited, remote))
    }
    private fun message(text: String, timestamp: String = text): Map<String, Any?> =
        mapOf("role" to "assistant", "content" to text, "timestamp" to timestamp)

    private fun history(vararg messages: Map<String, Any?>): Map<String, Any?> =
        mapOf("aiChatHistory" to messages.toList())

    @Test fun chatAppendPreservesUnknownRawMessageFields() {
        val base = history(message("old"))
        val raw = history(message("old") + ("iosModel" to mapOf("name" to "model", "tokens" to 50)))
        val edited = history(message("old"), message("new"))
        val expected = history(message("old") + ("iosModel" to mapOf("name" to "model", "tokens" to 50)), message("new"))
        assertEquals(expected, SharedDataMerge.merge(base, edited, raw, raw))
        assertEquals(expected, SharedDataMerge.merge(base, edited, expected, raw))
    }

    @Test fun chatClearWithUnknownRawMessageFieldsSucceedsAndRetries() {
        val base = history(message("old"))
        val raw = history(message("old") + ("iosModel" to "keep until removed"))
        assertEquals(history(), SharedDataMerge.merge(base, history(), raw, raw))
        assertEquals(history(), SharedDataMerge.merge(base, history(), history(), raw))
    }

    @Test fun chatConcurrentAdditionsConflictOnAppendAndClear() {
        val base = history(message("old"))
        val raw = history(message("old") + ("iosModel" to "keep"))
        val remote = history(message("old") + ("iosModel" to "keep"), message("remote"))
        conflict { SharedDataMerge.merge(base, history(message("old"), message("local")), remote, raw) }
        conflict { SharedDataMerge.merge(base, history(), remote, raw) }
    }

    @Test fun chatClearRejectsConcurrentUnknownMetadataChanges() {
        val base = history(message("old"))
        val raw = history(message("old") + ("iosModel" to "before"))
        val remote = history(message("old") + ("iosModel" to "after"))
        conflict { SharedDataMerge.merge(base, history(), remote, raw) }
    }

    @Test fun chatTrimmingPreservesRetainedMessageMetadata() {
        val base = history(message("first"), message("second"))
        val raw = history(message("first") + ("iosModel" to "one"), message("second") + ("iosModel" to "two"))
        val edited = history(message("second"), message("new"))
        assertEquals(history(message("second") + ("iosModel" to "two"), message("new")), SharedDataMerge.merge(base, edited, raw, raw))
    }

    @Test fun chatRetryCannotSilentlyDropRetainedMetadata() {
        val base = history(message("old"))
        val raw = history(message("old") + ("iosModel" to "keep"))
        val edited = history(message("old"), message("new"))
        conflict { SharedDataMerge.merge(base, edited, edited, raw) }
    }

    @Test fun unknownSubtreesDoNotInterpretSchemaCollectionNames() {
        val plugin = mapOf("tasks" to listOf("opaque", "values"),
            "notifications" to listOf(mapOf("id" to "same"), mapOf("id" to "same")))
        val base = mapOf<String, Any?>("title" to "Before", "boards" to listOf(mapOf("id" to "same", "title" to "Board")))
        val remote = base + ("plugin" to plugin)
        val edited = base + ("title" to "After")
        assertEquals(remote + ("title" to "After"), SharedDataMerge.merge(base, edited, remote))
        val rawBoard = mapOf("id" to "same", "title" to "Board", "plugin" to plugin)
        val raw = base + ("boards" to listOf(rawBoard))
        val boardEdit = base + ("boards" to listOf(mapOf("id" to "same", "title" to "Changed")))
        assertEquals(base + ("boards" to listOf(rawBoard + ("title" to "Changed"))), SharedDataMerge.merge(base, boardEdit, raw))
    }

}
