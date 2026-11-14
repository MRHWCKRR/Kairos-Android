package com.kairos.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId

/** Fresh-document commands share the existing durable journal and account lock. */
internal object AiPersistence {
    @Suppress("UNCHECKED_CAST")
    fun map(value: Any?): Map<String, Any?> {
        require(value is Map<*, *> && value.keys.all { it is String }) { "Saved AI data could not be read safely." }
        return value as Map<String, Any?>
    }
    fun maps(value: Any?): List<Map<String, Any?>> {
        if (value == null) return emptyList()
        require(value is List<*>) { "Saved AI data could not be read safely." }
        return value.map(::map)
    }
    private fun count(source: Map<String, Any?>, key: String): Long {
        if (!source.containsKey(key)) return 0L
        val value = source[key] as? Number ?: error("Saved AI counter $key is invalid.")
        val number = value.toDouble()
        require(number.isFinite() && number >= 0 && number % 1.0 == 0.0 && number <= Long.MAX_VALUE.toDouble()) { "Saved AI counter $key is invalid." }
        return value.toLong()
    }
    private fun timestamp(source: Map<String, Any?>, key: String): Double {
        if (!source.containsKey(key)) return 0.0
        val value = (source[key] as? Number)?.toDouble() ?: error("Saved preview time is invalid.")
        require(value.isFinite() && value >= 0) { "Saved preview time is invalid." }
        return value
    }
    fun proposals(user: Map<String, Any?>): Map<String, AiActions.Proposal> =
        if (!user.containsKey("aiNativeProposals")) emptyMap() else map(user["aiNativeProposals"]).mapValues { (chatId, value) ->
            AiActions.fromMap(map(value)).also { require(it.chatId == chatId) { "Saved preview belongs to another conversation." } }
        }

    fun isRetired(proposal: AiActions.Proposal, user: Map<String, Any?>): Boolean = AiActions.wasApplied(proposal,
        mapOf("aiNativeActionReceipts" to (user["aiNativeProposalHistory"] ?: emptyList<Any>()),
            "aiNativeActionReceiptFloor" to (user["aiNativeProposalHistoryFloor"] ?: 0)))

    private fun retire(proposal: AiActions.Proposal, state: String, user: Map<String, Any?>): Map<String, Any?> {
        val history = maps(user["aiNativeProposalHistory"]).toMutableList()
        val existing = history.firstOrNull { it["id"] == proposal.id }
        if (existing != null) require(existing["fingerprint"] == AiActions.payloadFingerprint(proposal)) { "The saved preview identity changed." }
        else history += AiActions.receipt(proposal) + ("state" to state)
        var floor = timestamp(user, "aiNativeProposalHistoryFloor")
        while (history.size > 100) floor = maxOf(floor, timestamp(history.removeAt(0), "createdAt"))
        return mapOf("aiNativeProposalHistory" to history, "aiNativeProposalHistoryFloor" to floor)
    }

    /** Pure user-only operations are also exercised without an emulator. */
    fun userPatch(edit: PendingEdit, user: Map<String, Any?>): Map<String, Any?> {
        val op = edit.edited
        val previews = proposals(user).toMutableMap()
        return when (op["operation"]) {
            "workspace" -> {
                val command = AiWorkspace.decodeCommand(map(op["command"]))
                if (command is AiWorkspace.Command.Append && command.messages.any { it.role == "user" }) {
                    require(previews[command.chatId] == null) { "Review or discard this chat’s saved preview first." }
                }
                val workspace = AiWorkspace.apply(user, command, edit.userId, (op["createdAt"] as Number).toLong())
                var patch: Map<String, Any?> = mapOf("aiWorkspace" to workspace)
                if (command is AiWorkspace.Command.DeleteChat) {
                    val removed = previews.remove(command.id)
                    if (removed != null) patch = patch + retire(removed, "discarded", user)
                    patch = patch + ("aiNativeProposals" to previews.mapValues { AiActions.toMap(it.value) })
                }
                patch
            }
            "reply" -> {
                val command = AiWorkspace.decodeCommand(map(op["command"]))
                require(command is AiWorkspace.Command.Append && command.messages.all { it.role == "assistant" }) { "Invalid saved AI reply." }
                val proposal = op["proposal"]?.let { AiActions.fromMap(map(it)) }
                if (proposal != null) {
                    require(proposal.ownerId == edit.userId && proposal.chatId == command.chatId) { "This preview belongs to another account or chat." }
                    if (isRetired(proposal, user)) return emptyMap()
                    val old = previews[proposal.chatId]
                    require(old == null || AiActions.payloadFingerprint(old) == AiActions.payloadFingerprint(proposal)) { "This chat already has a different saved preview." }
                    require(AiWorkspace.read(user, edit.userId).chats.firstOrNull { it.id == proposal.chatId }?.hasUnfinishedWebActions != true) { "Finish this chat’s pending web change first." }
                    previews[proposal.chatId] = proposal
                }
                mapOf("aiWorkspace" to AiWorkspace.apply(user, command, edit.userId, (op["createdAt"] as Number).toLong()),
                    "aiNativeProposals" to previews.mapValues { AiActions.toMap(it.value) })
            }
            "discard" -> {
                val proposal = AiActions.fromMap(map(op["proposal"]))
                require(proposal.ownerId == edit.userId) { "This preview belongs to another account." }
                if (isRetired(proposal, user)) return emptyMap()
                val saved = previews[proposal.chatId] ?: error("This saved preview is no longer available.")
                require(AiActions.payloadFingerprint(saved) == AiActions.payloadFingerprint(proposal)) { "The saved preview changed. Reopen this chat." }
                previews.remove(proposal.chatId)
                retire(proposal, "discarded", user) + ("aiNativeProposals" to previews.mapValues { AiActions.toMap(it.value) })
            }
            else -> error("Unsupported AI workspace operation.")
        }
    }

    suspend fun commit(db: FirebaseFirestore, edit: PendingEdit, requireOwner: () -> Unit) {
        require(edit.documentId == edit.userId && edit.kind == "ai") { "Invalid retained AI operation." }
        val userRef = db.collection("users").document(edit.userId)
        userRef.get(Source.SERVER).await()
        requireOwner()
        val op = edit.edited
        val createdAt = (op["createdAt"] as? Number)?.toLong() ?: error("Missing AI operation time.")
        require(createdAt > 0)
        val codec = PendingValueCodec(referenceResolver = { db.document(it) }, referenceProjectId = db.app.options.projectId)
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(codec.encode(edit).toByteArray()).joinToString("") { "%02x".format(it) }
        val planId = op["planId"] as? String
        require(planId == null || planId.isNotBlank() && '/' !in planId) { "Invalid planner identity." }
        val planRef = planId?.let { db.collection("study_plans").document(it) }
        db.runTransaction { tx ->
            requireOwner()
            val userSnapshot = tx.get(userRef)
            require(userSnapshot.exists()) { "This account profile was deleted. Sign in again before editing." }
            val user = userSnapshot.data.orEmpty()
            val receipts = maps(user["aiAndroidOperationReceipts"]).toMutableList()
            val prior = receipts.firstOrNull { it["id"] == edit.id }
            if (prior != null) {
                require(prior["fingerprint"] == fingerprint) { "This saved operation ID has different content." }
                return@runTransaction
            }
            var floor = count(user, "aiAndroidOperationFloor")
            require(createdAt > floor) { "This retained change is too old to retry. Reload your workspace." }
            var userPatch: Map<String, Any?>
            var planPatch: Map<String, Any?>? = null
            var newPlan = false
            if (op["operation"] in listOf("apply", "undo", "plan")) {
                require(planRef != null)
                val snapshot = tx.get(planRef)
                newPlan = !snapshot.exists()
                val plan = snapshot.data ?: mapOf("boards" to emptyList<Any>(), "scheduleEvents" to emptyList<Any>())
                require(if (snapshot.exists()) plan["userID"] == edit.userId else planId == "plan-${edit.userId}") { "The planner is unavailable or belongs to another account." }
                when (op["operation"]) {
                    "apply" -> {
                        val proposal = AiActions.fromMap(map(op["proposal"]))
                        require(proposal.ownerId == edit.userId && proposal.planId == planId) { "The saved preview belongs to another planner." }
                        if (AiActions.wasApplied(proposal, plan)) return@runTransaction
                        require(!isRetired(proposal, user)) { "This preview was discarded or already applied." }
                        val previews = proposals(user).toMutableMap()
                        require(previews[proposal.chatId]?.let(AiActions::payloadFingerprint) == AiActions.payloadFingerprint(proposal)) { "This preview changed or was removed. Reopen this chat." }
                        val workspace = AiWorkspace.read(user, edit.userId)
                        require(workspace.chats.any { it.id == proposal.chatId && !it.hasUnfinishedWebActions }) { "This conversation was deleted or has an unfinished web change." }
                        val prepared = AiActions.prepare(proposal, plan)
                        val actionReceipts = maps(plan["aiNativeActionReceipts"]).toMutableList()
                        actionReceipts += AiActions.receipt(proposal)
                        var actionFloor = timestamp(plan, "aiNativeActionReceiptFloor")
                        while (actionReceipts.size > 100) actionFloor = maxOf(actionFloor, timestamp(actionReceipts.removeAt(0), "createdAt"))
                        planPatch = prepared.after.filterKeys { it in listOf("boards", "scheduleEvents") } +
                            mapOf("aiNativeActionReceipts" to actionReceipts, "aiNativeActionReceiptFloor" to actionFloor)
                        previews.remove(proposal.chatId)
                        userPatch = retire(proposal, "applied", user) + ("aiNativeProposals" to previews.mapValues { AiActions.toMap(it.value) }) +
                            completedProgress(user, prepared.completedTaskIds, proposal.timezone)
                        val message = AiWorkspace.Message("applied-${proposal.id}", "assistant", "Applied ${proposal.rows.size} reviewed change(s).", createdAt)
                        userPatch = userPatch + ("aiWorkspace" to AiWorkspace.apply(user, AiWorkspace.Command.Append(proposal.chatId, listOf(message)), edit.userId, createdAt))
                    }
                    "undo" -> {
                        val before = map(op["before"]); val after = map(op["after"])
                        planPatch = AiActions.undo(before, after, plan).filterKeys { it in listOf("boards", "scheduleEvents") }
                        userPatch = emptyMap()
                    }
                    else -> {
                        val before = map(op["before"]); val after = map(op["after"])
                        // The preview appends authored content; concurrent unrelated edits survive.
                        planPatch = SharedDataMerge.merge(before, after, plan, before).filterKeys { it in listOf("boards", "scheduleEvents") }
                        userPatch = emptyMap()
                    }
                }
            } else userPatch = userPatch(edit, user)
            receipts += mapOf("id" to edit.id, "fingerprint" to fingerprint, "createdAt" to createdAt)
            while (receipts.size > 100) floor = maxOf(floor, (receipts.removeAt(0)["createdAt"] as Number).toLong())
            requireOwner()
            if (planPatch != null) {
                if (newPlan) {
                    tx.set(planRef!!, planPatch!! + mapOf("userID" to edit.userId, "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()), SetOptions.merge())
                } else tx.update(planRef!!, planPatch!!)
            }
            // update replaces just the addressed root maps, so removed chats/previews stay removed.
            val patch = userPatch + mapOf("aiAndroidOperationReceipts" to receipts, "aiAndroidOperationFloor" to floor)
            if (user.isEmpty()) tx.set(userRef, patch, SetOptions.merge()) else tx.update(userRef, patch)
        }.await()
        requireOwner()
    }

    private fun completedProgress(user: Map<String, Any?>, ids: List<String>, timezone: String): Map<String, Any?> {
        if (ids.isEmpty()) return emptyMap()
        val achievements = if (user["achievements"] == null) emptyMap() else map(user["achievements"])
        val counted = if (!achievements.containsKey("countedTaskIds")) emptyList() else {
            val values = achievements["countedTaskIds"] as? List<*> ?: error("Invalid task progress.")
            values.map { it as? String ?: error("Invalid task progress.") }
        }
        val newly = ids.distinct().filter { it !in counted }
        if (newly.isEmpty()) return emptyMap()
        val total = count(achievements, "lifetimeTasksCompleted")
        val focus = if (user["focusData"] == null) emptyMap() else map(user["focusData"])
        val log = if (focus["dailyTasksLog"] == null) emptyMap() else map(focus["dailyTasksLog"])
        val day = Instant.now().atZone(ZoneId.of(timezone)).toLocalDate().toString()
        return mapOf("achievements" to achievements + mapOf("countedTaskIds" to (counted + newly), "lifetimeTasksCompleted" to Math.addExact(total, newly.size.toLong())),
            "focusData" to focus + ("dailyTasksLog" to (log + (day to (Math.addExact(count(log, day), newly.size.toLong()))))))
    }
}
