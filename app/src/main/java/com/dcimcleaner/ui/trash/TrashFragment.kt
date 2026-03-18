package com.dcimcleaner.ui.trash

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.dcimcleaner.databinding.FragmentTrashBinding
import com.dcimcleaner.data.repository.TrashedPhoto
import com.dcimcleaner.data.repository.TrashRepository
import com.dcimcleaner.ui.fullscreen.TrashFullscreenActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrashFragment : Fragment() {

    private var _binding: FragmentTrashBinding? = null
    private val binding get() = _binding!!
    private lateinit var repo: TrashRepository
    private lateinit var adapter: TrashGridAdapter
    private var photos = listOf<TrashedPhoto>()

    private val emptyTrashLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(requireContext(), "Trash emptied successfully", Toast.LENGTH_SHORT).show()
            loadPhotos()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTrashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        repo = TrashRepository(requireContext())

        adapter = TrashGridAdapter { photo, position ->
            openFullscreen(position)
        }

        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recyclerView.adapter = adapter

        binding.btnEmptyTrash.setOnClickListener { confirmEmptyTrash() }

        loadPhotos()
    }

    override fun onResume() {
        super.onResume()
        loadPhotos()
    }

    private fun loadPhotos() {
        lifecycleScope.launch {
            photos = withContext(Dispatchers.IO) { repo.getTrashedPhotos() }
            adapter.submitList(photos)
            updateHeader()
        }
    }

    private fun updateHeader() {
        if (photos.isEmpty()) {
            binding.tvStats.text = ""
            binding.btnEmptyTrash.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            val totalMb = photos.sumOf { it.sizeMb.toDouble() }.toFloat()
            val sizeText = formatSize(totalMb)
            binding.tvStats.text = "${photos.size} photos · $sizeText"
            binding.btnEmptyTrash.visibility = View.VISIBLE
            binding.tvEmpty.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    private fun confirmEmptyTrash() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Empty Trash")
            .setMessage("Permanently delete all ${photos.size} trashed photos? This cannot be undone.")
            .setPositiveButton("Delete all") { _, _ -> emptyTrash() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun emptyTrash() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        lifecycleScope.launch {
            try {
                val uris = photos.map { it.uri }
                val pi = MediaStore.createDeleteRequest(requireContext().contentResolver, uris)
                emptyTrashLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to empty trash", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openFullscreen(startPosition: Int) {
        val uris = ArrayList(photos.map { it.uri.toString() })
        val intent = Intent(requireContext(), TrashFullscreenActivity::class.java).apply {
            putStringArrayListExtra(TrashFullscreenActivity.EXTRA_URIS, uris)
            putExtra(TrashFullscreenActivity.EXTRA_POSITION, startPosition)
        }
        startActivity(intent)
    }

    fun formatSize(mb: Float): String =
        if (mb >= 1024f) "${"%.1f".format(mb / 1024f)} GB" else "${"%.1f".format(mb)} MB"

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
