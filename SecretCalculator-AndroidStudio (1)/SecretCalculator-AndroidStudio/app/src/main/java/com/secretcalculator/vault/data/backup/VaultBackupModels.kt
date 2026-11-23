package com.secretcalculator.vault.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class VaultBackupBundle(
    val version: Int = 1,
    val exportedAt: Long,
    val albums: List<BackupAlbumRecord>,
    val photos: List<BackupPhotoRecord>
)

@Serializable
data class BackupAlbumRecord(
    val id: String,
    val name: String,
    val createdAt: Long
)

@Serializable
data class BackupPhotoRecord(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val originalSizeBytes: Long,
    val width: Int,
    val height: Int,
    val albumId: String?,
    val createdAt: Long,
    val isTrashed: Boolean,
    val trashedAt: Long?,
    val imageBase64: String,
    val thumbBase64: String
)
