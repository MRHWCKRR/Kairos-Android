package com.kairos.app.data.repository

import org.junit.Assert.*
import org.junit.Test

class AiWorkspaceTest {
    private fun workspace(chats: List<Map<String, Any?>> = listOf(chat())): Map<String, Any?> =
        mapOf("version" to 2L, "chats" to chats, "projects" to emptyList<Any>())
    private fun chat(messages: List<Map<String, Any?>> = emptyList()): Map<String, Any?> =
        mapOf("id" to "chat", "messages" to messages)
    private fun user(value: Map<String, Any?> = workspace()) = mapOf<String, Any?>("aiWorkspace" to value)
    private fun read(value: Map<String, Any?>) = AiWorkspace.read(value, "owner")
    private fun apply(value: Map<String, Any?>, command: AiWorkspace.Command) = AiWorkspace.apply(value, command, "owner", 100)

    @Test fun migratesLegacyOnceWithStableIdsAndPreservesRawMetadata() {
        val legacy = mapOf("usageId" to "receipt", "role" to "assistant", "content" to "Reply", "replies" to listOf("kept"))
        val source = mapOf("aiChatHistory" to listOf(legacy))
        assertEquals("legacy-owner", read(source).activeChatId)
        assertEquals("receipt", read(source).chats.single().messages.single().id)
        val migrated = apply(source, AiWorkspace.Command.RenameChat("legacy-owner", "History"))
        val rawChat = (migrated["chats"] as List<*>).single() as Map<*, *>
        assertEquals(legacy, (rawChat["messages"] as List<*>).single())
        assertEquals("History", read(user(migrated)).chats.single().title)
    }

    @Test fun preservesUnknownFieldsAndSummariesAndHonorsMemorySetting() {
        val message = mapOf("id" to "message", "role" to "assistant", "content" to "Reply", "usageId" to "usage", "replies" to listOf(1))
        val source = workspace(listOf(chat(listOf(message)) + mapOf("summary" to "Manual", "autoSummary" to "Auto", "receipt" to mapOf("a" to true)))) + ("future" to listOf("keep"))
        assertEquals("Manual\nAuto", read(user(source)).chats.single().summary)
        val changed = apply(user(source), AiWorkspace.Command.RenameChat("chat", "Renamed"))
        assertEquals(source["future"], changed["future"])
        val changedChat = (changed["chats"] as List<*>).single() as Map<*, *>
        assertEquals(message, (changedChat["messages"] as List<*>).single())
        assertEquals(mapOf("a" to true), changedChat["receipt"])
        val disabled = workspace(listOf(chat() + mapOf("summary" to "Manual", "autoSummary" to "Auto", "autoMemoryEnabled" to false)))
        assertEquals("Manual", read(user(disabled)).chats.single().summary)
    }

    @Test fun appendIsIdempotentButRejectsConflictingPayloadAndPendingWebUserMessages() {
        val original = mapOf("id" to "message", "usageId" to "usage", "role" to "assistant", "content" to "Reply", "timestamp" to 50L, "replies" to listOf("keep"))
        val source = user(workspace(listOf(chat(listOf(original)))))
        val updated = apply(source, AiWorkspace.Command.Append("chat", listOf(AiWorkspace.Message("usage", "assistant", "Reply"))))
        assertEquals(listOf(original), ((updated["chats"] as List<*>).single() as Map<*, *>)["messages"])
        assertThrows(IllegalArgumentException::class.java) {
            apply(source, AiWorkspace.Command.Append("chat", listOf(AiWorkspace.Message("message", "assistant", "Changed"))))
        }
        val pending = user(workspace(listOf(chat() + ("pendingActionBatch" to mapOf("id" to "batch")))))
        assertTrue(read(pending).chats.single().hasUnfinishedWebActions)
        assertThrows(IllegalArgumentException::class.java) {
            apply(pending, AiWorkspace.Command.Append("chat", listOf(AiWorkspace.Message("new", "user", "Continue"))))
        }
        assertEquals("Reply", read(user(apply(pending, AiWorkspace.Command.Append("chat", listOf(AiWorkspace.Message("reply", "assistant", "Reply")))))).chats.single().messages.single().content)
    }

    @Test fun rejectsCorruptShapesAndFutureVersionsWithoutLegacyFallback() {
        val invalid = listOf<Any?>(null, "broken", workspace() + ("version" to 3), workspace() + ("chats" to "broken"),
            workspace(listOf(chat(), chat())), workspace(listOf(chat(listOf(mapOf("role" to "tool", "content" to "bad"))))),
            workspace(listOf(chat() + ("summary" to listOf("bad")))), workspace() + ("projects" to listOf(mapOf("id" to ""))))
        invalid.forEach { value ->
            val source = mapOf("aiWorkspace" to value, "aiChatHistory" to emptyList<Any>())
            assertThrows(IllegalArgumentException::class.java) { read(source) }
            assertThrows(IllegalArgumentException::class.java) { apply(source, AiWorkspace.Command.CreateChat("new")) }
        }
    }

    @Test fun projectsMoveAndDeleteWithoutDeletingChatHistory() {
        val created = apply(user(), AiWorkspace.Command.SaveProject("project", "Name", "Description", "Context", true))
        val moved = apply(user(created), AiWorkspace.Command.MoveChat("chat", "project"))
        assertEquals("project", read(user(moved)).chats.single().projectId)
        val deleted = apply(user(moved), AiWorkspace.Command.DeleteProject("project"))
        assertTrue(read(user(deleted)).projects.isEmpty())
        assertEquals("", read(user(deleted)).chats.single().projectId)
        assertThrows(IllegalArgumentException::class.java) { apply(user(deleted), AiWorkspace.Command.MoveChat("chat", "missing")) }
    }

    @Test fun rollsHistoryAndRefusesChatCapacityAndUtf8Overflow() {
        val fullHistory = List(80) { mapOf("id" to "m$it", "role" to "assistant", "content" to "Reply") }
        val rolled = apply(user(workspace(listOf(chat(fullHistory)))), AiWorkspace.Command.Append("chat", listOf(AiWorkspace.Message("new", "assistant", "Next"))))
        assertEquals(80, read(user(rolled)).chats.single().messages.size)
        assertEquals("m1", read(user(rolled)).chats.single().messages.first().id)
        assertEquals("new", read(user(rolled)).chats.single().messages.last().id)
        val fullChats = List(12) { mapOf("id" to "c$it", "messages" to listOf(mapOf("role" to "user", "content" to "Hello"))) }
        assertThrows(IllegalArgumentException::class.java) { apply(user(workspace(fullChats)), AiWorkspace.Command.CreateChat("new")) }
        assertThrows(IllegalArgumentException::class.java) { apply(user(), AiWorkspace.Command.Append("chat", listOf(AiWorkspace.Message("big", "assistant", "界".repeat(270_000))))) }
        assertThrows(IllegalArgumentException::class.java) { apply(user(), AiWorkspace.Command.RenameChat("chat", "x".repeat(181))) }
        assertEquals(80, read(user(workspace(listOf(chat(fullHistory))))).chats.single().messages.size)
    }

    @Test fun rollingWindowPreservesFallbackIdentityAndMessageTotal() {
        val messages = List(80) { mapOf("role" to "user", "content" to "Message $it", "replies" to listOf(it)) }
        val source = user(workspace(listOf(chat(messages) + ("messageTotal" to 500L))))
        val changed = apply(source, AiWorkspace.Command.Append("chat", listOf(AiWorkspace.Message("new", "user", "Next"))))
        val snapshot = read(user(changed))
        assertEquals("chat-1", snapshot.chats.single().messages.first().id)
        val rawChat = (changed["chats"] as List<*>).single() as Map<*, *>
        assertEquals(501L, rawChat["messageTotal"])
        assertEquals(listOf(1), ((rawChat["messages"] as List<*>).first() as Map<*, *>)["replies"])
    }

    @Test fun journalCommandsRoundTripWithoutChangingMessageIdentity() {
        val commands = listOf(AiWorkspace.Command.CreateChat("new"), AiWorkspace.Command.RenameChat("chat", "Name"),
            AiWorkspace.Command.DeleteChat("chat"), AiWorkspace.Command.MoveChat("chat", "project"),
            AiWorkspace.Command.Append("chat", listOf(AiWorkspace.Message("m", "assistant", "Reply", 99, true))),
            AiWorkspace.Command.SaveProject("project", "Name", "Description", "Context", true), AiWorkspace.Command.DeleteProject("project"))
        val codec = PendingValueCodec()
        commands.forEach { command ->
            val edit = PendingEdit("operation", "owner", "aiWorkspace", "owner", true, emptyMap(), AiWorkspace.encodeCommand(command), emptyMap())
            val restored = AiWorkspace.decodeCommand(codec.decode(codec.encode(edit)).edited)
            assertEquals(command, restored)
        }
        val append = AiWorkspace.Command.Append("chat", listOf(AiWorkspace.Message("m", "assistant", "Reply")))
        val once = apply(user(), append)
        val restored = AiWorkspace.decodeCommand(AiWorkspace.encodeCommand(append))
        assertEquals(once, apply(user(once), restored))
        assertThrows(IllegalArgumentException::class.java) { AiWorkspace.decodeCommand(mapOf("type" to "append", "chatId" to "chat", "messages" to "bad")) }
    }

    @Test fun reusesBlankChatAndDoesNotMutateInputCollections() {
        val source = user()
        val created = apply(source, AiWorkspace.Command.CreateChat("new"))
        assertEquals("chat", created["activeChatId"])
        assertNull((source["aiWorkspace"] as Map<*, *>)["activeChatId"])
        val appended = apply(source, AiWorkspace.Command.Append("chat", listOf(AiWorkspace.Message("new", "user", "Hello", transportNotice = true))))
        val message = read(user(appended)).chats.single().messages.single()
        assertEquals(100L, message.timestamp)
        assertTrue(message.transportNotice)
        assertTrue(read(source).chats.single().messages.isEmpty())
    }
    @Test fun longLegacyConversationLoadsWithoutLossBeforeRollingAppend() {
        val legacy = (0 until 100).map { mapOf("role" to "user", "content" to "Message $it") }
        val source: Map<String, Any?> = mapOf("aiChatHistory" to legacy)
        val initial = AiWorkspace.read(source, "owner")
        assertEquals(100, initial.chats.single().messages.size)
        val renamed = AiWorkspace.apply(source, AiWorkspace.Command.RenameChat("legacy-owner", "Imported"), "owner")
        assertEquals(100, AiWorkspace.read(mapOf("aiWorkspace" to renamed), "owner").chats.single().messages.size)
        val appended = AiWorkspace.apply(mapOf("aiWorkspace" to renamed), AiWorkspace.Command.Append("legacy-owner", listOf(AiWorkspace.Message("next", "assistant", "New reply"))), "owner")
        val saved = AiWorkspace.read(mapOf("aiWorkspace" to appended), "owner").chats.single()
        assertEquals(80, saved.messages.size)
        assertEquals("New reply", saved.messages.last().content)
        assertEquals("Message 0", initial.chats.single().messages.first().content)
    }

    @Test fun oversizedExistingHistoryCanBeReadAndExplicitlyDeleted() {
        val source: Map<String, Any?> = mapOf("aiChatHistory" to listOf(mapOf("role" to "user", "content" to "界".repeat(300000))))
        assertEquals(1, AiWorkspace.read(source, "owner").chats.size)
        val changed = AiWorkspace.apply(source, AiWorkspace.Command.DeleteChat("legacy-owner"), "owner")
        assertTrue(AiWorkspace.read(mapOf("aiWorkspace" to changed), "owner").chats.isEmpty())
    }

}
