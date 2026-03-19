package com.dcimcleaner.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TrashedPhoto(
    val uri: Uri,
    val fileName: String,
    val sizeMb: Float,
    val filePath: String  // needed to re-find photo after restore (URI changes)
)

class TrashRepository(private val context: Context) {

    suspend fun getTrashedPhotos(): List<TrashedPhoto> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext emptyList()

        val bundle = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATA
        )
        val results = mutableListOf<TrashedPhoto>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, bundle, null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Unknown"
                val sizeBytes = cursor.getLong(sizeCol)
                val path = cursor.getString(pathCol) ?: ""
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )
                results.add(TrashedPhoto(uri, name, sizeBytes / 1_048_576f, path))
            }
        }
        results
    }

    suspend fun getTrashedCount(): Int = getTrashedPhotos().size
    suspend fun getTrashedSizeMb(): Float = getTrashedPhotos().sumOf { it.sizeMb.toDouble() }.toFloat()
}
