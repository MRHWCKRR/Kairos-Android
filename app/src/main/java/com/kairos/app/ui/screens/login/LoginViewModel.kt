package com.kairos.app.ui.screens.login

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.local.PreferenceManager
import com.kairos.app.data.repository.AuthRepository
import kotlinx.coroutines.launch

class LoginViewModel @JvmOverloads constructor(
    application: Application,
    private val authRepository: AuthRepository = AuthRepository()
) : AndroidViewModel(application) {

    private val preferenceManager = PreferenceManager(application)

    var email by mutableStateOf(preferenceManager.getEmail() ?: "")
    var password by mutableStateOf("")
    var rememberMe by mutableStateOf(preferenceManager.getEmail() != null)
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

        if (rememberMe) {
            preferenceManager.saveEmail(email)
        } else {
            preferenceManager.clearEmail()
        }

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
