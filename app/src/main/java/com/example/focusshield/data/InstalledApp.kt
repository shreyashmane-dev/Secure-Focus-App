package com.example.focusshield.data

data class InstalledApp(
    val label: String,
    val packageName: String
) {
    val displayName: String
        get() = "$label ($packageName)"
}
