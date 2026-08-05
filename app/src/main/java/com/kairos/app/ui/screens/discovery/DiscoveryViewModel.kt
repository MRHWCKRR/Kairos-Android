package com.kairos.app.ui.screens.discovery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.models.KairosSharedRoutine
import com.kairos.app.data.repository.FirebaseRepository
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DiscoveryViewModel(
    private val firebaseRepository: FirebaseRepository = FirebaseRepository()
) : ViewModel() {

    private val _routines = MutableStateFlow<List<KairosSharedRoutine>>(emptyList())
    val routines: StateFlow<List<KairosSharedRoutine>> = _routines.asStateFlow()

    var isRefreshing by mutableStateOf(false)
        private set

    init {
        loadRoutines()
    }

    private fun loadRoutines() {
        viewModelScope.launch {
            isRefreshing = true
            firebaseRepository.getSharedRoutines().collect {
                _routines.value = it
                isRefreshing = false
            }
        }
    }

    fun adoptRoutine(shared: KairosSharedRoutine, userId: String) {
        viewModelScope.launch {
            // Logic to add the shared routine's boards to the user's plan
            // This would normally involve calling mainViewModel.addBoard for each
        }
    }
}
