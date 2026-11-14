package com.kairos.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.kairos.app.data.models.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

class PendingEditStagingException(message: String, cause: Throwable) : java.io.IOException(message, cause)

/** Reads retain their document identity and raw baseline; writes never serialize a partial model. */
class FirebaseRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val currentUserId: () -> String? = { FirebaseAuth.getInstance().currentUser?.uid },
    private val pendingStore: PendingEditStore = PendingEditStore(
        File(db.app.applicationContext.noBackupFilesDir, "pending-edits"),
        PendingValueCodec(referenceResolver = { db.document(it) }, referenceProjectId = db.app.options.projectId)
    )
) {
    private val plansCollection = db.collection("study_plans")
    private val usersCollection = db.collection("users")

    private companion object {
        val accountLocks = ConcurrentHashMap<String, Mutex>()
        val activeOwners = MutableStateFlow<Set<String>>(emptySet())
    }
    private fun accountLock(uid: String) = accountLocks.getOrPut(uid) { Mutex() }

    private fun requireOwner(uid: String) {
        check(uid.isNotBlank() && currentUserId() == uid) { "Your account changed. Open your planner again." }
    }

    private fun latest(uid: String) = plansCollection.whereEqualTo("userID", uid)
        .orderBy("createdAt", Query.Direction.DESCENDING).limit(1)

    fun getLatestPlan(userId: String): Flow<KairosPlan?> = callbackFlow {
        requireOwner(userId)
        val subscription = latest(userId).addSnapshotListener { snapshot, error ->
            if (currentUserId() != userId) { close(); return@addSnapshotListener }
            if (error != null) { close(error); return@addSnapshotListener }
            try { trySend(snapshot?.documents?.firstOrNull()?.readPlan()) }
            catch (e: Exception) { close(e) }
        }
        awaitClose { subscription.remove() }
    }

    suspend fun getPlanOnce(userId: String): KairosPlan? {
        requireOwner(userId)
        val snapshot = latest(userId).get().await()
        requireOwner(userId)
        return snapshot.documents.firstOrNull()?.readPlan()
    }

    fun getUserProfile(userId: String): Flow<KairosUserProfile?> = callbackFlow {
        requireOwner(userId)
        val subscription = usersCollection.document(userId).addSnapshotListener { snapshot, error ->
            if (currentUserId() != userId) { close(); return@addSnapshotListener }
            if (error != null) { close(error); return@addSnapshotListener }
            try { trySend(snapshot?.toObject(KairosUserProfile::class.java)?.copy(documentExists = snapshot.exists(), sourceFields = snapshot.data.orEmpty())) }
            catch (e: Exception) { close(e) }
        }
        awaitClose { subscription.remove() }
    }

    suspend fun updatePlan(userId: String, before: KairosPlan, after: KairosPlan, operationId: String = UUID.randomUUID().toString()) {
        requireOwner(userId)
        check(before.userID == userId && after.userID == userId) { "Planner belongs to a different account." }
        savePending(PendingEdit(operationId, userId, "plan", before.documentId.ifBlank { userId },
            before.documentId.isNotBlank(), before.sharedFields(), after.sharedFields(), before.sourceFields))
    }

    suspend fun updateUserProfile(userId: String, before: KairosUserProfile, data: Map<String, Any>, operationId: String = UUID.randomUUID().toString()) {
        requireOwner(userId)
        savePending(PendingEdit(operationId, userId, "profile", userId, before.documentExists,
            before.sharedFields(), profilePatch(before, data), before.sourceFields))
    }

    suspend fun getAiUserOnce(userId: String): Map<String, Any?> {
        requireOwner(userId)
        val snapshot = usersCollection.document(userId).get(Source.SERVER).await()
        requireOwner(userId)
        return snapshot.data.orEmpty()
    }

    suspend fun saveAiOperation(userId: String, operation: Map<String, Any?>, operationId: String = UUID.randomUUID().toString()) {
        requireOwner(userId)
        savePending(PendingEdit(operationId, userId, "ai", userId, true, emptyMap(), operation, emptyMap()))
    }

    data class PendingEditsStatus(val edits: List<PendingEdit> = emptyList(), val error: String? = null, val isSaving: Boolean = false)

    fun observePendingEditStatus(userId: String): Flow<PendingEditsStatus> = combine(pendingStore.changes, activeOwners) { _, active ->
        requireOwner(userId)
        try { PendingEditsStatus(pendingStore.list(userId), isSaving = userId in active) }
        catch (e: Exception) { PendingEditsStatus(error = "Retained edits could not be read. Discard them to recover. ${e.localizedMessage}") }
    }.flowOn(Dispatchers.IO)

    fun observePendingEdits(userId: String): Flow<List<PendingEdit>> = observePendingEditStatus(userId).map {
        check(it.error == null) { it.error.orEmpty() }
        it.edits
    }

    suspend fun discardPendingEdit(userId: String, operationId: String) = withContext(Dispatchers.IO) {
        accountLock(userId).withLock {
            requireOwner(userId)
            pendingStore.remove(userId, operationId)
        }
    }

    suspend fun discardPendingEdits(userId: String) = withContext(Dispatchers.IO) {
        accountLock(userId).withLock {
            requireOwner(userId)
            pendingStore.clear(userId)
        }
    }

    suspend fun retryPendingEdit(userId: String, operationId: String) {
        requireOwner(userId)
        val edit = withContext(Dispatchers.IO) {
            pendingStore.list(userId).singleOrNull { it.id == operationId }
        } ?: return // An acknowledged/discarded edit cannot be resurrected by a stale Retry click.
        requireOwner(userId)
        executePending(edit)
    }

    private suspend fun savePending(edit: PendingEdit) {
        requireOwner(edit.userId)
        // Persist before any RPC: even process death before an error/acknowledgement is recoverable.
        withContext(Dispatchers.IO) {
            try { pendingStore.put(edit) }
            catch (e: Exception) {
                throw PendingEditStagingException("Could not retain this edit on the device. Keep the app open to retry or discard it. ${e.localizedMessage}", e)
            }
        }
        requireOwner(edit.userId)
        executePending(edit)
    }

    private suspend fun executePending(edit: PendingEdit) = accountLock(edit.userId).withLock {
        requireOwner(edit.userId)
        val retained = withContext(Dispatchers.IO) { pendingStore.list(edit.userId).singleOrNull { it.id == edit.id } }
            ?: throw CancellationException("Retained edit was discarded or acknowledged.")
        activeOwners.update { it + edit.userId }
        try { commitPending(retained) }
        finally { activeOwners.update { it - edit.userId } }
    }

    private suspend fun commitPending(edit: PendingEdit) {
        val userId = edit.userId
        requireOwner(userId)
        require(edit.documentId.isNotBlank() && '/' !in edit.documentId) { "Invalid retained document." }
        require(edit.kind in setOf("plan", "profile", "ai")) { "Unsupported retained edit." }
        require(edit.kind != "profile" || edit.documentId == userId) { "Profile belongs to a different account." }
        if (edit.kind == "ai") {
            AiPersistence.commit(db, edit) { requireOwner(userId) }
            withContext(Dispatchers.IO) { pendingStore.remove(userId, edit.id) }
            return
        }
        val reference = (if (edit.kind == "plan") plansCollection else usersCollection).document(edit.documentId)
        // The emulator regression shows transaction RPCs can bypass disableNetwork.
        // Require a server read so explicitly offline clients do not start a cached edit.
        reference.get(Source.SERVER).await()
        requireOwner(userId)
        db.runTransaction { transaction ->
            requireOwner(userId)
            val snapshot = transaction.get(reference)
            if (edit.documentExisted && !snapshot.exists()) throw SharedDataConflict("This document was deleted. Discard the retained edit and reload.")
            val remote = snapshot.data.orEmpty()
            if (edit.kind == "plan" && snapshot.exists() && remote["userID"] != userId) throw SharedDataConflict("Planner ownership changed.")
            if (edit.kind == "plan" && remote["sections"] != null && remote["boards"] == null) throw SharedDataConflict("Open this legacy planner on the web before editing on Android.")
            val merged = SharedDataMerge.merge(edit.base, edit.edited, remote, edit.rawBaseline)
            if (edit.kind == "plan") validateRecurringCompletions(remote, merged)
            requireOwner(userId)
            if (edit.kind == "plan") {
                val fields = merged.filterKeys { it in setOf("boards", "dayInsights", "scheduleEvents") }
                if (snapshot.exists()) transaction.update(reference, fields)
                else transaction.set(reference, fields + mapOf("userID" to userId, "createdAt" to FieldValue.serverTimestamp()))
            } else transaction.set(reference, merged, SetOptions.merge())
        }.await()
        requireOwner(userId)
        withContext(Dispatchers.IO) { pendingStore.remove(userId, edit.id) }
    }

    private fun DocumentSnapshot.readPlan(): KairosPlan {
        // Legacy clients also write ISO strings for creation metadata. It must not hide readable tasks.
        val value = toObject(PlannerFields::class.java) ?: throw IllegalStateException("Planner is unavailable.")
        return KairosPlan(value.boards, value.dayInsights, value.scheduleEvents, value.userID,
            get("createdAt") as? com.google.firebase.Timestamp, id, data.orEmpty())
    }

    @IgnoreExtraProperties
    data class PlannerFields(
        val boards: List<KairosBoard> = emptyList(),
        val dayInsights: Map<String, String> = emptyMap(),
        val scheduleEvents: List<KairosScheduleEvent> = emptyList(),
        val userID: String = ""
    )

    private fun validateRecurringCompletions(before: Map<String, Any?>, after: Map<String, Any?>) {
        fun tasks(plan: Map<String, Any?>): List<Map<*, *>> = (plan["boards"] as? List<*>).orEmpty()
            .filterIsInstance<Map<*, *>>().flatMap { (it["sections"] as? List<*>).orEmpty() }
            .filterIsInstance<Map<*, *>>().flatMap { (it["tasks"] as? List<*>).orEmpty() }.filterIsInstance<Map<*, *>>()
        val old = tasks(before).associateBy { it["id"] }
        tasks(after).forEach { task ->
            val previous = old[task["id"]]
            if ((previous?.get("recurrence") as? Map<*, *>)?.get("enabled") == true && previous["completed"] != task["completed"]) {
                throw SharedDataConflict("Complete recurring occurrences on iOS or web until Android supports occurrence editing.")
            }
        }
    }
}
