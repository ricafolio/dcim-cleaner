package com.dcimcleaner.ui.fullscreen

import android.app.Activity
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.dcimcleaner.data.db.AppDatabase
import com.dcimcleaner.data.model.PhotoEntry
import com.dcimcleaner.data.repository.PhotoRepository
import com.dcimcleaner.data.repository.TrashResult
import com.dcimcleaner.databinding.ActivityFullscreenBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class FullscreenActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URIS = "extra_uris"
        const val EXTRA_POSITION = "extra_position"
    }

    private lateinit var binding: ActivityFullscreenBinding
    private lateinit var adapter: FullscreenAdapter
    private var photos = mutableListOf<PhotoEntry>()
    private lateinit var repo: PhotoRepository
    private var pendingTrashEntry: PhotoEntry? = null

    private val trashLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingTrashEntry?.let { entry ->
                lifecycleScope.launch {
                    try {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.IS_TRASHED, 1)
                        }
                        val rows = contentResolver.update(Uri.parse(entry.uri), values, null, null)
                        if (rows > 0) removeCurrentPhoto()
                    } catch (e: Exception) {
                        android.util.Log.e("TRASH", "Post-permission failed: ${e.message}")
                    }
                }
            }
        }
        pendingTrashEntry = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullscreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = PhotoRepository(this)

        val uris = intent.getStringArrayListExtra(EXTRA_URIS) ?: return
        val startPos = intent.getIntExtra(EXTRA_POSITION, 0)

        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@FullscreenActivity).photoDao()
            photos = uris.map { uri ->
                dao.getPhotoByUri(uri) ?: queryMediaStore(uri)
            }.toMutableList()

            adapter = FullscreenAdapter(photos) { entry -> updateInfo(entry) }
            binding.viewPager.adapter = adapter
            binding.viewPager.setCurrentItem(startPos, false)
            updateInfo(photos.getOrNull(startPos))
        }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateInfo(photos.getOrNull(position))
            }
        })

        binding.btnTrash.setOnClickListener {
            photos.getOrNull(binding.viewPager.currentItem)?.let { trashCurrent(it) }
        }

        binding.btnBack.setOnClickListener { finish() }

    }

    private var touchStartY = 0f
    private var touchStartX = 0f

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        when (ev.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                touchStartY = ev.y
                touchStartX = ev.x
            }
            android.view.MotionEvent.ACTION_UP -> {
                val deltaY = ev.y - touchStartY
                val deltaX = kotlin.math.abs(ev.x - touchStartX)
                if (deltaY > 200 && deltaX < deltaY * 0.5f) {
                    if (android.os.Build.VERSION.SDK_INT >= 34) {
                        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0,
                            com.dcimcleaner.R.anim.slide_down)
                    } else {
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, com.dcimcleaner.R.anim.slide_down)
                    }
                    finish()
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
    private fun trashCurrent(entry: PhotoEntry) {
        lifecycleScope.launch {
            when (val result = repo.moveToTrash(entry)) {
                is TrashResult.Success -> removeCurrentPhoto()
                is TrashResult.NeedsIntent -> {
                    pendingTrashEntry = entry
                    trashLauncher.launch(IntentSenderRequest.Builder(result.intentSender).build())
                }
                is TrashResult.Failed -> android.util.Log.e("TRASH", "Failed to trash")
            }
        }
    }

    private fun removeCurrentPhoto() {
        val pos = binding.viewPager.currentItem
        if (pos < photos.size) {
            photos.removeAt(pos)
            adapter.updatePhotos(photos)
            if (photos.isEmpty()) finish()
            else updateInfo(photos.getOrNull(binding.viewPager.currentItem))
        }
    }

    private fun updateInfo(entry: PhotoEntry?) {
        entry ?: return
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val dateStr = if (entry.dateTaken > 0) sdf.format(Date(entry.dateTaken)) else "Unknown"
        binding.tvFileName.text = entry.fileName.ifEmpty { "Unknown" }
        binding.tvDatetime.text = dateStr
        binding.tvSize.text = if (entry.sizeMb > 0) "${"%.2f".format(entry.sizeMb)} MB" else "Unknown"
        if (entry.width > 0 && entry.height > 0) {
            binding.tvDimensions.text = "${entry.width} × ${entry.height}"
        } else {
            binding.tvDimensions.text = ""
        }
    }

    private fun queryMediaStore(uriString: String): PhotoEntry {
        val uri = Uri.parse(uriString)
        val projection = arrayOf(
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATA
        )
        return try {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)) ?: ""
                    val dateTaken = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN))
                    val sizeBytes = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE))
                    val w = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH))
                    val h = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT))
                    val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)) ?: ""
                    PhotoEntry(uriString, name, path, dateTaken, "", "", sizeBytes / 1_048_576f, w, h)
                } else null
            } ?: fallbackEntry(uriString)
        } catch (e: Exception) { fallbackEntry(uriString) }
    }

    private fun fallbackEntry(uriString: String) = PhotoEntry(
        uri = uriString,
        fileName = Uri.parse(uriString).lastPathSegment ?: "Unknown",
        filePath = "", dateTaken = 0L, monthKey = "", dayKey = "", sizeMb = 0f, width = 0, height = 0
    )
}
