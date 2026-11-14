package com.kairos.app.data.repository

import kotlinx.serialization.json.*

/** Raw workspace commands preserve fields written by the other Kairos clients. */
object AiWorkspace {
    data class Message(val id: String, val role: String, val content: String, val timestamp: Long = 0, val transportNotice: Boolean = false)
    data class Chat(val id: String, val title: String, val projectId: String, val messages: List<Message>, val summary: String, val hasUnfinishedWebActions: Boolean, val order: Double)
    data class Project(val id: String, val title: String, val description: String, val sharedContext: String, val order: Double)
    data class Snapshot(val chats: List<Chat>, val projects: List<Project>, val activeChatId: String?)
    sealed class Command {
        data class CreateChat(val id: String, val projectId: String = "") : Command()
        data class RenameChat(val id: String, val title: String) : Command()
        data class DeleteChat(val id: String) : Command()
        data class MoveChat(val id: String, val projectId: String) : Command()
        data class Append(val chatId: String, val messages: List<Message>) : Command()
        data class SaveProject(val id: String, val title: String, val description: String, val context: String, val creating: Boolean) : Command()
        data class DeleteProject(val id: String) : Command()
    }

    private const val INVALID = "This AI workspace cannot be edited safely. Review it on the web and retry."
    private fun check(valid: Boolean, message: String = INVALID) = require(valid) { message }
    private fun map(value: Any?): Map<String, Any?> {
        check(value is Map<*, *> && value.keys.all { it is String })
        @Suppress("UNCHECKED_CAST")
        return value as Map<String, Any?>
    }
    private fun maps(value: Any?): List<Map<String, Any?>> {
        check(value is List<*>)
        return (value as List<*>).map(::map)
    }
    private fun id(value: Any?): String {
        check(value is String && value.isNotBlank())
        return value as String
    }
    private fun string(item: Map<String, Any?>, key: String) = item[key] as? String ?: ""
    private fun validateFields(item: Map<String, Any?>, strings: List<String>, numbers: List<String> = emptyList(), booleans: List<String> = emptyList()) {
        strings.forEach { check(item[it] == null || item[it] is String) }
        numbers.forEach { check(item[it] == null || item[it] is Number && (item[it] as Number).toDouble().isFinite()) }
        booleans.forEach { check(item[it] == null || item[it] is Boolean) }
    }
    private fun messageId(message: Map<String, Any?>, chatId: String, index: Int) =
        message["id"] as? String ?: message["usageId"] as? String ?: "$chatId-$index"

    private fun raw(user: Map<String, Any?>, ownerId: String): Map<String, Any?> {
        val value = if (user.containsKey("aiWorkspace")) map(user["aiWorkspace"]) else {
            val legacy = if (user.containsKey("aiChatHistory")) maps(user["aiChatHistory"]) else emptyList()
            val legacyId = "legacy-${id(ownerId)}"
            mapOf("version" to 2, "activeChatId" to legacyId.takeIf { legacy.isNotEmpty() }, "projects" to emptyList<Any>(),
                "chats" to if (legacy.isEmpty()) emptyList() else listOf(mapOf("id" to legacyId, "title" to "", "messages" to legacy, "order" to 0)))
        }
        check(!value.containsKey("version") || value["version"] is Number && (value["version"] as Number).toDouble() == 2.0)
        validateFields(value, listOf("activeChatId"))
        val chats = maps(value["chats"])
        val projects = if (value["projects"] == null) emptyList() else maps(value["projects"])
        for (items in listOf(chats, projects)) check(items.map { id(it["id"]) }.distinct().size == items.size)
        chats.forEach { chat ->
            validateFields(chat, listOf("title", "projectId", "summary", "autoSummary"), listOf("order", "createdAt", "updatedAt", "messageTotal"), listOf("autoMemoryEnabled"))
            if (chat["pendingActionBatch"] != null) map(chat["pendingActionBatch"])
            val messages = maps(chat["messages"])
            val identities = mutableSetOf<String>()
            messages.forEachIndexed { index, message ->
                check(message["role"] in listOf("user", "assistant") && message["content"] is String)
                validateFields(message, listOf("id", "usageId"), listOf("timestamp"), listOf("transportNotice"))
                listOf("id", "usageId").forEach { if (message[it] != null) id(message[it]) }
                val keys = setOfNotNull(message["id"] as? String, message["usageId"] as? String, messageId(message, id(chat["id"]), index))
                check(keys.none { it in identities })
                identities.addAll(keys)
            }
        }
        projects.forEach { validateFields(it, listOf("title", "description", "sharedContext"), listOf("order", "createdAt", "updatedAt")) }
        // Validate JSON compatibility before editing so unknown fields can never disappear in serialization.
        json(value)
        return value
    }

    fun read(user: Map<String, Any?>, ownerId: String): Snapshot {
        val value = raw(user, ownerId)
        val chats = maps(value["chats"]).mapIndexed { index, chat ->
            val chatId = id(chat["id"])
            val messages = maps(chat["messages"]).mapIndexed { messageIndex, message ->
                Message(messageId(message, chatId, messageIndex), string(message, "role"), string(message, "content"),
                    (message["timestamp"] as? Number)?.toLong() ?: 0, message["transportNotice"] == true)
            }
            Chat(chatId, string(chat, "title").trim().ifEmpty { (messages.firstOrNull { it.role == "user" }?.content ?: "New chat").take(42) },
                string(chat, "projectId"), messages, listOfNotNull(chat["summary"] as? String,
                    (chat["autoSummary"] as? String).takeUnless { chat["autoMemoryEnabled"] == false }).joinToString("\n"),
                chat["pendingActionBatch"] is Map<*, *>, (chat["order"] as? Number)?.toDouble() ?: index.toDouble())
        }.sortedBy { it.order }
        val projects = (value["projects"]?.let(::maps) ?: emptyList()).mapIndexed { index, project ->
            Project(id(project["id"]), project["title"] as? String ?: "New project", string(project, "description"), string(project, "sharedContext"),
                (project["order"] as? Number)?.toDouble() ?: index.toDouble())
        }.sortedBy { it.order }
        return Snapshot(chats, projects, value["activeChatId"] as? String)
    }

    /** Returns only the new aiWorkspace value; callers persist it against their fresh document. */
    fun apply(user: Map<String, Any?>, command: Command, ownerId: String, nowMillis: Long = System.currentTimeMillis()): Map<String, Any?> {
        val value = raw(user, ownerId).toMutableMap()
        val chats = maps(value["chats"]).map { it.toMutableMap() }.toMutableList()
        val projects = (value["projects"]?.let(::maps) ?: emptyList()).map { it.toMutableMap() }.toMutableList()
        fun chat(chatId: String): MutableMap<String, Any?> = chats.firstOrNull { it["id"] == id(chatId) }
            ?: throw IllegalArgumentException("This conversation was deleted on another device.")
        fun checkProject(projectId: String) = check(projectId.isEmpty() || projects.any { it["id"] == projectId }, "This project is no longer available.")
        when (command) {
            is Command.CreateChat -> {
                id(command.id)
                checkProject(command.projectId)
                val blank = chats.firstOrNull { maps(it["messages"]).isEmpty() && string(it, "projectId") == command.projectId && it["pendingActionBatch"] == null }
                if (blank != null) value["activeChatId"] = blank["id"] else {
                    check(chats.size < 12, "Your workspace has 12 chats. Delete a chat before creating another.")
                    check(chats.none { it["id"] == command.id })
                    chats.add(0, mutableMapOf("id" to command.id, "title" to "", "projectId" to command.projectId, "createdAt" to nowMillis,
                        "updatedAt" to nowMillis, "order" to ((chats.mapNotNull { (it["order"] as? Number)?.toDouble() }.minOrNull() ?: 0.0) - 1), "messages" to emptyList<Any>()))
                    value["activeChatId"] = command.id
                }
            }
            is Command.RenameChat -> {
                check(command.title.isNotBlank() && command.title.length <= 180, "Use a chat title from 1 to 180 characters.")
                chat(command.id).apply { this["title"] = command.title; this["updatedAt"] = nowMillis }
            }
            is Command.DeleteChat -> {
                chats.remove(chat(command.id))
                if (value["activeChatId"] == command.id) value["activeChatId"] = chats.firstOrNull()?.get("id")
            }
            is Command.MoveChat -> {
                checkProject(command.projectId)
                chat(command.id).apply { this["projectId"] = command.projectId; this["updatedAt"] = nowMillis }
            }
            is Command.Append -> {
                val target = chat(command.chatId)
                // Materialize fallback IDs before the rolling window changes message indices.
                val history = maps(target["messages"]).mapIndexed { index, message ->
                    if (message["id"] == null && message["usageId"] == null) message + ("id" to messageId(message, command.chatId, index)) else message
                }.toMutableList()
                command.messages.forEach { message ->
                    id(message.id)
                    check(message.role in listOf("user", "assistant") && message.content.isNotBlank(), "The message is empty or invalid.")
                    check(message.role != "user" || target["pendingActionBatch"] == null, "Finish or cancel this chat’s pending change on the web before continuing here.")
                    val existing = history.withIndex().firstOrNull { (index, item) -> message.id == messageId(item, command.chatId, index) || message.id == item["usageId"] }?.value
                    if (existing != null) {
                        check(existing["role"] == message.role && existing["content"] == message.content && (existing["transportNotice"] == true) == message.transportNotice &&
                            (message.timestamp == 0L || (existing["timestamp"] as? Number)?.toLong() == message.timestamp), "This message ID already contains a different reply.")
                    } else {
                        if (message.role == "user") target["messageTotal"] = (target["messageTotal"] as? Number)?.toLong()?.plus(1) ?: (history.size + 1).toLong()
                        history.add(buildMap { put("id", message.id); put("role", message.role); put("content", message.content); put("timestamp", message.timestamp.takeIf { it != 0L } ?: nowMillis); if (message.transportNotice) put("transportNotice", true) })
                    }
                }
                target["messages"] = history.takeLast(80)
                target["updatedAt"] = nowMillis
            }
            is Command.SaveProject -> {
                id(command.id)
                check(command.title.isNotBlank() && command.title.length <= 80 && command.description.length <= 3000 && command.context.length <= 3000,
                    "Use a project name up to 80 characters and description/context up to 3000 characters.")
                if (command.creating) {
                    check(projects.size < 20 && projects.none { it["id"] == command.id }, "Your workspace has reached its project limit.")
                    projects.add(mutableMapOf("id" to command.id, "createdAt" to nowMillis, "order" to nowMillis))
                }
                val project = projects.firstOrNull { it["id"] == command.id } ?: throw IllegalArgumentException("This project was deleted on another device.")
                project["title"] = command.title; project["description"] = command.description; project["sharedContext"] = command.context; project["updatedAt"] = nowMillis
            }
            is Command.DeleteProject -> {
                id(command.id)
                checkProject(command.id)
                projects.removeAll { it["id"] == command.id }
                chats.filter { it["projectId"] == command.id }.forEach { it["projectId"] = "" }
            }
        }
        value["version"] = 2; value["chats"] = chats; value["projects"] = projects
        checkSize(value)
        return value
    }

    fun encodeCommand(command: Command): Map<String, Any?> = when (command) {
        is Command.CreateChat -> mapOf("type" to "createChat", "id" to command.id, "projectId" to command.projectId)
        is Command.RenameChat -> mapOf("type" to "renameChat", "id" to command.id, "title" to command.title)
        is Command.DeleteChat -> mapOf("type" to "deleteChat", "id" to command.id)
        is Command.MoveChat -> mapOf("type" to "moveChat", "id" to command.id, "projectId" to command.projectId)
        is Command.Append -> mapOf("type" to "append", "chatId" to command.chatId, "messages" to command.messages.map {
            mapOf("id" to it.id, "role" to it.role, "content" to it.content, "timestamp" to it.timestamp, "transportNotice" to it.transportNotice)
        })
        is Command.SaveProject -> mapOf("type" to "saveProject", "id" to command.id, "title" to command.title, "description" to command.description, "context" to command.context, "creating" to command.creating)
        is Command.DeleteProject -> mapOf("type" to "deleteProject", "id" to command.id)
    }

    fun decodeCommand(value: Map<String, Any?>): Command {
        fun text(key: String): String { check(value[key] is String); return value[key] as String }
        fun commandId() = id(value["id"])
        return when (value["type"]) {
            "createChat" -> Command.CreateChat(commandId(), text("projectId"))
            "renameChat" -> Command.RenameChat(commandId(), text("title"))
            "deleteChat" -> Command.DeleteChat(commandId())
            "moveChat" -> Command.MoveChat(commandId(), text("projectId"))
            "append" -> Command.Append(id(value["chatId"]), maps(value["messages"]).map { item ->
                check(item["role"] in listOf("user", "assistant") && item["content"] is String && (item["content"] as String).isNotBlank())
                validateFields(item, emptyList(), listOf("timestamp"), listOf("transportNotice"))
                Message(id(item["id"]), item["role"] as String, item["content"] as String, (item["timestamp"] as? Number)?.toLong() ?: 0, item["transportNotice"] == true)
            })
            "saveProject" -> { check(value["creating"] is Boolean); Command.SaveProject(commandId(), text("title"), text("description"), text("context"), value["creating"] as Boolean) }
            "deleteProject" -> Command.DeleteProject(commandId())
            else -> throw IllegalArgumentException("Unknown AI workspace command.")
        }
    }

    private fun json(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> { check(value.toDouble().isFinite()); JsonPrimitive(value) }
        is Map<*, *> -> JsonObject(map(value).mapValues { json(it.value) })
        is List<*> -> JsonArray(value.map(::json))
        else -> throw IllegalArgumentException(INVALID)
    }
    private fun checkSize(value: Map<String, Any?>) = check(json(value).toString().toByteArray(Charsets.UTF_8).size < 800_000,
        "This AI workspace is full. Shorten the message or remove an old chat on the web.")
}
