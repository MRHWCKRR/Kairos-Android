package com.kairos.app.data.repository

import org.junit.Assert.*
import org.junit.Test

class AiPersistenceTest {
    private val workspace = mapOf("version" to 2, "projects" to emptyList<Any>(), "webOnly" to "preserve", "chats" to listOf(
        mapOf("id" to "chat", "title" to "Study", "summary" to "Keep memory", "messages" to emptyList<Any>())))
    private val user: Map<String, Any?> = mapOf("aiWorkspace" to workspace, "other" to "unchanged")
    private val plan: Map<String, Any?> = mapOf("boards" to listOf(mapOf("id" to "b", "title" to "Board", "sections" to listOf(
        mapOf("id" to "s", "title" to "Section", "tasks" to listOf(mapOf("id" to "t", "title" to "Read", "completed" to false)))))))
    private fun edit(type: String, fields: Map<String, Any?>) = PendingEdit("op", "owner", "ai", "owner", true, emptyMap(),
        fields + mapOf("operation" to type, "createdAt" to 1000L), emptyMap())
    private fun proposal() = AiActions.make(listOf(AiActions.Action("complete_task", mapOf("taskId" to "t"))), plan, "owner", "chat", "plan", "UTC", "Complete Read")
    private fun reply(preview: AiActions.Proposal) = edit("reply", mapOf("proposal" to AiActions.toMap(preview), "command" to AiWorkspace.encodeCommand(
        AiWorkspace.Command.Append("chat", listOf(AiWorkspace.Message("reply", "assistant", "Review the change.", 1000L))))))

    @Test fun workspaceWritesAreScopedAndKeepWebMetadata() {
        val patch = AiPersistence.userPatch(edit("workspace", mapOf("command" to AiWorkspace.encodeCommand(
            AiWorkspace.Command.RenameChat("chat", "New title")))), user)
        assertEquals(setOf("aiWorkspace"), patch.keys)
        val read = AiWorkspace.read(user + patch, "owner")
        assertEquals("New title", read.chats.single().title)
        assertEquals("Keep memory", read.chats.single().summary)
        assertEquals("preserve", (patch["aiWorkspace"] as Map<*, *>)["webOnly"])
    }
    @Test fun retiredPreviewCannotReturnThroughALateReplyRetry() {
        val preview = proposal()
        val saved = user + AiPersistence.userPatch(reply(preview), user)
        assertEquals(preview.id, AiPersistence.proposals(saved).getValue("chat").id)
        val discarded = saved + AiPersistence.userPatch(edit("discard", mapOf("proposal" to AiActions.toMap(preview))), saved)
        assertTrue(AiPersistence.proposals(discarded).isEmpty())
        assertTrue(AiPersistence.userPatch(reply(preview), discarded).isEmpty())
    }
    @Test fun anotherPendingPreviewOrDeletedChatCannotBeOverwritten() {
        val first = proposal()
        val saved = user + AiPersistence.userPatch(reply(first), user)
        assertThrows(IllegalArgumentException::class.java) { AiPersistence.userPatch(reply(proposal()), saved) }
        val deleted = saved + AiPersistence.userPatch(edit("workspace", mapOf("command" to AiWorkspace.encodeCommand(AiWorkspace.Command.DeleteChat("chat")))), saved)
        assertTrue(AiWorkspace.read(deleted, "owner").chats.isEmpty())
        assertTrue(AiPersistence.userPatch(reply(first), deleted).isEmpty())
    }
    @Test fun userMessagesWaitForSavedReviewAndMalformedPreviewsFailClosed() {
        val saved = user + AiPersistence.userPatch(reply(proposal()), user)
        val append = edit("workspace", mapOf("command" to AiWorkspace.encodeCommand(AiWorkspace.Command.Append("chat", listOf(AiWorkspace.Message("next", "user", "Continue"))))))
        assertThrows(IllegalArgumentException::class.java) { AiPersistence.userPatch(append, saved) }
        assertThrows(IllegalArgumentException::class.java) { AiPersistence.userPatch(append, user + ("aiNativeProposals" to "broken")) }
    }
}
