package com.example.focusshield.data

data class SessionLog(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: LogType,
    val message: String,
    val source: String = "Session"
)

enum class LogType {
    Info,
    Warning,
    Violation
}
