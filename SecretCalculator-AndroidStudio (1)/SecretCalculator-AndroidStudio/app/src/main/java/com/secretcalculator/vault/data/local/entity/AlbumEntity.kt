package com.secretcalculator.vault.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long
)
