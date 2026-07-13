package com.voicetap.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "regions")
data class RegionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phrase: String,
    val appPackage: String,
    val appLabel: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)
