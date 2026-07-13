package com.voicetap.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RegionDao {
    @Query("SELECT * FROM regions ORDER BY id ASC")
    fun observeAll(): Flow<List<RegionEntity>>

    @Query("SELECT * FROM regions ORDER BY id ASC")
    suspend fun getAll(): List<RegionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(region: RegionEntity): Long

    @Query("DELETE FROM regions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
