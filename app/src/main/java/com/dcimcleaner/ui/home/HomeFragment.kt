package com.dcimcleaner.ui.home

import android.os.Bundle
import android.view.*
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.dcimcleaner.R
import com.dcimcleaner.data.repository.SessionPrefs
import com.dcimcleaner.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionPrefs

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        session = SessionPrefs(requireContext())

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
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
        refreshLastVisited()
    }

    private fun refreshStats() {
        val total = session.totalTrashedSizeMb
        val today = session.todayTrashedSizeMb
        val count = session.totalTrashedCount

        binding.tvTotalSaved.text = if (total >= 1024f) "${"%.1f".format(total / 1024f)} GB" else "${"%.1f".format(total)} MB"
        binding.tvTodaySaved.text = if (today >= 1024f) "${"%.1f".format(today / 1024f)} GB" else "${"%.1f".format(today)} MB"
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
