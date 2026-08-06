package com.kairos.app.ui.screens.discovery

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.models.KairosSharedRoutine
import com.kairos.app.data.repository.FirebaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class DiscoveryViewModel(
    private val firebaseRepository: FirebaseRepository = FirebaseRepository()
) : ViewModel() {

    private val _routines = MutableStateFlow<List<KairosSharedRoutine>>(emptyList())
    val routines: StateFlow<List<KairosSharedRoutine>> = _routines.asStateFlow()

    var isRefreshing by mutableStateOf(false)
        private set

    var showOnlyMyRoutines by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        loadRoutines()
    }

    fun loadRoutines() {
        viewModelScope.launch {
            isRefreshing = true
            errorMessage = null
            firebaseRepository.getSharedRoutines()
                .catch { e ->
                    Log.e("DiscoveryViewModel", "Flow error", e)
                    errorMessage = e.localizedMessage ?: "Failed to load routines"
                    isRefreshing = false
                }
                .collect {
                    _routines.value = it
                    isRefreshing = false
                }
        }
    }

    fun toggleFilter(myRoutines: Boolean) {
        showOnlyMyRoutines = myRoutines
    }

    fun deleteRoutine(routineId: String) {
        viewModelScope.launch {
            try {
                firebaseRepository.deleteSharedRoutine(routineId)
            } catch (e: Exception) {
                Log.e("DiscoveryViewModel", "Delete failed", e)
                errorMessage = "Failed to delete: ${e.localizedMessage}"
            }
        }
    }

    fun updateRoutine(routineId: String, description: String, category: String) {
        viewModelScope.launch {
            try {
                firebaseRepository.updateSharedRoutine(routineId, mapOf(
                    "description" to description,
                    "category" to category
                ))
            } catch (e: Exception) {
                Log.e("DiscoveryViewModel", "Update failed", e)
                errorMessage = "Failed to update: ${e.localizedMessage}"
            }
        }
    }

    fun adoptRoutine(shared: KairosSharedRoutine, userId: String) {
        viewModelScope.launch {
            // Future logic for adoption tracking
        }
    }
}
