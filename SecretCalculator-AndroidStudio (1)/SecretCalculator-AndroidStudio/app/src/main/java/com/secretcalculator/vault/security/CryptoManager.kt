package com.secretcalculator.vault.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hardware-backed Android Keystore AES-256-GCM cryptographic engine.
 * - Master vault key never leaves AndroidKeyStore.
 * - Every file uses a unique 96-bit (12-byte) IV prepended to ciphertext.
 * - Decryption occurs strictly in memory (ByteArray) with zero unencrypted temp files.
 * - Backup archives use PBKDF2-HMAC-SHA256 (200k rounds) + AES-256-GCM with explicit passphrase.
 */
class CryptoManager {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val VAULT_KEY_ALIAS = "com.secretcalculator.vault.master_aes_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH_BYTES = 12
        private const val TAG_LENGTH_BITS = 128
        private const val BACKUP_SALT_BYTES = 16
        private const val BACKUP_PBKDF2_ITERATIONS = 200_000
    }

    private val secureRandom = SecureRandom()

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existingKey = keyStore.getEntry(VAULT_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existingKey != null) {
            return existingKey.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
        val spec = KeyGenParameterSpec.Builder(
            VAULT_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun encryptBytes(plainBytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainBytes)

        return ByteArrayOutputStream(iv.size + encrypted.size).apply {
            write(iv)
            write(encrypted)
        }.toByteArray()
    }

    fun decryptBytes(cipherPayload: ByteArray): ByteArray {
        require(cipherPayload.size > IV_LENGTH_BYTES) { "Corrupted encrypted payload" }
        val iv = cipherPayload.copyOfRange(0, IV_LENGTH_BYTES)
        val cipherText = cipherPayload.copyOfRange(IV_LENGTH_BYTES, cipherPayload.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateMasterKey(), spec)
        return cipher.doFinal(cipherText)
    }

    fun encryptToFile(plainBytes: ByteArray, destination: File) {
        val payload = encryptBytes(plainBytes)
        destination.parentFile?.mkdirs()
        destination.writeBytes(payload)
    }

    fun decryptFromFile(source: File): ByteArray {
        require(source.exists()) { "Encrypted vault file not found" }
        return decryptBytes(source.readBytes())
    }

    /**
     * Overwrites file blocks with random noise and zeroes before unlinking
     * so deleted encrypted blobs cannot be recovered from flash blocks.
     */
    fun secureWipeFile(file: File): Boolean {
        if (!file.exists()) return true
        return try {
            val length = file.length()
            if (length > 0L) {
                RandomAccessFile(file, "rws").use { raf ->
                    val buffer = ByteArray(8192)
                    var remaining = length
                    raf.seek(0)
                    while (remaining > 0) {
                        val toWrite = minOf(remaining, buffer.size.toLong()).toInt()
                        secureRandom.nextBytes(buffer)
                        raf.write(buffer, 0, toWrite)
                        remaining -= toWrite
                    }
                    buffer.fill(0)
                    raf.seek(0)
                    remaining = length
                    while (remaining > 0) {
                        val toWrite = minOf(remaining, buffer.size.toLong()).toInt()
                        raf.write(buffer, 0, toWrite)
                        remaining -= toWrite
                    }
                }
            }
            file.delete()
        } catch (_: Exception) {
            file.delete()
        }
    }

    /**
     * Encrypts an exported backup bundle using a user-chosen recovery passphrase.
     * Layout: [16-byte Salt][12-byte IV][AES-256-GCM Ciphertext + Tag]
     */
    fun encryptBackupWithPassphrase(plainBytes: ByteArray, passphrase: CharArray): ByteArray {
        val salt = ByteArray(BACKUP_SALT_BYTES).also { secureRandom.nextBytes(it) }
        val key = derivePassphraseKey(passphrase, salt)
        val iv = ByteArray(IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val encrypted = cipher.doFinal(plainBytes)

        return ByteArrayOutputStream(salt.size + iv.size + encrypted.size).apply {
            write(salt)
            write(iv)
            write(encrypted)
        }.toByteArray()
    }

    fun decryptBackupWithPassphrase(backupPayload: ByteArray, passphrase: CharArray): ByteArray {
        val headerSize = BACKUP_SALT_BYTES + IV_LENGTH_BYTES
        require(backupPayload.size > headerSize) { "Invalid backup file format" }

        val salt = backupPayload.copyOfRange(0, BACKUP_SALT_BYTES)
        val iv = backupPayload.copyOfRange(BACKUP_SALT_BYTES, headerSize)
        val cipherText = backupPayload.copyOfRange(headerSize, backupPayload.size)

        val key = derivePassphraseKey(passphrase, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    private fun derivePassphraseKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase, salt, BACKUP_PBKDF2_ITERATIONS, 256)
        val rawKey = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(rawKey, "AES")
    }
}
