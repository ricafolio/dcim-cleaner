package com.dcimcleaner.ui.fullscreen

import android.app.Activity
import android.content.Intent
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

class TrashFullscreenActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URIS = "extra_uris"
        const val EXTRA_PATHS = "extra_paths"  // file paths parallel to URIs
        const val EXTRA_POSITION = "extra_position"
        const val RESULT_RESTORED_PATHS = "result_restored_paths"  // pass back paths, not URIs
    }

    private lateinit var binding: ActivityTrashFullscreenBinding
    private var uris = mutableListOf<String>()
    private var paths = mutableListOf<String>()  // parallel list of file paths
    private lateinit var adapter: TrashFullscreenAdapter
    private var pendingDeleteUri: String? = null
    private val restoredPaths = mutableListOf<String>()  // collect paths of restored photos

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Photo permanently deleted", Toast.LENGTH_SHORT).show()
            removeCurrentAndAdvance()
        }
        pendingDeleteUri = null
    }

    private val restoreLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Photo restored successfully", Toast.LENGTH_SHORT).show()
            val pos = binding.viewPager.currentItem
            val path = paths.getOrNull(pos)
            if (!path.isNullOrEmpty()) restoredPaths.add(path)
            removeCurrentAndAdvance()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrashFullscreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        uris = intent.getStringArrayListExtra(EXTRA_URIS)?.toMutableList() ?: return
        paths = intent.getStringArrayListExtra(EXTRA_PATHS)?.toMutableList()
            ?: MutableList(uris.size) { "" }
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

        binding.btnRestore.setOnClickListener {
            val uri = uris.getOrNull(binding.viewPager.currentItem) ?: return@setOnClickListener
            restorePhoto(uri)
        }

        binding.btnBack.setOnClickListener { finishWithResult() }
    }

    override fun onBackPressed() { finishWithResult() }

    private fun finishWithResult() {
        val data = Intent().apply {
            putStringArrayListExtra(RESULT_RESTORED_PATHS, ArrayList(restoredPaths))
        }
        setResult(Activity.RESULT_OK, data)
        finish()
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

    private fun restorePhoto(uriString: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        lifecycleScope.launch {
            try {
                val uri = Uri.parse(uriString)
                val pi = MediaStore.createTrashRequest(contentResolver, listOf(uri), false)
                restoreLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            } catch (e: Exception) {
                Toast.makeText(this@TrashFullscreenActivity, "Restore failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun removeCurrentAndAdvance() {
        val pos = binding.viewPager.currentItem
        if (pos < uris.size) {
            uris.removeAt(pos)
            if (pos < paths.size) paths.removeAt(pos)
            adapter.submitList(uris.toList())
            if (uris.isEmpty()) finishWithResult()
            else updateInfo()
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
            android.view.MotionEvent.ACTION_DOWN -> { touchStartY = ev.y; touchStartX = ev.x }
            android.view.MotionEvent.ACTION_UP -> {
                val deltaY = ev.y - touchStartY
                val deltaX = kotlin.math.abs(ev.x - touchStartX)
                if (deltaY > 200 && deltaX < deltaY * 0.5f) {
                    finishWithResult(); return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}
