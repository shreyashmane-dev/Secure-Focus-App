package com.example.focusshield.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.focusshield.data.AuthUiState

@Composable
fun AuthScreen(
    authState: AuthUiState,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onClearError: () -> Unit
) {
    var isRegisterMode by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Focus Shield", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (isRegisterMode) "Create a student account" else "Sign in to continue",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }

            if (isRegisterMode) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            authState.errorMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onClearError) {
                    Text("Dismiss")
                }
            }

            Button(
                onClick = {
                    if (isRegisterMode) {
                        onRegister(name, email, password)
                    } else {
                        onLogin(email, password)
                    }
                },
                enabled = !authState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (authState.isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text(if (isRegisterMode) "Register" else "Login")
                }
            }

            OutlinedButton(
                onClick = { isRegisterMode = !isRegisterMode },
                enabled = !authState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRegisterMode) "Use existing account" else "Create account")
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
