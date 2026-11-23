package com.secretcalculator.vault

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.secretcalculator.vault.data.preferences.AppThemeMode
import com.secretcalculator.vault.data.preferences.AutoLockPolicy
import com.secretcalculator.vault.ui.navigation.AppNavGraph
import com.secretcalculator.vault.ui.theme.SecretCalculatorTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private var isVaultUnlocked by mutableStateOf(false)
    private var backgroundTimestampMs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as SecretCalculatorApp

        // Observe app foreground/background transitions for automatic vault locking
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                backgroundTimestampMs = System.currentTimeMillis()
                val policy = runBlocking { app.preferences.autoLockPolicy.first() }
                if (policy == AutoLockPolicy.IMMEDIATE) {
                    lockVaultImmediately()
                }
            }

            override fun onStart(owner: LifecycleOwner) {
                if (!isVaultUnlocked || backgroundTimestampMs == 0L) return
                val policy = runBlocking { app.preferences.autoLockPolicy.first() }
                val elapsedSeconds = (System.currentTimeMillis() - backgroundTimestampMs) / 1000L
                if (elapsedSeconds >= policy.timeoutSeconds) {
                    lockVaultImmediately()
                }
            }
        })

        setContent {
            val themeMode by app.preferences.themeMode.collectAsState(initial = AppThemeMode.DARK)

            // Block screenshots & Android Recents preview whenever the Private Vault is unlocked
            if (isVaultUnlocked) {
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }

            SecretCalculatorTheme(themeMode = themeMode) {
                AppNavGraph(
                    app = app,
                    isVaultUnlocked = isVaultUnlocked,
                    onUnlockVault = { isVaultUnlocked = true },
                    onLockVault = { lockVaultImmediately() }
                )
            }
        }
    }

    private fun lockVaultImmediately() {
        isVaultUnlocked = false
    }
}
