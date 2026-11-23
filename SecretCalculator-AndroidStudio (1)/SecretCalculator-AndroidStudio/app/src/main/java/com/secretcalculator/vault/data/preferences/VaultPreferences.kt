package com.secretcalculator.vault.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "secret_calculator_prefs")

enum class AppThemeMode { DARK, LIGHT, SYSTEM }
enum class AutoLockPolicy(val label: String, val timeoutSeconds: Long) {
    IMMEDIATE("Immediately on background", 0L),
    THIRTY_SECONDS("After 30 seconds idle", 30L),
    ONE_MINUTE("After 1 minute idle", 60L),
    FIVE_MINUTES("After 5 minutes idle", 300L)
}
enum class SortOrder(val label: String) {
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)")
}

class VaultPreferences(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUTO_LOCK = stringPreferencesKey("auto_lock")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")
    }

    val themeMode: Flow<AppThemeMode> = context.dataStore.data.map { prefs ->
        runCatching { AppThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: AppThemeMode.DARK.name) }
            .getOrDefault(AppThemeMode.DARK)
    }

    val autoLockPolicy: Flow<AutoLockPolicy> = context.dataStore.data.map { prefs ->
        runCatching { AutoLockPolicy.valueOf(prefs[Keys.AUTO_LOCK] ?: AutoLockPolicy.IMMEDIATE.name) }
            .getOrDefault(AutoLockPolicy.IMMEDIATE)
    }

    val sortOrder: Flow<SortOrder> = context.dataStore.data.map { prefs ->
        runCatching { SortOrder.valueOf(prefs[Keys.SORT_ORDER] ?: SortOrder.DATE_DESC.name) }
            .getOrDefault(SortOrder.DATE_DESC)
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAutoLockPolicy(policy: AutoLockPolicy) {
        context.dataStore.edit { it[Keys.AUTO_LOCK] = policy.name }
    }

    suspend fun setSortOrder(order: SortOrder) {
        context.dataStore.edit { it[Keys.SORT_ORDER] = order.name }
    }
}
