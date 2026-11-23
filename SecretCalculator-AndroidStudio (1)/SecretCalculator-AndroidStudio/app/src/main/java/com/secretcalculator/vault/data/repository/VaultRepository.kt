package com.secretcalculator.vault.data.repository

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.secretcalculator.vault.data.backup.BackupAlbumRecord
import com.secretcalculator.vault.data.backup.BackupPhotoRecord
import com.secretcalculator.vault.data.backup.VaultBackupBundle
import com.secretcalculator.vault.data.local.VaultDatabase
import com.secretcalculator.vault.data.local.entity.AlbumEntity
import com.secretcalculator.vault.data.local.entity.CalcHistoryEntity
import com.secretcalculator.vault.data.local.entity.VaultPhotoEntity
import com.secretcalculator.vault.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class VaultRepository(
    private val context: Context,
    private val database: VaultDatabase,
    private val cryptoManager: CryptoManager
) {
    private val vaultDao = database.vaultDao()
    private val albumDao = database.albumDao()
    private val historyDao = database.calcHistoryDao()

    private val vaultDir: File
        get() = File(context.filesDir, "vault_blobs").apply { if (!exists()) mkdirs() }

    private val json = Json { ignoreUnknownKeys = true }

    val activePhotos: Flow<List<VaultPhotoEntity>> = vaultDao.observeActivePhotos()
    val trashedPhotos: Flow<List<VaultPhotoEntity>> = vaultDao.observeTrashedPhotos()
    val albums: Flow<List<AlbumEntity>> = albumDao.observeAlbums()
    val totalEncryptedBytes: Flow<Long> = vaultDao.observeTotalEncryptedBytes()
    val calcHistory: Flow<List<CalcHistoryEntity>> = historyDao.observeHistory()

    suspend fun saveCalculation(expression: String, result: String) = withContext(Dispatchers.IO) {
        historyDao.insert(
            CalcHistoryEntity(
                expression = expression,
                result = result,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearCalculationHistory() = withContext(Dispatchers.IO) {
        historyDao.clearHistory()
    }

    /**
     * Imports one or more URIs selected from the Android Photo Picker.
     * Reads raw bytes in memory, creates a compressed thumbnail in memory,
     * and encrypts both directly to internal app storage using AES-256-GCM.
     * Zero unencrypted temporary files are written to disk.
     */
    suspend fun importPhotos(
        uris: List<Uri>,
        targetAlbumId: String?
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            var importedCount = 0

            for (uri in uris) {
                val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: continue
                if (rawBytes.isEmpty()) continue

                val photoId = UUID.randomUUID().toString()
                val fileName = resolveDisplayName(resolver, uri) ?: "IMG_${photoId.take(8)}.jpg"
                val mimeType = resolver.getType(uri) ?: "image/jpeg"

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)

                val thumbBytes = createThumbnailBytes(rawBytes)

                val encPhotoName = "$photoId.enc"
                val encThumbName = "${photoId}_thumb.enc"

                val encPhotoFile = File(vaultDir, encPhotoName)
                val encThumbFile = File(vaultDir, encThumbName)

                cryptoManager.encryptToFile(rawBytes, encPhotoFile)
                cryptoManager.encryptToFile(thumbBytes, encThumbFile)

                val entity = VaultPhotoEntity(
                    id = photoId,
                    displayName = fileName,
                    mimeType = mimeType,
                    originalSizeBytes = rawBytes.size.toLong(),
                    encryptedSizeBytes = encPhotoFile.length() + encThumbFile.length(),
                    width = bounds.outWidth.coerceAtLeast(1),
                    height = bounds.outHeight.coerceAtLeast(1),
                    albumId = targetAlbumId,
                    encryptedFileName = encPhotoName,
                    encryptedThumbName = encThumbName,
                    createdAt = System.currentTimeMillis(),
                    isTrashed = false
                )

                vaultDao.upsertPhoto(entity)
                rawBytes.fill(0)
                importedCount++
            }
            importedCount
        }
    }

    suspend fun decryptThumbnail(photo: VaultPhotoEntity): ByteArray = withContext(Dispatchers.IO) {
        cryptoManager.decryptFromFile(File(vaultDir, photo.encryptedThumbName))
    }

    suspend fun decryptFullPhoto(photo: VaultPhotoEntity): ByteArray = withContext(Dispatchers.IO) {
        cryptoManager.decryptFromFile(File(vaultDir, photo.encryptedFileName))
    }

    suspend fun createAlbum(name: String): AlbumEntity = withContext(Dispatchers.IO) {
        val clean = name.trim().take(40).ifEmpty { "Untitled Album" }
        val album = AlbumEntity(
            id = UUID.randomUUID().toString(),
            name = clean,
            createdAt = System.currentTimeMillis()
        )
        albumDao.upsertAlbum(album)
        album
    }

    suspend fun renameAlbum(albumId: String, newName: String) = withContext(Dispatchers.IO) {
        val clean = newName.trim().take(40)
        if (clean.isNotEmpty()) {
            albumDao.renameAlbum(albumId, clean)
        }
    }

    suspend fun deleteAlbum(albumId: String) = withContext(Dispatchers.IO) {
        // Photos in this album remain safely in All Photos (albumId set to NULL via FK)
        albumDao.deleteAlbum(albumId)
    }

    suspend fun movePhotosToAlbum(photoIds: List<String>, targetAlbumId: String?) =
        withContext(Dispatchers.IO) {
            vaultDao.movePhotosToAlbum(photoIds, targetAlbumId)
        }

    suspend fun moveToTrash(photoIds: List<String>) = withContext(Dispatchers.IO) {
        vaultDao.softDeletePhotos(photoIds, System.currentTimeMillis())
    }

    suspend fun restoreFromTrash(photoIds: List<String>) = withContext(Dispatchers.IO) {
        vaultDao.restorePhotos(photoIds)
    }

    suspend fun permanentlyDelete(photoIds: List<String>) = withContext(Dispatchers.IO) {
        for (id in photoIds) {
            val photo = vaultDao.getPhotoById(id) ?: continue
            cryptoManager.secureWipeFile(File(vaultDir, photo.encryptedFileName))
            cryptoManager.secureWipeFile(File(vaultDir, photo.encryptedThumbName))
        }
        vaultDao.deletePhotosByIds(photoIds)
    }

    /**
     * Exports an encrypted .scvault archive protected by a user recovery passphrase.
     */
    suspend fun exportEncryptedBackup(
        destinationUri: Uri,
        passphrase: CharArray
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val albums = albumDao.getAllAlbumsOnce().map {
                BackupAlbumRecord(it.id, it.name, it.createdAt)
            }
            val photos = vaultDao.getAllPhotosOnce().map { entity ->
                val fullBytes = cryptoManager.decryptFromFile(File(vaultDir, entity.encryptedFileName))
                val thumbBytes = cryptoManager.decryptFromFile(File(vaultDir, entity.encryptedThumbName))
                val record = BackupPhotoRecord(
                    id = entity.id,
                    displayName = entity.displayName,
                    mimeType = entity.mimeType,
                    originalSizeBytes = entity.originalSizeBytes,
                    width = entity.width,
                    height = entity.height,
                    albumId = entity.albumId,
                    createdAt = entity.createdAt,
                    isTrashed = entity.isTrashed,
                    trashedAt = entity.trashedAt,
                    imageBase64 = Base64.encodeToString(fullBytes, Base64.NO_WRAP),
                    thumbBase64 = Base64.encodeToString(thumbBytes, Base64.NO_WRAP)
                )
                fullBytes.fill(0)
                record
            }

            val bundle = VaultBackupBundle(
                exportedAt = System.currentTimeMillis(),
                albums = albums,
                photos = photos
            )
            val jsonBytes = json.encodeToString(bundle).toByteArray(Charsets.UTF_8)
            val encryptedArchive = cryptoManager.encryptBackupWithPassphrase(jsonBytes, passphrase)
            jsonBytes.fill(0)

            context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                out.write(encryptedArchive)
                out.flush()
            } ?: error("Unable to open backup destination")

            photos.size
        }
    }

    /**
     * Restores an encrypted .scvault archive into the device's hardware-backed Android Keystore vault.
     */
    suspend fun restoreEncryptedBackup(
        sourceUri: Uri,
        passphrase: CharArray,
        replaceExisting: Boolean
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val encryptedArchive = context.contentResolver.openInputStream(sourceUri)?.use {
                it.readBytes()
            } ?: error("Unable to read backup file")

            val plainJsonBytes = cryptoManager.decryptBackupWithPassphrase(encryptedArchive, passphrase)
            val bundle = json.decodeFromString<VaultBackupBundle>(
                plainJsonBytes.toString(Charsets.UTF_8)
            )
            plainJsonBytes.fill(0)

            if (replaceExisting) {
                vaultDao.getAllPhotosOnce().forEach {
                    cryptoManager.secureWipeFile(File(vaultDir, it.encryptedFileName))
                    cryptoManager.secureWipeFile(File(vaultDir, it.encryptedThumbName))
                }
                vaultDao.clearAllPhotos()
                albumDao.clearAllAlbums()
            }

            bundle.albums.forEach { a ->
                albumDao.upsertAlbum(AlbumEntity(a.id, a.name, a.createdAt))
            }

            val restoredEntities = bundle.photos.map { p ->
                val fullBytes = Base64.decode(p.imageBase64, Base64.NO_WRAP)
                val thumbBytes = Base64.decode(p.thumbBase64, Base64.NO_WRAP)

                val encPhotoName = "${p.id}.enc"
                val encThumbName = "${p.id}_thumb.enc"
                val encPhotoFile = File(vaultDir, encPhotoName)
                val encThumbFile = File(vaultDir, encThumbName)

                cryptoManager.encryptToFile(fullBytes, encPhotoFile)
                cryptoManager.encryptToFile(thumbBytes, encThumbFile)
                fullBytes.fill(0)

                VaultPhotoEntity(
                    id = p.id,
                    displayName = p.displayName,
                    mimeType = p.mimeType,
                    originalSizeBytes = p.originalSizeBytes,
                    encryptedSizeBytes = encPhotoFile.length() + encThumbFile.length(),
                    width = p.width,
                    height = p.height,
                    albumId = p.albumId,
                    encryptedFileName = encPhotoName,
                    encryptedThumbName = encThumbName,
                    createdAt = p.createdAt,
                    isTrashed = p.isTrashed,
                    trashedAt = p.trashedAt
                )
            }
            vaultDao.upsertPhotos(restoredEntities)
            restoredEntities.size
        }
    }

    private fun resolveDisplayName(resolver: ContentResolver, uri: Uri): String? {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return null
    }

    private fun createThumbnailBytes(rawBytes: ByteArray): ByteArray {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts)

        val maxDim = 480
        var sampleSize = 1
        while (opts.outWidth / sampleSize > maxDim || opts.outHeight / sampleSize > maxDim) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
            ?: return rawBytes

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
        bitmap.recycle()
        return out.toByteArray()
    }
}
