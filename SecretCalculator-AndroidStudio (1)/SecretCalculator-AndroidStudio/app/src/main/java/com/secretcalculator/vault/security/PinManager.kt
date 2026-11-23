package com.secretcalculator.vault.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Stores and verifies the user's secret calculator PIN using PBKDF2-HMAC-SHA256
 * inside AES-256 EncryptedSharedPreferences backed by Android Keystore.
 * PIN is never stored in plaintext or exposed in logs/UI.
 */
class PinManager(context: Context) {

    companion object {
        private const val PREF_FILE = "sc_secure_pin_store"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_LENGTH = "pin_length"
        private const val PBKDF2_ITERATIONS = 120_000
    }

    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREF_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val secureRandom = SecureRandom()

    fun isPinConfigured(): Boolean {
        return securePrefs.contains(KEY_PIN_HASH) && securePrefs.contains(KEY_PIN_SALT)
    }

    fun getPinLength(): Int {
        return securePrefs.getInt(KEY_PIN_LENGTH, 4)
    }

    fun setPin(pin: String) {
        require(pin.length in 4..12 && pin.all { it.isDigit() }) {
            "PIN must be 4 to 12 digits"
        }
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val hash = pbkdf2Hash(pin.toCharArray(), salt)

        securePrefs.edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt(KEY_PIN_LENGTH, pin.length)
            .apply()
    }

    fun verifyPin(candidate: String): Boolean {
        if (candidate.isBlank() || !candidate.all { it.isDigit() }) return false
        val saltB64 = securePrefs.getString(KEY_PIN_SALT, null) ?: return false
        val hashB64 = securePrefs.getString(KEY_PIN_HASH, null) ?: return false

        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val expectedHash = Base64.decode(hashB64, Base64.NO_WRAP)
        val candidateHash = pbkdf2Hash(candidate.toCharArray(), salt)

        return MessageDigest.isEqual(expectedHash, candidateHash)
    }

    fun clearPin() {
        securePrefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .remove(KEY_PIN_LENGTH)
            .apply()
    }

    private fun pbkdf2Hash(pinChars: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pinChars, salt, PBKDF2_ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
