package com.secretcalculator.vault.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.secretcalculator.vault.data.local.entity.AlbumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query("SELECT * FROM vault_albums ORDER BY createdAt ASC")
    fun observeAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM vault_albums ORDER BY createdAt ASC")
    suspend fun getAllAlbumsOnce(): List<AlbumEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlbum(album: AlbumEntity)

    @Query("UPDATE vault_albums SET name = :newName WHERE id = :albumId")
    suspend fun renameAlbum(albumId: String, newName: String)

    @Query("DELETE FROM vault_albums WHERE id = :albumId")
    suspend fun deleteAlbum(albumId: String)

    @Query("DELETE FROM vault_albums")
    suspend fun clearAllAlbums()
}
