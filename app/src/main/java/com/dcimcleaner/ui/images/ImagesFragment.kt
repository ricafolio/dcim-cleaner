package com.dcimcleaner.ui.images

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.dcimcleaner.IndexCompleteListener
import com.dcimcleaner.R
import com.dcimcleaner.data.model.PhotoEntry
import com.dcimcleaner.databinding.FragmentImagesBinding
import com.dcimcleaner.ui.fullscreen.FullscreenActivity
import kotlinx.coroutines.launch

class ImagesFragment : Fragment(), IndexCompleteListener {

    private var _binding: FragmentImagesBinding? = null
    private val binding get() = _binding!!
    private val vm: ImagesViewModel by viewModels()
    private lateinit var adapter: PhotoGridAdapter

    private var pendingTrashEntry: PhotoEntry? = null

    private val trashLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
                        ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingTrashEntry?.let { entry ->
                vm.recordTrashAndRemove(entry)
            }
        }
        pendingTrashEntry = null
    }

    // Result from FullscreenActivity — it tells us which URIs were trashed
    private val fullscreenLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val trashedUris = result.data?.getStringArrayListExtra(FullscreenActivity.RESULT_TRASHED_URIS)
            val trashedSizes = result.data?.getFloatArrayExtra(FullscreenActivity.RESULT_TRASHED_SIZES)
            trashedUris?.forEachIndexed { i, uri ->
                val sizeMb = trashedSizes?.getOrNull(i) ?: 0f
                vm.session.addTrashed(sizeMb)
                vm.removeFromIndex(uri)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentImagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = PhotoGridAdapter(
            onPhotoClick = { entry, pos ->
                if (vm.trashModeEnabled.value == true) handleTrash(entry)
                else openFullscreen(pos)
            },
            onPhotoLongClick = { entry -> handleTrash(entry) }
        )

        binding.recyclerView.adapter = adapter
        updateGridLayout(isCompact = false)

        binding.btnRandomMonth.setOnClickListener { vm.pickRandomMonth() }
        binding.btnRandomDay.setOnClickListener { vm.pickRandomDay() }
        binding.btnGridToggle.setOnClickListener { vm.toggleGrid() }
        binding.btnHome.setOnClickListener { findNavController().navigate(R.id.nav_home) }

        binding.btnTrashContainer.setOnClickListener {
            binding.btnTrash.isChecked = !binding.btnTrash.isChecked
        }

        binding.btnTrash.setOnCheckedChangeListener { _, isChecked ->
            if (vm.trashModeEnabled.value != isChecked) {
                vm.trashModeEnabled.value = isChecked
                if (isChecked) {
                    Toast.makeText(requireContext(), "Quick trash enabled: Tap a photo in grid to trash instantly", Toast.LENGTH_SHORT).show()
                }
            }
        }

        vm.trashModeEnabled.observe(viewLifecycleOwner) { enabled ->
            if (binding.btnTrash.isChecked != enabled) binding.btnTrash.isChecked = enabled
        }

        vm.photos.observe(viewLifecycleOwner) { photos ->
            adapter.submitList(photos)
            val totalMb = photos.sumOf { it.sizeMb.toDouble() }
            binding.tvStats.text = "${photos.size} photos · ${"%.1f".format(totalMb)} MB"
        }

        vm.isCompactGrid.observe(viewLifecycleOwner) { compact ->
            updateGridLayout(compact)
            binding.ivGridIcon.setImageResource(
                if (compact) R.drawable.ic_grid_large else R.drawable.ic_grid_compact
            )
        }

        vm.currentDate.observe(viewLifecycleOwner) { date ->
            binding.tvDate.text = date
        }

        // Handle arguments — from Analyzer or Home
        if (vm.photos.value.isNullOrEmpty()) {
            arguments?.getString("pick_random")?.let { type ->
                if (type == "month") vm.pickRandomMonth() else vm.pickRandomDay()
            }
            arguments?.getString("load_month")?.let { vm.loadByMonth(it) }
            arguments?.getString("load_day")?.let { vm.loadByDay(it) }
        }
    }

    private fun updateGridLayout(isCompact: Boolean) {
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), if (isCompact) 5 else 3)
    }

    private fun handleTrash(entry: PhotoEntry) {
        vm.trashPhoto(
            entry,
            onNeedsIntent = { intentSender ->
                pendingTrashEntry = entry
                trashLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            },
            onDone = {}
        )
    }

    private fun openFullscreen(startPosition: Int) {
        val photos = vm.photos.value ?: return
        val intent = Intent(requireContext(), FullscreenActivity::class.java).apply {
            putStringArrayListExtra(FullscreenActivity.EXTRA_URIS, ArrayList(photos.map { it.uri }))
            putExtra(FullscreenActivity.EXTRA_POSITION, startPosition)
        }
        fullscreenLauncher.launch(intent)
    }

    override fun onIndexComplete() {}

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
