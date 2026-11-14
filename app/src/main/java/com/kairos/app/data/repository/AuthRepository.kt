package com.kairos.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun getAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun signOut() {
        auth.signOut()
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    suspend fun signUp(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password).await()
    }

    suspend fun signInWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await()
    }

    suspend fun updateEmail(userId: String, password: String, newEmail: String) {
        val user = reauthenticatedUser(userId, password)
        check(auth.currentUser?.uid == userId) { "Your account changed. Try again." }
        user.verifyBeforeUpdateEmail(newEmail).await()
    }

    suspend fun updatePassword(userId: String, password: String, newPassword: String) {
        val user = reauthenticatedUser(userId, password)
        check(auth.currentUser?.uid == userId) { "Your account changed. Try again." }
        user.updatePassword(newPassword).await()
    }

    private suspend fun reauthenticatedUser(userId: String, password: String): FirebaseUser {
        val user = auth.currentUser ?: error("Sign in before changing account details.")
        check(user.uid == userId) { "Your account changed. Try again." }
        val email = user.email ?: error("No email found")
        val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
        user.reauthenticate(credential).await()
        check(auth.currentUser?.uid == userId) { "Your account changed. Try again." }
        return user
    }
}
