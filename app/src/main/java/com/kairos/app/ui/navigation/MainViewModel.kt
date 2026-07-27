package com.kairos.app.ui.navigation

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.models.KairosPlan
import com.kairos.app.data.repository.AuthRepository
import com.kairos.app.data.repository.FirebaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainViewModel @JvmOverloads constructor(
    private val authRepository: AuthRepository = AuthRepository(),
    private val firebaseRepository: FirebaseRepository = FirebaseRepository()
) : ViewModel() {

    private val _plan = MutableStateFlow<KairosPlan?>(null)
    val plan: StateFlow<KairosPlan?> = _plan.asStateFlow()

    private val _user = MutableStateFlow(authRepository.currentUser)
    val user: StateFlow<com.google.firebase.auth.FirebaseUser?> = _user.asStateFlow()

    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            authRepository.getAuthState().collectLatest { firebaseUser ->
                _user.value = firebaseUser
                if (firebaseUser != null) {
                    // Start listening to the plan for this user
                    firebaseRepository.getLatestPlan(firebaseUser.uid)
                        .catch { e ->
                            Log.e("MainViewModel", "Plan stream error", e)
                            errorMessage = e.localizedMessage
                        }
                        .collect { latestPlan ->
                            _plan.value = latestPlan
                            errorMessage = null // Clear error if we get data
                        }
                } else {
                    _plan.value = null
                    errorMessage = null
                }
            }
        }
    }

    /**
     * Toggles a task's completion status and syncs with Firestore.
     * Implements optimistic UI updates.
     */
    fun toggleTask(taskId: String, completed: Boolean) {
        val currentPlan = _plan.value ?: return
        val userId = _user.value?.uid ?: return

        // Create an updated plan structure
        val updatedBoards = currentPlan.boards.map { board ->
            board.copy(sections = board.sections.map { section ->
                section.copy(tasks = section.tasks.map { task ->
                    if (task.id == taskId) task.copy(completed = completed) else task
                })
            })
        }
        
        val updatedPlan = currentPlan.copy(boards = updatedBoards)
        
        // Update local state immediately for responsiveness
        _plan.value = updatedPlan
        
        viewModelScope.launch {
            try {
                firebaseRepository.updatePlan(userId, updatedPlan)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to sync task toggle", e)
                errorMessage = "Sync failed: ${e.localizedMessage}"
            }
        }
    }

    fun signOut() {
        authRepository.signOut()
    }
    
    fun clearError() {
        errorMessage = null
    }
}
