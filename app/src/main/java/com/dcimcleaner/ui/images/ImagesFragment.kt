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
import com.dcimcleaner.ui.fullscreen.TrashFullscreenActivity
import kotlinx.coroutines.launch
import java.util.LinkedList

class ImagesFragment : Fragment(), IndexCompleteListener {

    private var _binding: FragmentImagesBinding? = null
    private val binding get() = _binding!!
    private val vm: ImagesViewModel by viewModels()
    private lateinit var adapter: PhotoGridAdapter
    private var gridToast: Toast? = null
    private var trashToast: Toast? = null
    private var gridToggleInitialized = false

    // Queue for entries waiting to be trashed while a system dialog is open
    private var pendingTrashEntry: PhotoEntry? = null
    private val trashQueue = LinkedList<PhotoEntry>()
    private var isTrashDialogActive = false

    private val trashLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        isTrashDialogActive = false
        if (result.resultCode == Activity.RESULT_OK) {
            pendingTrashEntry?.let { entry ->
                vm.recordTrashAndRemove(entry)
                showTrashToast("Moved to trash: ${entry.fileName}")
            }
        } else {
            // User denied — put it back so queue can continue
            pendingTrashEntry?.let { entry ->
                // Re-add to photos since we removed optimistically
                // reloadCurrentDate will restore it
                vm.reloadCurrentDate()
            }
        }
        pendingTrashEntry = null
        // Process next in queue
        processTrashQueue()
    }

    private val fullscreenLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val trashedUris = result.data?.getStringArrayListExtra(FullscreenActivity.RESULT_TRASHED_URIS)
            val trashedSizes = result.data?.getFloatArrayExtra(FullscreenActivity.RESULT_TRASHED_SIZES)
            trashedUris?.forEachIndexed { i, uri ->
                vm.session.addTrashed(trashedSizes?.getOrNull(i) ?: 0f)
                vm.removeFromIndex(uri)
            }
            val restoredPaths = result.data?.getStringArrayListExtra(TrashFullscreenActivity.RESULT_RESTORED_PATHS)
            // if (!restoredPaths.isNullOrEmpty()) vm.reloadAfterRestore(restoredPaths)
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
        val initialSpan = vm.spanCount.value ?: 3
        updateGridLayout(initialSpan)

        binding.btnRandomMonth.setOnClickListener { vm.pickRandomMonth() }
        binding.btnRandomDay.setOnClickListener { vm.pickRandomDay() }
        binding.btnGridToggle.setOnClickListener { vm.toggleGrid() }

        binding.btnTrashContainer.setOnClickListener {
            binding.btnTrash.isChecked = !binding.btnTrash.isChecked
        }

        binding.btnTrash.setOnCheckedChangeListener { _, isChecked ->
            if (vm.trashModeEnabled.value != isChecked) {
                vm.trashModeEnabled.value = isChecked
                if (isChecked) Toast.makeText(requireContext(),
                    "Quick trash enabled — tap a photo to trash instantly", Toast.LENGTH_SHORT).show()
            }
        }

        vm.trashModeEnabled.observe(viewLifecycleOwner) { enabled ->
            if (binding.btnTrash.isChecked != enabled) binding.btnTrash.isChecked = enabled
        }

        vm.photos.observe(viewLifecycleOwner) { photos ->
            // Completion callback fires only once the DiffUtil calculation actually finishes —
            // this is what lets us safely re-enable the random buttons without racing the diff.
            adapter.submitList(photos) {
                vm.onGridUpdateComplete()
                binding.recyclerView.scrollToPosition(0)
            }
            val totalMb = photos.sumOf { it.sizeMb.toDouble() }.toFloat()
            val sizeText = if (totalMb >= 1024f) "${"%.1f".format(totalMb / 1024f)} GB"
                           else "${"%.1f".format(totalMb)} MB"
            binding.tvStats.text = "${photos.size} photos · $sizeText"
        }

        vm.isLoadingRandom.observe(viewLifecycleOwner) { loading ->
            // Block new random taps until the current pick has fully rendered —
            // prevents rapid taps from cancelling each other's DiffUtil calculation
            binding.btnRandomMonth.isEnabled = !loading
            binding.btnRandomDay.isEnabled = !loading
        }

        vm.spanCount.observe(viewLifecycleOwner) { span ->
            // Update layout and adapter
            updateGridLayout(span)
            adapter.updateSpanCount(span)

            // Update Icon based on state
            binding.ivGridIcon.setImageResource(
                when(span) {
                    2 -> R.drawable.ic_grid_large    // You may need to add/choose icons
                    3 -> R.drawable.ic_grid_compact
                    else -> R.drawable.ic_grid_compact
                }
            )

            if (gridToggleInitialized) {
                val label = "$span-column grid"
                gridToast?.cancel()
                gridToast = Toast.makeText(requireContext(), label, Toast.LENGTH_SHORT)
                gridToast?.show()
            }
            gridToggleInitialized = true
        }

        vm.currentDate.observe(viewLifecycleOwner) { date -> binding.tvDate.text = date }

        vm.noEligibleDate.observe(viewLifecycleOwner) { triggered ->
            if (triggered == true) {
                Toast.makeText(
                    requireContext(),
                    "No dates match your filters — try adjusting them in Filter Settings",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        vm.cycleRestarted.observe(viewLifecycleOwner) { type ->
            if (type != null) {
                val label = if (type == "month") "months" else "days"
                Toast.makeText(
                    requireContext(),
                    "You've seen all $label — starting over",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        if (vm.photos.value.isNullOrEmpty()) {
            arguments?.getString("pick_random")?.let { type ->
                if (type == "month") vm.pickRandomMonth() else vm.pickRandomDay()
            }
            arguments?.getString("load_month")?.let { vm.loadByMonth(it) }
            arguments?.getString("load_day")?.let { vm.loadByDay(it) }
        }
    }

    private fun updateGridLayout(span: Int) {
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), span)
    }

    private fun handleTrash(entry: PhotoEntry) {
        // Optimistically remove from grid immediately for responsiveness
        vm.removeFromListOptimistic(entry.uri)

        if (isTrashDialogActive) {
            // Dialog is open — queue for after current dialog resolves
            trashQueue.add(entry)
        } else {
            trashEntry(entry)
        }
    }

    private fun trashEntry(entry: PhotoEntry) {
        vm.trashPhoto(
            entry,
            onNeedsIntent = { intentSender ->
                isTrashDialogActive = true
                pendingTrashEntry = entry
                trashLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            },
            onDone = {
                // Success path (manage media) — already removed optimistically, just update stats
                showTrashToast("Moved to trash: ${entry.fileName}")
                processTrashQueue()
            }
        )
    }

    private fun processTrashQueue() {
        val next = trashQueue.poll() ?: return
        trashEntry(next)
    }

    private fun showTrashToast(message: String) {
        trashToast?.cancel()
        trashToast = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT)
        trashToast?.show()
    }

    private fun openFullscreen(startPosition: Int) {
        val photos = vm.photos.value ?: return
        val intent = Intent(requireContext(), FullscreenActivity::class.java).apply {
            putStringArrayListExtra(FullscreenActivity.EXTRA_URIS, ArrayList(photos.map { it.uri }))
            putExtra(FullscreenActivity.EXTRA_POSITION, startPosition)
        }
        fullscreenLauncher.launch(intent)
    }

    override fun onResume() {
        super.onResume()
        if (!vm.photos.value.isNullOrEmpty()) vm.reloadCurrentDate()
    }

    override fun onIndexComplete() {}

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
