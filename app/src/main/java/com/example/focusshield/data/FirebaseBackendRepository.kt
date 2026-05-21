package com.example.focusshield.data

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object FirebaseBackendRepository {
    private const val USERS_COLLECTION = "users"
    private const val SESSIONS_COLLECTION = "sessions"
    private const val LOGS_COLLECTION = "logs"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _authState = MutableStateFlow(AuthUiState())
    val authState: StateFlow<AuthUiState> = _authState

    private var configured = false
    private var listenerRegistered = false
    private var activeSessionId: String? = null

    fun initialize(context: Context) {
        if (!configured) {
            configured = runCatching {
                FirebaseApp.getApps(context).isNotEmpty() ||
                    FirebaseApp.initializeApp(context) != null
            }.getOrDefault(false)
        }
        if (!configured) {
            _authState.value = AuthUiState(
                isLoading = false,
                errorMessage = "Firebase is not configured. Add app/google-services.json."
            )
            return
        }
        if (listenerRegistered) return
        listenerRegistered = true
        Firebase.auth.addAuthStateListener { auth ->
            val user = auth.currentUser
            if (user == null) {
                activeSessionId = null
                _authState.value = AuthUiState(isLoading = false)
            } else {
                loadCurrentUser(user.uid, user.email.orEmpty())
            }
        }
    }

    fun register(name: String, email: String, password: String) {
        if (!isReady()) return
        val cleanName = name.trim()
        val cleanEmail = email.trim()
        if (cleanName.isBlank() || cleanEmail.isBlank() || password.isBlank()) {
            _authState.update { it.copy(isLoading = false, errorMessage = "Name, email, and password are required.") }
            return
        }
        scope.launch {
            _authState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val result = Firebase.auth.createUserWithEmailAndPassword(cleanEmail, password).await()
                val uid = result.user?.uid ?: error("Registration failed.")
                _authState.value = AuthUiState(
                    isLoading = false,
                    isAuthenticated = true,
                    uid = uid,
                    name = cleanName,
                    email = cleanEmail,
                    role = "student"
                )
                val user = hashMapOf(
                    "uid" to uid,
                    "name" to cleanName,
                    "email" to cleanEmail,
                    "role" to "student",
                    "createdAt" to FieldValue.serverTimestamp()
                )
                runCatching {
                    Firebase.firestore.collection(USERS_COLLECTION).document(uid).set(user).await()
                }.onFailure { error ->
                    _authState.update {
                        it.copy(errorMessage = firestoreSetupMessage(error))
                    }
                }
                loadCurrentUser(uid, cleanEmail)
            }.onFailure { error ->
                _authState.update { it.copy(isLoading = false, errorMessage = error.message ?: "Registration failed.") }
            }
        }
    }

    fun login(email: String, password: String) {
        if (!isReady()) return
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || password.isBlank()) {
            _authState.update { it.copy(isLoading = false, errorMessage = "Email and password are required.") }
            return
        }
        scope.launch {
            _authState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val result = Firebase.auth.signInWithEmailAndPassword(cleanEmail, password).await()
                val uid = result.user?.uid ?: error("Login failed.")
                _authState.value = AuthUiState(
                    isLoading = false,
                    isAuthenticated = true,
                    uid = uid,
                    name = cleanEmail.substringBefore('@'),
                    email = cleanEmail,
                    role = "student"
                )
                loadCurrentUser(uid, cleanEmail)
            }.onFailure { error ->
                _authState.update { it.copy(isLoading = false, errorMessage = error.message ?: "Login failed.") }
            }
        }
    }

    fun logout() {
        if (!configured) {
            _authState.value = AuthUiState(isLoading = false)
            return
        }
        activeSessionId = null
        Firebase.auth.signOut()
        _authState.value = AuthUiState(isLoading = false)
    }

    fun clearError() {
        _authState.update { it.copy(errorMessage = null) }
    }

    fun startSession(testName: String, startedAt: Long) {
        if (!configured) return
        val student = _authState.value
        val uid = student.uid ?: return
        scope.launch {
            runCatching {
                val sessionRef = Firebase.firestore.collection(SESSIONS_COLLECTION).document()
                activeSessionId = sessionRef.id
                val session = hashMapOf(
                    "sessionId" to sessionRef.id,
                    "studentId" to uid,
                    "studentName" to student.name.ifBlank { student.email },
                    "testName" to testName.ifBlank { "Exam session" },
                    "startedAt" to FieldValue.serverTimestamp(),
                    "startedAtMillis" to startedAt,
                    "status" to "active",
                    "riskScore" to 0,
                    "totalViolations" to 0,
                    "lastActivity" to FieldValue.serverTimestamp()
                )
                sessionRef.set(session).await()
            }
        }
    }

    fun completeSession() {
        if (!configured) return
        val sessionId = activeSessionId ?: return
        scope.launch {
            runCatching {
                Firebase.firestore.collection(SESSIONS_COLLECTION).document(sessionId)
                    .set(
                        mapOf(
                            "status" to "completed",
                            "completedAt" to FieldValue.serverTimestamp(),
                            "lastActivity" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                    .await()
            }
            activeSessionId = null
        }
    }

    fun logViolation(violationType: String, severity: String, details: String) {
        if (!configured) return
        val sessionId = activeSessionId ?: return
        val student = _authState.value
        val uid = student.uid ?: return
        val riskDelta = riskScoreFor(violationType)
        scope.launch {
            runCatching {
                val firestore = Firebase.firestore
                val logRef = firestore.collection(LOGS_COLLECTION).document()
                val log = hashMapOf(
                    "logId" to logRef.id,
                    "sessionId" to sessionId,
                    "studentId" to uid,
                    "studentName" to student.name.ifBlank { student.email },
                    "violationType" to violationType,
                    "severity" to severity,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "details" to details
                )
                logRef.set(log).await()
                firestore.collection(SESSIONS_COLLECTION).document(sessionId)
                    .set(
                        mapOf(
                            "totalViolations" to FieldValue.increment(1),
                            "riskScore" to FieldValue.increment(riskDelta.toLong()),
                            "lastActivity" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                    .await()
            }
        }
    }

    private fun loadCurrentUser(uid: String, fallbackEmail: String) {
        scope.launch {
            val fallbackName = fallbackEmail.substringBefore('@').ifBlank { "Student" }
            _authState.update {
                it.copy(
                    isLoading = false,
                    isAuthenticated = true,
                    uid = uid,
                    name = it.name.ifBlank { fallbackName },
                    email = it.email.ifBlank { fallbackEmail },
                    role = it.role.ifBlank { "student" }
                )
            }
            runCatching {
                val snapshot = Firebase.firestore.collection(USERS_COLLECTION).document(uid).get().await()
                if (!snapshot.exists()) {
                    val user = Firebase.auth.currentUser
                    val profile = mapOf(
                        "uid" to uid,
                        "name" to (user?.displayName ?: fallbackName),
                        "email" to fallbackEmail,
                        "role" to "student",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    Firebase.firestore.collection(USERS_COLLECTION).document(uid).set(profile).await()
                }
                val fresh = Firebase.firestore.collection(USERS_COLLECTION).document(uid).get().await()
                _authState.value = AuthUiState(
                    isLoading = false,
                    isAuthenticated = true,
                    uid = uid,
                    name = fresh.getString("name").orEmpty(),
                    email = fresh.getString("email") ?: fallbackEmail,
                    role = fresh.getString("role") ?: "student"
                )
            }.onFailure { error ->
                _authState.update {
                    it.copy(
                        isLoading = false,
                        isAuthenticated = true,
                        uid = uid,
                        name = it.name.ifBlank { fallbackName },
                        email = it.email.ifBlank { fallbackEmail },
                        role = it.role.ifBlank { "student" },
                        errorMessage = firestoreSetupMessage(error)
                    )
                }
            }
        }
    }

    private fun isReady(): Boolean {
        if (configured) return true
        _authState.value = AuthUiState(
            isLoading = false,
            errorMessage = "Firebase is not configured. Add app/google-services.json."
        )
        return false
    }

    private fun riskScoreFor(violationType: String): Int {
        return when (violationType) {
            "APP_SWITCH", "LEFT_APP" -> 10
            "OVERLAY_DETECTED" -> 20
            "SCREEN_OFF" -> 15
            "SPLIT_SCREEN" -> 15
            else -> 5
        }
    }

    private fun firestoreSetupMessage(error: Throwable): String {
        val message = error.message.orEmpty()
        return if (message.contains("PERMISSION_DENIED", ignoreCase = true) ||
            message.contains("insufficient permissions", ignoreCase = true)
        ) {
            "Logged in. Firestore rules need to be published before cloud sync works."
        } else {
            "Logged in. Profile sync pending: ${message.ifBlank { "Firestore unavailable." }}"
        }
    }
}
