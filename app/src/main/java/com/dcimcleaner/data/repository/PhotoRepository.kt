package com.dcimcleaner.data.repository

import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.dcimcleaner.data.db.AppDatabase
import com.dcimcleaner.data.model.DayStat
import com.dcimcleaner.data.model.MonthStat
import com.dcimcleaner.data.model.PhotoEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

sealed class TrashResult {
    object Success : TrashResult()
    data class NeedsIntent(val intentSender: android.content.IntentSender) : TrashResult()
    object Failed : TrashResult()
}

class PhotoRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val photoDao = db.photoDao()
    private val statsDao = db.statsDao()

    val monthStats = statsDao.getMonthStats()
    val dayStats = statsDao.getDayStats()

    suspend fun buildIndex(onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        photoDao.clearAll()
        statsDao.clearMonthStats()
        statsDao.clearDayStats()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )

        val dcimPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath
        val selection = "${MediaStore.Images.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("$dcimPath%")
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} ASC"

        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
            ?: return@withContext

        val total = cursor.count
        val dfMonth = SimpleDateFormat("yyyy-MM", Locale.US)
        val dfDay = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        val batch = mutableListOf<PhotoEntry>()
        var processed = 0

        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
        val wCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
        val hCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val name = cursor.getString(nameCol) ?: ""
            val path = cursor.getString(dataCol) ?: ""
            val dateTaken = cursor.getLong(dateCol).takeIf { it > 0 } ?: System.currentTimeMillis()
            val sizeBytes = cursor.getLong(sizeCol)
            val w = cursor.getInt(wCol)
            val h = cursor.getInt(hCol)

            val contentUri = ContentUris.withAppendedId(uri, id).toString()
            val cal = Calendar.getInstance().apply { timeInMillis = dateTaken }
            val monthKey = dfMonth.format(cal.time)
            val dayKey = dfDay.format(cal.time)
            val sizeMb = sizeBytes / 1_048_576f

            batch.add(PhotoEntry(contentUri, name, path, dateTaken, monthKey, dayKey, sizeMb, w, h))

            if (batch.size >= 500) {
                photoDao.insertPhotos(batch)
                batch.clear()
            }

            processed++
            if (processed % 200 == 0 || processed == total) {
                onProgress((processed * 100) / total.coerceAtLeast(1))
            }
        }
        cursor.close()

        if (batch.isNotEmpty()) photoDao.insertPhotos(batch)
        buildStats()
        onProgress(100)
    }

    private suspend fun buildStats() {
        val monthKeys = photoDao.getAllMonthKeys()
        val monthStats = monthKeys.map { key ->
            val photos = photoDao.getPhotosByMonth(key)
            MonthStat(key, photos.size, photos.sumOf { it.sizeMb.toDouble() }.toFloat())
        }
        statsDao.insertMonthStats(monthStats)

        val dayKeys = photoDao.getAllDayKeys()
        val dayStats = dayKeys.mapNotNull { key ->
            val photos = photoDao.getPhotosByDay(key)
            if (photos.size >= 50) DayStat(key, photos.size, photos.sumOf { it.sizeMb.toDouble() }.toFloat())
            else null
        }
        statsDao.insertDayStats(dayStats)
    }

    suspend fun getRandomMonthPhotos(): Pair<String, List<PhotoEntry>> {
        val keys = photoDao.getAllMonthKeys()
        val key = keys.randomOrNull() ?: return Pair("", emptyList())
        return Pair(key, photoDao.getPhotosByMonth(key))
    }

    suspend fun getRandomDayPhotos(): Pair<String, List<PhotoEntry>> {
        val stats = statsDao.getDayStatsSync()
        val key = stats.randomOrNull()?.date ?: return Pair("", emptyList())
        return Pair(key, photoDao.getPhotosByDay(key))
    }

    suspend fun getPhotosByMonth(month: String) = photoDao.getPhotosByMonth(month)
    suspend fun getPhotosByDay(day: String) = photoDao.getPhotosByDay(day)

    suspend fun moveToTrash(entry: PhotoEntry): TrashResult = withContext(Dispatchers.IO) {
        android.util.Log.d("TRASH", "Attempting trash for URI: ${entry.uri}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val uri = Uri.parse(entry.uri)
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_TRASHED, 1)
                }
                val rows = context.contentResolver.update(uri, values, null, null)
                if (rows > 0) {
                    android.util.Log.d("TRASH", "Trashed via IS_TRASHED flag ✓")
                    TrashResult.Success
                } else {
                    TrashResult.Failed
                }
            } catch (e: android.app.RecoverableSecurityException) {
                android.util.Log.d("TRASH", "RecoverableSecurityException — launching system dialog")
                TrashResult.NeedsIntent(e.userAction.actionIntent.intentSender)
            } catch (e: Exception) {
                android.util.Log.e("TRASH", "moveToTrash error: ${e.message}")
                TrashResult.Failed
            }
        } else {
            try {
                context.contentResolver.delete(Uri.parse(entry.uri), null, null)
                TrashResult.Success
            } catch (e: android.app.RecoverableSecurityException) {
                TrashResult.NeedsIntent(e.userAction.actionIntent.intentSender)
            } catch (e: Exception) {
                TrashResult.Failed
            }
        }
    }

    suspend fun retryTrashAfterPermission(uri: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.IS_TRASHED, 1)
            }
            val rows = context.contentResolver.update(Uri.parse(uri), values, null, null)
            android.util.Log.d("TRASH", "Post-permission trash rows: $rows")
            rows > 0
        } catch (e: Exception) {
            android.util.Log.e("TRASH", "Post-permission trash failed: ${e.message}")
            false
        }
    }

    suspend fun deleteViaContentResolver(entry: PhotoEntry) = withContext(Dispatchers.IO) {
        try { context.contentResolver.delete(Uri.parse(entry.uri), null, null) } catch (_: Exception) {}
    }

    suspend fun deleteFromIndex(uri: String) = photoDao.deletePhoto(uri)
    suspend fun isIndexBuilt() = photoDao.getTotalCount() > 0
}
