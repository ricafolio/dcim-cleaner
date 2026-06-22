package com.dcimcleaner.ui.filters

import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.dcimcleaner.data.repository.PhotoRepository
import com.dcimcleaner.data.repository.SessionPrefs
import com.dcimcleaner.databinding.FragmentFilterSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class FilterSettingsFragment : Fragment() {

    private var _binding: FragmentFilterSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionPrefs
    private lateinit var repo: PhotoRepository

    private var years: List<String> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFilterSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        session = SessionPrefs(requireContext())
        repo = PhotoRepository(requireContext())

        setupYearFilter()
        setupToggles()
        setupMinPhotos()
        setupResetButton()
    }

    private fun setupYearFilter() {
        lifecycleScope.launch {
            years = repo.getAvailableYears()
            val options = listOf("All years") + years
            binding.dropdownYear.setAdapter(
                ArrayAdapter(requireContext(), com.dcimcleaner.R.layout.item_dropdown, options)
            )

            val currentYear = session.filterYear
            val displayValue = if (currentYear.isEmpty()) "All years" else currentYear
            binding.dropdownYear.setText(displayValue, false)

            binding.dropdownYear.setOnItemClickListener { _, _, position, _ ->
                val selected = options[position]
                session.filterYear = if (selected == "All years") "" else selected
                refreshAppliedLabel()
            }
        }
    }

    private fun setupToggles() {
        binding.switchNoRepeatDays.isChecked = session.filterNoRepeatDays
        binding.switchNoRepeatDays.setOnCheckedChangeListener { _, isChecked ->
            session.filterNoRepeatDays = isChecked
            refreshAppliedLabel()
        }

        binding.switchNoRepeatMonths.isChecked = session.filterNoRepeatMonths
        binding.switchNoRepeatMonths.setOnCheckedChangeListener { _, isChecked ->
            session.filterNoRepeatMonths = isChecked
            refreshAppliedLabel()
        }
    }

    private fun setupMinPhotos() {
        val options = listOf("No minimum", "5+ photos", "10+ photos", "20+ photos", "50+ photos")
        val values = listOf(0, 5, 10, 20, 50)

        binding.dropdownMinPhotos.setAdapter(
            ArrayAdapter(requireContext(), com.dcimcleaner.R.layout.item_dropdown, options)
        )

        val currentMin = session.filterMinPhotos
        val currentIndex = values.indexOf(currentMin).takeIf { it >= 0 } ?: 0
        binding.dropdownMinPhotos.setText(options[currentIndex], false)

        binding.dropdownMinPhotos.setOnItemClickListener { _, _, position, _ ->
            session.filterMinPhotos = values[position]
            refreshAppliedLabel()
        }
    }

    private fun setupResetButton() {
        binding.btnResetFilters.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Reset filters?")
                .setMessage("This clears the year, repeat, and minimum photo filters.")
                .setPositiveButton("Reset") { _, _ ->
                    session.filterYear = ""
                    session.filterNoRepeatDays = false
                    session.filterNoRepeatMonths = false
                    session.filterMinPhotos = 0
                    binding.dropdownYear.setText("All years", false)
                    binding.switchNoRepeatDays.isChecked = false
                    binding.switchNoRepeatMonths.isChecked = false
                    binding.dropdownMinPhotos.setText("No minimum", false)
                    refreshAppliedLabel()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun refreshAppliedLabel() {
        val count = session.activeFilterCount()
        binding.tvAppliedSummary.text = if (count == 0) "No filters applied"
            else "$count filter${if (count > 1) "s" else ""} applied"
    }

    override fun onResume() {
        super.onResume()
        refreshAppliedLabel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
