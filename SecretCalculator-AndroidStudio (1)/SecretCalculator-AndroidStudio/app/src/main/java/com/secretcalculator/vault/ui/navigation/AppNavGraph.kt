package com.secretcalculator.vault.ui.navigation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.secretcalculator.vault.SecretCalculatorApp
import com.secretcalculator.vault.data.preferences.AppThemeMode
import com.secretcalculator.vault.ui.calculator.CalculatorScreen
import com.secretcalculator.vault.ui.calculator.CalculatorViewModel
import com.secretcalculator.vault.ui.onboarding.PinSetupScreen
import com.secretcalculator.vault.ui.settings.SettingsScreen
import com.secretcalculator.vault.ui.vault.PhotoViewerScreen
import com.secretcalculator.vault.ui.vault.TrashScreen
import com.secretcalculator.vault.ui.vault.VaultScreen
import com.secretcalculator.vault.ui.vault.VaultViewModel

object Routes {
    const val PIN_SETUP = "pin_setup"
    const val CALCULATOR = "calculator"
    const val VAULT = "vault"
    const val TRASH = "trash"
    const val SETTINGS = "settings"
    const val PHOTO_VIEWER = "photo_viewer/{photoId}"

    fun photoViewer(photoId: String) = "photo_viewer/$photoId"
}

@Composable
fun AppNavGraph(
    app: SecretCalculatorApp,
    isVaultUnlocked: Boolean,
    onUnlockVault: () -> Unit,
    onLockVault: () -> Unit
) {
    val navController = rememberNavController()
    var isPinConfigured by remember { mutableStateOf(app.pinManager.isPinConfigured()) }
    val themeMode by app.preferences.themeMode.collectAsState(initial = AppThemeMode.DARK)
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val calculatorViewModel: CalculatorViewModel = viewModel(
        factory = CalculatorViewModel.factory(app.repository, app.pinManager)
    )
    val vaultViewModel: VaultViewModel = viewModel(
        factory = VaultViewModel.factory(app.repository, app.preferences, app.pinManager)
    )

    // Automatically return to Calculator whenever MainActivity locks the vault (e.g. backgrounding)
    LaunchedEffect(isVaultUnlocked) {
        if (!isVaultUnlocked && isPinConfigured) {
            navController.navigate(Routes.CALCULATOR) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val startDestination = if (isPinConfigured) Routes.CALCULATOR else Routes.PIN_SETUP

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.PIN_SETUP) {
            PinSetupScreen(
                onPinConfirmed = { newPin ->
                    app.pinManager.setPin(newPin)
                    isPinConfigured = true
                    navController.navigate(Routes.CALCULATOR) {
                        popUpTo(Routes.PIN_SETUP) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.CALCULATOR) {
            CalculatorScreen(
                viewModel = calculatorViewModel,
                isDarkTheme = isDark,
                onNavigateToVault = {
                    onUnlockVault()
                    navController.navigate(Routes.VAULT) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.VAULT) {
            VaultScreen(
                viewModel = vaultViewModel,
                onOpenPhoto = { id -> navController.navigate(Routes.photoViewer(id)) },
                onOpenTrash = { navController.navigate(Routes.TRASH) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onLockVault = {
                    onLockVault()
                }
            )
        }

        composable(
            route = Routes.PHOTO_VIEWER,
            arguments = listOf(navArgument("photoId") { type = NavType.StringType })
        ) { backStackEntry ->
            val photoId = backStackEntry.arguments?.getString("photoId") ?: return@composable
            PhotoViewerScreen(
                photoId = photoId,
                viewModel = vaultViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.TRASH) {
            TrashScreen(
                viewModel = vaultViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = vaultViewModel,
                onBack = { navController.popBackStack() },
                onResetPin = {
                    isPinConfigured = false
                    onLockVault()
                    navController.navigate(Routes.PIN_SETUP) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}
