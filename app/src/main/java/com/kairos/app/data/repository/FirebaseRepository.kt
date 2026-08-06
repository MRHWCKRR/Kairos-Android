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
     * Marketplace: Fetches shared routines.
     */
    fun getSharedRoutines(): Flow<List<com.kairos.app.data.models.KairosSharedRoutine>> = callbackFlow {
        Log.d("FirebaseRepository", "Fetching shared routines...")
        val query = db.collection("shared_routines")
            // Temporarily removed orderBy to rule out missing index crashes
            // .orderBy("downloads", Query.Direction.DESCENDING) 
            .limit(20)

        val subscription = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("FirebaseRepository", "Shared routines listener error: ${error.message}", error)
                close(error)
                return@addSnapshotListener
            }
            
            try {
                val routines = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(com.kairos.app.data.models.KairosSharedRoutine::class.java)
                    } catch (e: Exception) {
                        Log.e("FirebaseRepository", "Error parsing shared routine: ${doc.id}", e)
                        null
                    }
                } ?: emptyList()
                Log.d("FirebaseRepository", "Loaded ${routines.size} shared routines")
                trySend(routines)
            } catch (e: Exception) {
                Log.e("FirebaseRepository", "General parse error in shared routines", e)
            }
        }
        awaitClose { subscription.remove() }
    }

    /**
     * Marketplace: Shares a routine to the global collection.
     */
    suspend fun shareRoutine(shared: com.kairos.app.data.models.KairosSharedRoutine) {
        db.collection("shared_routines").document(shared.id).set(shared).await()
    }

    /**
     * Marketplace: Deletes a shared routine from the global collection.
     */
    suspend fun deleteSharedRoutine(routineId: String) {
        db.collection("shared_routines").document(routineId).delete().await()
    }

    /**
     * Marketplace: Updates metadata of a shared routine.
     */
    suspend fun updateSharedRoutine(routineId: String, data: Map<String, Any>) {
        db.collection("shared_routines").document(routineId).update(data).await()
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
