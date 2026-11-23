package com.secretcalculator.vault.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.secretcalculator.vault.data.local.dao.AlbumDao
import com.secretcalculator.vault.data.local.dao.CalcHistoryDao
import com.secretcalculator.vault.data.local.dao.VaultDao
import com.secretcalculator.vault.data.local.entity.AlbumEntity
import com.secretcalculator.vault.data.local.entity.CalcHistoryEntity
import com.secretcalculator.vault.data.local.entity.VaultPhotoEntity

@Database(
    entities = [
        VaultPhotoEntity::class,
        AlbumEntity::class,
        CalcHistoryEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
    abstract fun albumDao(): AlbumDao
    abstract fun calcHistoryDao(): CalcHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: VaultDatabase? = null

        fun getInstance(context: Context): VaultDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VaultDatabase::class.java,
                    "secret_calculator_vault.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
