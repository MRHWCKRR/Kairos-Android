package com.kairos.app.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.kairos.app.data.models.KairosPlan
import com.kairos.app.data.models.KairosUserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseRepository {
    private val db = FirebaseFirestore.getInstance()
    private val plansCollection = db.collection("study_plans")
    private val usersCollection = db.collection("users")

    /**
     * Listens for the latest study plan for a given user.
     */
    fun getLatestPlan(userId: String): Flow<KairosPlan?> = callbackFlow {
        Log.d("FirebaseRepository", "Starting snapshot listener for user: $userId")
        
        val query = plansCollection
            .whereEqualTo("userID", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1)

        val subscription = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("FirebaseRepository", "Firestore listener error: ${error.message}", error)
                close(error)
                return@addSnapshotListener
            }

            try {
                val plan = snapshot?.documents?.firstOrNull()?.toObject(KairosPlan::class.java)
                trySend(plan)
            } catch (e: Exception) {
                Log.e("FirebaseRepository", "Error parsing plan object", e)
            }
        }

        awaitClose { subscription.remove() }
    }

    /**
     * Listens for the user's profile data (achievements, focusData, settings).
     */
    fun getUserProfile(userId: String): Flow<KairosUserProfile?> = callbackFlow {
        val docRef = usersCollection.document(userId)
        val subscription = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            try {
                val profile = snapshot?.toObject(KairosUserProfile::class.java)
                trySend(profile)
            } catch (e: Exception) {
                Log.e("FirebaseRepository", "Error parsing user profile", e)
            }
        }
        awaitClose { subscription.remove() }
    }

    /**
     * Updates the study plan document.
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

    /**
     * Updates specific fields in the user profile (merge).
     */
    suspend fun updateUserProfile(userId: String, data: Map<String, Any>) {
        usersCollection.document(userId).set(data, SetOptions.merge()).await()
    }
}
