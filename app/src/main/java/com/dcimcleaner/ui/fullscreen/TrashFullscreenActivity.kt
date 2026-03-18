package com.dcimcleaner.ui.fullscreen

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.dcimcleaner.databinding.ActivityTrashFullscreenBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TrashFullscreenActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URIS = "extra_uris"
        const val EXTRA_POSITION = "extra_position"
    }

    private lateinit var binding: ActivityTrashFullscreenBinding
    private var uris = mutableListOf<String>()
    private lateinit var adapter: TrashFullscreenAdapter
    private var pendingDeleteUri: String? = null

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Photo permanently deleted", Toast.LENGTH_SHORT).show()
            val pos = binding.viewPager.currentItem
            uris.removeAt(pos)
            adapter.submitList(uris.toList())
            if (uris.isEmpty()) finish()
            else updateInfo()
        }
        pendingDeleteUri = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrashFullscreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        uris = intent.getStringArrayListExtra(EXTRA_URIS)?.toMutableList() ?: return
        val startPos = intent.getIntExtra(EXTRA_POSITION, 0)

        adapter = TrashFullscreenAdapter()
        adapter.submitList(uris.toList())
        binding.viewPager.adapter = adapter
        binding.viewPager.setCurrentItem(startPos, false)
        updateInfo()

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) { updateInfo() }
        })

        binding.btnDelete.setOnClickListener {
            val uri = uris.getOrNull(binding.viewPager.currentItem) ?: return@setOnClickListener
            confirmDelete(uri)
        }

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun confirmDelete(uriString: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete permanently?")
            .setMessage("This photo will be permanently deleted and cannot be recovered.")
            .setPositiveButton("Delete") { _, _ -> deletePhoto(uriString) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePhoto(uriString: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        lifecycleScope.launch {
            try {
                val uri = Uri.parse(uriString)
                val pi = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                pendingDeleteUri = uriString
                deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            } catch (e: Exception) {
                Toast.makeText(this@TrashFullscreenActivity, "Delete failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateInfo() {
        val pos = binding.viewPager.currentItem
        binding.tvPosition.text = "${pos + 1} / ${uris.size}"
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
                    finish()
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}
