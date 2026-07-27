package com.kairos.app.ui.navigation

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.models.*
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
                    firebaseRepository.getLatestPlan(firebaseUser.uid)
                        .catch { e ->
                            Log.e("MainViewModel", "Plan stream error", e)
                            errorMessage = e.localizedMessage
                        }
                        .collect { latestPlan ->
                            _plan.value = latestPlan
                            errorMessage = null
                        }
                } else {
                    _plan.value = null
                    errorMessage = null
                }
            }
        }
    }

    fun toggleTask(taskId: String, completed: Boolean) {
        val currentPlan = _plan.value ?: return
        val updatedBoards = currentPlan.boards.map { board ->
            board.copy(sections = board.sections.map { section ->
                section.copy(tasks = section.tasks.map { task ->
                    if (task.id == taskId) task.copy(completed = completed) else task
                })
            })
        }
        updatePlanInternal(currentPlan.copy(boards = updatedBoards))
    }

    fun addBoard(title: String) {
        val currentPlan = _plan.value ?: KairosPlan(userID = _user.value?.uid ?: "")
        val newBoard = KairosBoard(
            id = "board-${System.currentTimeMillis()}",
            title = title
        )
        updatePlanInternal(currentPlan.copy(boards = currentPlan.boards + newBoard))
    }

    fun renameBoard(boardId: String, newTitle: String) {
        val currentPlan = _plan.value ?: return
        val updatedBoards = currentPlan.boards.map {
            if (it.id == boardId) it.copy(title = newTitle) else it
        }
        updatePlanInternal(currentPlan.copy(boards = updatedBoards))
    }

    fun archiveBoard(boardId: String) {
        val currentPlan = _plan.value ?: return
        val updatedBoards = currentPlan.boards.map {
            if (it.id == boardId) it.copy(archived = true) else it
        }
        updatePlanInternal(currentPlan.copy(boards = updatedBoards))
    }

    fun addSection(boardId: String, title: String) {
        val currentPlan = _plan.value ?: return
        val updatedBoards = currentPlan.boards.map { board ->
            if (board.id == boardId) {
                val newSection = KairosSection(
                    id = "sec-${System.currentTimeMillis()}",
                    title = title
                )
                board.copy(sections = board.sections + newSection)
            } else board
        }
        updatePlanInternal(currentPlan.copy(boards = updatedBoards))
    }

    fun renameSection(sectionId: String, newTitle: String) {
        val currentPlan = _plan.value ?: return
        val updatedBoards = currentPlan.boards.map { board ->
            board.copy(sections = board.sections.map { section ->
                if (section.id == sectionId) section.copy(title = newTitle) else section
            })
        }
        updatePlanInternal(currentPlan.copy(boards = updatedBoards))
    }

    fun archiveSection(sectionId: String) {
        val currentPlan = _plan.value ?: return
        val updatedBoards = currentPlan.boards.map { board ->
            board.copy(sections = board.sections.map { section ->
                if (section.id == sectionId) section.copy(archived = true) else section
            })
        }
        updatePlanInternal(currentPlan.copy(boards = updatedBoards))
    }

    fun addTask(sectionId: String, title: String) {
        val currentPlan = _plan.value ?: return
        val updatedBoards = currentPlan.boards.map { board ->
            board.copy(sections = board.sections.map { section ->
                if (section.id == sectionId) {
                    val newTask = KairosTask(
                        id = "task-${System.currentTimeMillis()}",
                        title = title
                    )
                    section.copy(tasks = section.tasks + newTask)
                } else section
            })
        }
        updatePlanInternal(currentPlan.copy(boards = updatedBoards))
    }

    fun renameTask(taskId: String, newTitle: String) {
        val currentPlan = _plan.value ?: return
        val updatedBoards = currentPlan.boards.map { board ->
            board.copy(sections = board.sections.map { section ->
                section.copy(tasks = section.tasks.map { task ->
                    if (task.id == taskId) task.copy(title = newTitle) else task
                })
            })
        }
        updatePlanInternal(currentPlan.copy(boards = updatedBoards))
    }

    fun archiveTask(taskId: String) {
        val currentPlan = _plan.value ?: return
        val updatedBoards = currentPlan.boards.map { board ->
            board.copy(sections = board.sections.map { section ->
                section.copy(tasks = section.tasks.map { task ->
                    if (task.id == taskId) task.copy(archived = true) else task
                })
            })
        }
        updatePlanInternal(currentPlan.copy(boards = updatedBoards))
    }

    fun updatePlan(updatedPlan: KairosPlan) {
        updatePlanInternal(updatedPlan)
    }

    private fun updatePlanInternal(updatedPlan: KairosPlan) {
        val userId = _user.value?.uid ?: return
        _plan.value = updatedPlan
        viewModelScope.launch {
            try {
                firebaseRepository.updatePlan(userId, updatedPlan)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to update plan", e)
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
