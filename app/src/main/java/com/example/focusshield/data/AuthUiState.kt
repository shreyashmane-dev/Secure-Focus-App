package com.example.focusshield.data

data class AuthUiState(
    val isLoading: Boolean = true,
    val isAuthenticated: Boolean = false,
    val uid: String? = null,
    val name: String = "",
    val email: String = "",
    val role: String = "student",
    val errorMessage: String? = null
)
