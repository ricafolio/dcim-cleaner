package com.dcimcleaner.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.dcimcleaner.data.model.DayStat
import com.dcimcleaner.data.model.MonthStat
import com.dcimcleaner.data.model.PhotoEntry

@Database(
    entities = [PhotoEntry::class, MonthStat::class, DayStat::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun statsDao(): StatsDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dcim_cleaner.db"
                ).build().also { INSTANCE = it }
            }
    }
}
