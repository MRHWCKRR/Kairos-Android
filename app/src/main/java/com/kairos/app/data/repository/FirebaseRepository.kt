package com.kairos.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.kairos.app.data.models.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Reads retain their document identity and raw baseline; writes never serialize a partial model. */
class FirebaseRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val currentUserId: () -> String? = { FirebaseAuth.getInstance().currentUser?.uid }
) {
    private val plansCollection = db.collection("study_plans")
    private val usersCollection = db.collection("users")

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
            try { trySend(snapshot?.toObject(KairosUserProfile::class.java)?.copy(sourceFields = snapshot.data.orEmpty())) }
            catch (e: Exception) { close(e) }
        }
        awaitClose { subscription.remove() }
    }

    suspend fun updatePlan(userId: String, before: KairosPlan, after: KairosPlan) {
        requireOwner(userId)
        check(before.userID == userId && after.userID == userId) { "Planner belongs to a different account." }
        val reference = plansCollection.document(before.documentId.ifBlank { userId })
        val base = before.sharedFields()
        val edited = after.sharedFields()
        db.runTransaction { transaction ->
            requireOwner(userId)
            val snapshot = transaction.get(reference)
            if (before.documentId.isNotBlank() && !snapshot.exists()) throw SharedDataConflict("This planner was deleted. Reload before editing.")
            val remote = snapshot.data.orEmpty()
            if (snapshot.exists() && remote["userID"] != userId) throw SharedDataConflict("Planner ownership changed.")
            if (remote["sections"] != null && remote["boards"] == null) throw SharedDataConflict("Open this legacy planner on the web before editing on Android.")
            val merged = SharedDataMerge.merge(base, edited, remote, before.sourceFields)
            validateRecurringCompletions(remote, merged)
            requireOwner(userId)
            val fields = merged.filterKeys { it in setOf("boards", "dayInsights", "scheduleEvents") }
            if (snapshot.exists()) transaction.update(reference, fields)
            else transaction.set(reference, fields + mapOf("userID" to userId, "createdAt" to FieldValue.serverTimestamp()))
        }.await()
        requireOwner(userId)
    }

    suspend fun updateUserProfile(userId: String, before: KairosUserProfile, data: Map<String, Any>) {
        requireOwner(userId)
        val base = before.sharedFields()
        val edited = profilePatch(before, data)
        val reference = usersCollection.document(userId)
        db.runTransaction { transaction ->
            requireOwner(userId)
            val snapshot = transaction.get(reference)
            if (!snapshot.exists() && before.sourceFields.isNotEmpty()) throw SharedDataConflict("This profile was deleted. Sign in again.")
            val merged = SharedDataMerge.merge(base, edited, snapshot.data.orEmpty(), before.sourceFields)
            requireOwner(userId)
            // Top-level merge plus raw nested preservation. No unknown top-level data is removed.
            transaction.set(reference, merged, SetOptions.merge())
        }.await()
        requireOwner(userId)
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
