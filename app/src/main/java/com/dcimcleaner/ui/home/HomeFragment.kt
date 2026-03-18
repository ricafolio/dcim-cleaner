package com.dcimcleaner.ui.home

import android.os.Bundle
import android.view.*
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.dcimcleaner.R
import com.dcimcleaner.data.repository.SessionPrefs
import com.dcimcleaner.data.repository.TrashRepository
import com.dcimcleaner.databinding.FragmentHomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionPrefs
    private lateinit var trashRepo: TrashRepository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        session = SessionPrefs(requireContext())
        trashRepo = TrashRepository(requireContext())

        binding.btnRandomMonth.setOnClickListener {
            findNavController().navigate(R.id.nav_images, bundleOf("pick_random" to "month"))
        }
        binding.btnRandomDay.setOnClickListener {
            findNavController().navigate(R.id.nav_images, bundleOf("pick_random" to "day"))
        }
        binding.btnLastVisited.setOnClickListener {
            val date = session.lastVisitedDate
            val type = session.lastVisitedType
            if (date.isNotEmpty()) {
                val key = if (type == "month") "load_month" else "load_day"
                findNavController().navigate(R.id.nav_images, bundleOf(key to date))
            }
        }
        binding.btnViewTrash.setOnClickListener {
            findNavController().navigate(R.id.nav_trash)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
        refreshLastVisited()
        refreshTrashButton()
    }

    private fun refreshStats() {
        val total = session.totalTrashedSizeMb
        val today = session.todayTrashedSizeMb
        val count = session.totalTrashedCount
        binding.tvTotalSaved.text = formatSize(total)
        binding.tvTodaySaved.text = formatSize(today)
        binding.tvTrashedCount.text = "$count"
    }

    private fun refreshLastVisited() {
        val lastDate = session.lastVisitedDate
        if (lastDate.isEmpty()) {
            binding.btnLastVisited.visibility = View.GONE
            binding.tvLastVisitedLabel.visibility = View.GONE
        } else {
            binding.btnLastVisited.visibility = View.VISIBLE
            binding.tvLastVisitedLabel.visibility = View.VISIBLE
            binding.btnLastVisited.text = "Continue: $lastDate"
        }
    }

    private fun refreshTrashButton() {
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) { trashRepo.getTrashedCount() }
            val sizeMb = withContext(Dispatchers.IO) { trashRepo.getTrashedSizeMb() }
            if (count == 0) {
                binding.llTrashContainer.visibility = View.GONE
            } else {
                binding.llTrashContainer.visibility = View.VISIBLE
                binding.btnViewTrash.text = "You have ${formatSize(sizeMb)} photos to delete"
            }
        }
    }

    private fun formatSize(mb: Float): String =
        if (mb >= 1024f) "${"%.1f".format(mb / 1024f)} GB" else "${"%.1f".format(mb)} MB"

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
