package com.secretcalculator.vault.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.secretcalculator.vault.data.local.entity.CalcHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalcHistoryDao {
    @Query("SELECT * FROM calc_history ORDER BY timestamp DESC LIMIT 50")
    fun observeHistory(): Flow<List<CalcHistoryEntity>>

    @Insert
    suspend fun insert(entry: CalcHistoryEntity)

    @Query("DELETE FROM calc_history")
    suspend fun clearHistory()
}
