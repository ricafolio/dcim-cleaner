package com.dcimcleaner.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.dcimcleaner.data.model.DayStat
import com.dcimcleaner.data.model.MonthStat
import com.dcimcleaner.data.model.PhotoEntry

@Dao
interface PhotoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<PhotoEntry>)

    @Query("SELECT * FROM photo_index WHERE monthKey = :month ORDER BY dateTaken ASC")
    suspend fun getPhotosByMonth(month: String): List<PhotoEntry>

    @Query("SELECT * FROM photo_index WHERE dayKey = :day ORDER BY dateTaken ASC")
    suspend fun getPhotosByDay(day: String): List<PhotoEntry>

    @Query("SELECT DISTINCT monthKey FROM photo_index ORDER BY monthKey DESC")
    suspend fun getAllMonthKeys(): List<String>

    @Query("SELECT DISTINCT dayKey FROM photo_index ORDER BY dayKey DESC")
    suspend fun getAllDayKeys(): List<String>

    @Query("SELECT * FROM photo_index WHERE uri = :uri LIMIT 1")
    suspend fun getPhotoByUri(uri: String): PhotoEntry?

    @Query("DELETE FROM photo_index WHERE uri = :uri")
    suspend fun deletePhoto(uri: String)

    @Query("SELECT COUNT(*) FROM photo_index")
    suspend fun getTotalCount(): Int

    @Query("DELETE FROM photo_index")
    suspend fun clearAll()
}

@Dao
interface StatsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMonthStats(stats: List<MonthStat>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDayStats(stats: List<DayStat>)

    @Query("SELECT * FROM month_stats ORDER BY date DESC")
    fun getMonthStats(): LiveData<List<MonthStat>>

    @Query("SELECT * FROM day_stats ORDER BY date DESC")
    fun getDayStats(): LiveData<List<DayStat>>

    @Query("SELECT * FROM month_stats ORDER BY date DESC")
    suspend fun getMonthStatsSync(): List<MonthStat>

    @Query("SELECT * FROM day_stats ORDER BY date DESC")
    suspend fun getDayStatsSync(): List<DayStat>

    @Query("DELETE FROM month_stats")
    suspend fun clearMonthStats()

    @Query("DELETE FROM day_stats")
    suspend fun clearDayStats()
}
