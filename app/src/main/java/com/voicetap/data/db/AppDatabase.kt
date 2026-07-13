package com.voicetap.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [RegionEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun regionDao(): RegionDao
}
