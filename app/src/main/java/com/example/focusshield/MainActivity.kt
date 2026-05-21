package com.example.focusshield

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.focusshield.monitoring.ImmersiveModeController
import com.example.focusshield.service.AppNotificationHelper
import com.example.focusshield.ui.FocusShieldApp
import com.example.focusshield.ui.theme.FocusShieldTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: FocusShieldViewModel by viewModels()
    private lateinit var immersiveModeController: ImmersiveModeController
    private var splashReady = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refresh(this)
        if (it) {
            AppNotificationHelper.showPermissionReadyNotification(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !splashReady }
        super.onCreate(savedInstanceState)
        viewModel.initialize(this)
        AppNotificationHelper.ensureChannels(this)
        immersiveModeController = ImmersiveModeController(this)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        lifecycleScope.launch {
            delay(1200)
            splashReady = true
        }

        setContent {
            FocusShieldTheme {
                val state by viewModel.state.collectAsState()
                val authState by viewModel.authState.collectAsState()
                var hasPromptedNotifications by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(state.hasNotificationPermission) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !state.hasNotificationPermission &&
                        !hasPromptedNotifications
                    ) {
                        hasPromptedNotifications = true
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                LaunchedEffect(state.isActive) {
                    if (state.isActive) {
                        while (true) {
                            immersiveModeController.enable()
                            delay(1_500)
                        }
                    } else {
                        immersiveModeController.restore()
                    }
                }

                FocusShieldApp(
                    authState = authState,
                    state = state,
                    onLogin = viewModel::login,
                    onRegister = viewModel::register,
                    onLogout = viewModel::logout,
                    onClearAuthError = viewModel::clearAuthError,
                    onProtectedPackageChange = viewModel::setProtectedPackage,
                    onStartProtection = { viewModel.startProtection(this) },
                    onStopProtection = { viewModel.stopProtection(this) },
                    onClearWarning = viewModel::clearWarning,
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onOpenOverlaySettings = {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                        )
                    },
                    onOpenNotificationSettings = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        }
                        startActivity(intent)
                    },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            AppNotificationHelper.showPermissionReadyNotification(this)
                        }
                    },
                    onShowTestNotification = {
                        AppNotificationHelper.showPermissionReadyNotification(this)
                    },
                    onRefreshSetup = {
                        viewModel.refresh(this)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh(this)
        if (viewModel.state.value.isActive) {
            immersiveModeController.enable()
        }
        if (isInMultiWindowMode) {
            viewModel.recordSplitScreenAttempt()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && viewModel.state.value.isActive) {
            immersiveModeController.enable()
        }
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean) {
        super.onMultiWindowModeChanged(isInMultiWindowMode)
        if (isInMultiWindowMode) {
            viewModel.recordSplitScreenAttempt()
            immersiveModeController.enable()
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            immersiveModeController.restore()
        }
        super.onDestroy()
    }
}
