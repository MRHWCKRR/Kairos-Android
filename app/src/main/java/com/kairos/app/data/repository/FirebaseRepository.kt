package com.kairos.app.data.repository

import android.util.Log
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
        Log.d("FirebaseRepository", "Starting snapshot listener for user: $userId")
        
        val query = try {
            plansCollection
                .whereEqualTo("userID", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(1)
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error building query", e)
            close(e)
            return@callbackFlow
        }

        val subscription = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("FirebaseRepository", "Firestore listener error: ${error.message}", error)
                // If this is an index error, the message will contain a link to create it.
                close(error)
                return@addSnapshotListener
            }

            try {
                val plan = snapshot?.documents?.firstOrNull()?.toObject(KairosPlan::class.java)
                Log.d("FirebaseRepository", "Plan received: ${plan != null}")
                trySend(plan)
            } catch (e: Exception) {
                Log.e("FirebaseRepository", "Error parsing plan object", e)
            }
        }

        awaitClose { 
            Log.d("FirebaseRepository", "Closing snapshot listener")
            subscription.remove() 
        }
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
