package com.kairos.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.kairos.app.data.models.KairosPlan
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseRepository {
    private val db = FirebaseFirestore.getInstance()
    private val plansCollection = db.collection("study_plans")

    /**
     * Listens for the latest study plan for a given user.
     * Matches the web app's logic of sorting by createdAt descending.
     */
    fun getLatestPlan(userId: String): Flow<KairosPlan?> = callbackFlow {
        val query = plansCollection
            .whereEqualTo("userID", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1)

        val subscription = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            val plan = snapshot?.documents?.firstOrNull()?.let { doc ->
                doc.toObject(KairosPlan::class.java)?.copy(
                    // We can store the document ID if needed, but the web app
                    // doesn't seem to store it inside the boards data itself.
                )
            }
            trySend(plan)
        }

        awaitClose { subscription.remove() }
    }

    /**
     * Updates the entire plan document in Firestore.
     * In a production app with very large plans, we might want to use
     * FieldValue.arrayUnion/Remove or more granular updates, but this
     * matches the current web implementation.
     */
    suspend fun updatePlan(userId: String, plan: KairosPlan) {
        val query = plansCollection
            .whereEqualTo("userID", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()

        val docId = query.documents.firstOrNull()?.id ?: return
        
        plansCollection.document(docId).set(plan).await()
    }
}
