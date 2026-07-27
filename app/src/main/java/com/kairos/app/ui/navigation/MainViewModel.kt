package com.kairos.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.models.KairosPlan
import com.kairos.app.data.repository.AuthRepository
import com.kairos.app.data.repository.FirebaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val firebaseRepository: FirebaseRepository = FirebaseRepository()
) : ViewModel() {

    private val _plan = MutableStateFlow<KairosPlan?>(null)
    val plan: StateFlow<KairosPlan?> = _plan.asStateFlow()

    private val _user = MutableStateFlow(authRepository.currentUser)
    val user: StateFlow<com.google.firebase.auth.FirebaseUser?> = _user.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.getAuthState().collectLatest { firebaseUser ->
                _user.value = firebaseUser
                if (firebaseUser != null) {
                    firebaseRepository.getLatestPlan(firebaseUser.uid).collectLatest { latestPlan ->
                        _plan.value = latestPlan
                    }
                } else {
                    _plan.value = null
                }
            }
        }
    }

    fun signOut() {
        authRepository.signOut()
    }
}
