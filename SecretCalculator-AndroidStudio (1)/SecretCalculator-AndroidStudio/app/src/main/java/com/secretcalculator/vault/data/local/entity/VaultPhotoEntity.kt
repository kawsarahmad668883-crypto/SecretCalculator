package com.secretcalculator.vault.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vault_photos",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("albumId"), Index("isTrashed")]
)
data class VaultPhotoEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val mimeType: String,
    val originalSizeBytes: Long,
    val encryptedSizeBytes: Long,
    val width: Int,
    val height: Int,
    val albumId: String?,
    val encryptedFileName: String,
    val encryptedThumbName: String,
    val createdAt: Long,
    val isTrashed: Boolean = false,
    val trashedAt: Long? = null
)
