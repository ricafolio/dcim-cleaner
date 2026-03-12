package com.dcimcleaner.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "month_stats")
data class MonthStat(
    @PrimaryKey val date: String,   // "2022-05"
    val fileCount: Int,
    val totalSizeMb: Float
)

@Entity(tableName = "day_stats")
data class DayStat(
    @PrimaryKey val date: String,   // "2022-05-25"
    val fileCount: Int,
    val totalSizeMb: Float
)

@Entity(tableName = "photo_index")
data class PhotoEntry(
    @PrimaryKey val uri: String,
    val fileName: String,
    val filePath: String,
    val dateTaken: Long,            // epoch ms
    val monthKey: String,           // "2022-05"
    val dayKey: String,             // "2022-05-25"
    val sizeMb: Float,
    val width: Int,
    val height: Int
)
