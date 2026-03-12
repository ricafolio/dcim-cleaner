package com.dcimcleaner.ui.images

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
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
                lifecycleScope.launch {
                    val uri = Uri.parse(entry.uri)
                    val values = android.content.ContentValues().apply {
                        put(MediaStore.Images.Media.IS_TRASHED, 1)
                    }
                    try {
                        val rows = requireContext().contentResolver.update(uri, values, null, null)
                        if (rows > 0) vm.removeFromIndex(entry.uri)
                    } catch (e: Exception) {
                        android.util.Log.e("TRASH", "Post-permission failed: ${e.message}")
                    }
                }
            }
        }
        pendingTrashEntry = null
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

        binding.btnTrash.setOnCheckedChangeListener { _, isChecked ->
            vm.trashModeEnabled.value = isChecked
        }


        vm.trashModeEnabled.observe(viewLifecycleOwner) { enabled ->
            if (binding.btnTrash.isChecked != enabled) {
                binding.btnTrash.isChecked = enabled
            }
        }

        vm.photos.observe(viewLifecycleOwner) { photos ->
            adapter.submitList(photos)
            val totalMb = photos.sumOf { it.sizeMb.toDouble() }
            binding.tvStats.text = "${photos.size} photos · ${"%.1f".format(totalMb)} MB"
        }

        vm.isCompactGrid.observe(viewLifecycleOwner) { compact ->
            updateGridLayout(compact)
            binding.btnGridToggle.setImageResource(
                if (compact) R.drawable.ic_grid_large else R.drawable.ic_grid_compact
            )
        }

        vm.currentDate.observe(viewLifecycleOwner) { date ->
            binding.tvDate.text = date
        }

        // Navigate from Analyzer — load specific date
        arguments?.getString("load_month")?.let { vm.loadByMonth(it) }
        arguments?.getString("load_day")?.let { vm.loadByDay(it) }
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
        startActivity(intent)
    }

    override fun onIndexComplete() {}

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
