package com.secretcalculator.vault.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.secretcalculator.vault.data.local.entity.VaultPhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    @Query("SELECT * FROM vault_photos WHERE isTrashed = 0 ORDER BY createdAt DESC")
    fun observeActivePhotos(): Flow<List<VaultPhotoEntity>>

    @Query("SELECT * FROM vault_photos WHERE isTrashed = 1 ORDER BY trashedAt DESC")
    fun observeTrashedPhotos(): Flow<List<VaultPhotoEntity>>

    @Query("SELECT * FROM vault_photos")
    suspend fun getAllPhotosOnce(): List<VaultPhotoEntity>

    @Query("SELECT * FROM vault_photos WHERE id = :id LIMIT 1")
    suspend fun getPhotoById(id: String): VaultPhotoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhoto(photo: VaultPhotoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhotos(photos: List<VaultPhotoEntity>)

    @Query("UPDATE vault_photos SET albumId = :targetAlbumId WHERE id IN (:photoIds)")
    suspend fun movePhotosToAlbum(photoIds: List<String>, targetAlbumId: String?)

    @Query("UPDATE vault_photos SET isTrashed = 1, trashedAt = :trashedAt WHERE id IN (:photoIds)")
    suspend fun softDeletePhotos(photoIds: List<String>, trashedAt: Long)

    @Query("UPDATE vault_photos SET isTrashed = 0, trashedAt = NULL WHERE id IN (:photoIds)")
    suspend fun restorePhotos(photoIds: List<String>)

    @Query("DELETE FROM vault_photos WHERE id IN (:photoIds)")
    suspend fun deletePhotosByIds(photoIds: List<String>)

    @Query("DELETE FROM vault_photos")
    suspend fun clearAllPhotos()

    @Query("SELECT COALESCE(SUM(encryptedSizeBytes), 0) FROM vault_photos")
    fun observeTotalEncryptedBytes(): Flow<Long>
}
