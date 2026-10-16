package com.voicetap.data.db

import android.content.res.Configuration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [RegionEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun regionDao(): RegionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE regions ADD COLUMN orientation INTEGER NOT NULL DEFAULT ${Configuration.ORIENTATION_PORTRAIT}"
                )
            }
        }
    }
}
