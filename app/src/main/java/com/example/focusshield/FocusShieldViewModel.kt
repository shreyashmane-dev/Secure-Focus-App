package com.example.focusshield

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import com.example.focusshield.data.AuthUiState
import com.example.focusshield.data.FirebaseBackendRepository
import com.example.focusshield.data.ProtectionRepository
import com.example.focusshield.service.ProtectionMonitoringService
import kotlinx.coroutines.flow.StateFlow

class FocusShieldViewModel : ViewModel() {
    val state: StateFlow<com.example.focusshield.data.ProtectionUiState> = ProtectionRepository.state
    val authState: StateFlow<AuthUiState> = FirebaseBackendRepository.authState

    fun initialize(context: Context) {
        FirebaseBackendRepository.initialize(context.applicationContext)
        ProtectionRepository.initialize(context.applicationContext)
    }

    fun register(name: String, email: String, password: String) {
        FirebaseBackendRepository.register(name, email, password)
    }

    fun login(email: String, password: String) {
        FirebaseBackendRepository.login(email, password)
    }

    fun logout() {
        FirebaseBackendRepository.logout()
    }

    fun clearAuthError() {
        FirebaseBackendRepository.clearError()
    }

    fun setProtectedPackage(packageName: String) {
        ProtectionRepository.setProtectedPackage(packageName)
    }

    fun refresh(context: Context) {
        ProtectionRepository.refreshPermissionState(context.applicationContext)
    }

    fun startProtection(context: Context): Boolean {
        val appContext = context.applicationContext
        if (!ProtectionRepository.canStartProtection(appContext)) return false
        ProtectionRepository.startProtection(appContext)
        ProtectionMonitoringService.start(appContext)
        appContext.packageManager.getLaunchIntentForPackage(state.value.protectedAppPackage)?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
        }
        return true
    }

    fun stopProtection(context: Context) {
        ProtectionRepository.stopProtection()
        ProtectionMonitoringService.stop(context.applicationContext)
    }

    fun clearWarning() {
        ProtectionRepository.clearWarning()
    }

    fun recordSplitScreenAttempt() {
        ProtectionRepository.recordSplitScreenAttempt()
    }
}
