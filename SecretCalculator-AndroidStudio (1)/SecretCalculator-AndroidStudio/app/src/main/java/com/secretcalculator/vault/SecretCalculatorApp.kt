package com.secretcalculator.vault

import android.app.Application
import com.secretcalculator.vault.data.local.VaultDatabase
import com.secretcalculator.vault.data.preferences.VaultPreferences
import com.secretcalculator.vault.data.repository.VaultRepository
import com.secretcalculator.vault.security.CryptoManager
import com.secretcalculator.vault.security.PinManager

class SecretCalculatorApp : Application() {
    val cryptoManager: CryptoManager by lazy { CryptoManager() }
    val pinManager: PinManager by lazy { PinManager(this) }
    val database: VaultDatabase by lazy { VaultDatabase.getInstance(this) }
    val preferences: VaultPreferences by lazy { VaultPreferences(this) }
    val repository: VaultRepository by lazy {
        VaultRepository(this, database, cryptoManager)
    }
}
