package com.secretcalculator.vault.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.secretcalculator.vault.data.preferences.AppThemeMode

private val DarkColors = darkColorScheme(
    primary = VividOrange,
    onPrimary = Color.White,
    background = ObsidianBlack,
    onBackground = Color.White,
    surface = DarkSurface,
    onSurface = Color.White,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = Color(0xFFA1A1AA),
    error = DangerCrimson,
    onError = Color.White
)

private val LightColors = lightColorScheme(
    primary = VividOrange,
    onPrimary = Color.White,
    background = LightCanvas,
    onBackground = TextDarkPrimary,
    surface = LightSurface,
    onSurface = TextDarkPrimary,
    surfaceVariant = LightSurfaceSecondary,
    onSurfaceVariant = TextDarkSecondary,
    error = DangerCrimson,
    onError = Color.White
)

@Composable
fun SecretCalculatorTheme(
    themeMode: AppThemeMode,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (isDark) DarkColors else LightColors,
        typography = SecretCalcTypography,
        content = content
    )
}
