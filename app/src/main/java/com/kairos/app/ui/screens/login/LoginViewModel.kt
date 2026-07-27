package com.kairos.app.ui.screens.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.repository.AuthRepository
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var rememberMe by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var isSignUpMode by mutableStateOf(false)

    fun onAction() {
        if (email.isBlank() || password.isBlank()) {
            errorMessage = "Please fill in all fields."
            return
        }

        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            try {
                if (isSignUpMode) {
                    authRepository.signUp(email, password)
                } else {
                    authRepository.signIn(email, password)
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Authentication failed."
            } finally {
                isLoading = false
            }
        }
    }

    fun onGoogleSignInResult(idToken: String) {
        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            try {
                authRepository.signInWithGoogle(idToken)
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Google Sign-In failed."
            } finally {
                isLoading = false
            }
        }
    }

    fun toggleMode() {
        isSignUpMode = !isSignUpMode
        errorMessage = null
    }
}
