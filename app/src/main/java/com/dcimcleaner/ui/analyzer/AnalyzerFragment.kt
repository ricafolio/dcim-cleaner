package com.dcimcleaner.ui.analyzer

import android.graphics.Color
import android.os.Bundle
import android.view.*
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.dcimcleaner.R
import com.dcimcleaner.data.model.MonthStat
import com.dcimcleaner.data.model.DayStat
import com.dcimcleaner.databinding.FragmentAnalyzerBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.*

class AnalyzerFragment : Fragment() {

    private var _binding: FragmentAnalyzerBinding? = null
    private val binding get() = _binding!!
    private val vm: AnalyzerViewModel by viewModels()

    private var monthAdapter: StatsListAdapter? = null
    private var dayAdapter: StatsListAdapter? = null
    private var yearAdapter: StatsListAdapter? = null

    private val dfMonthIn = SimpleDateFormat("yyyy-MM", Locale.US)
    private val dfMonthOut = SimpleDateFormat("MMMM yyyy", Locale.US)
    private val dfDayIn = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dfDayOut = SimpleDateFormat("MMMM d, yyyy", Locale.US)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAnalyzerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupChart()
        setupTabs()
        setupSortHeaders()
        observeData()
    }

    private fun setupChart() {
        binding.barChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.labelRotationAngle = -45f
            axisRight.isEnabled = false
            // Dark mode safe colors
            val textColor = if (isDarkMode()) Color.WHITE else Color.BLACK
            xAxis.textColor = textColor
            axisLeft.textColor = textColor
            legend.textColor = textColor
        }
    }

    private fun isDarkMode(): Boolean {
        return (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("By Month"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("By Year"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("By Day"))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { showTab(tab.position) }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        showTab(0)
    }

    private fun setupSortHeaders() {
        binding.headerDate.setOnClickListener {
            getCurrentAdapter()?.sortBy(SortColumn.DATE)
            updateSortIndicators()
        }
        binding.headerCount.setOnClickListener {
            getCurrentAdapter()?.sortBy(SortColumn.COUNT)
            updateSortIndicators()
        }
        binding.headerSize.setOnClickListener {
            getCurrentAdapter()?.sortBy(SortColumn.SIZE)
            updateSortIndicators()
        }
    }

    private fun getCurrentAdapter() = when (binding.tabLayout.selectedTabPosition) {
        0 -> monthAdapter
        1 -> yearAdapter
        2 -> dayAdapter
        else -> null
    }

    private fun updateSortIndicators() {
        val (col, dir) = getCurrentAdapter()?.getCurrentSort() ?: return
        val arrow = if (dir == SortDir.ASC) " ↑" else " ↓"
        binding.headerDate.text = if (col == SortColumn.DATE) "Date$arrow" else "Date"
        binding.headerCount.text = if (col == SortColumn.COUNT) "Count$arrow" else "Count"
        binding.headerSize.text = if (col == SortColumn.SIZE) "Size$arrow" else "Size"
    }

    private fun showTab(pos: Int) {
        binding.headerDate.text = "Date"
        binding.headerCount.text = "Count"
        binding.headerSize.text = "Size"

        when (pos) {
            0 -> vm.monthStats.value?.let { updateMonthView(it) }
            1 -> vm.yearStats.value?.let { updateYearView(it) }
            2 -> {
                binding.barChart.visibility = View.GONE
                vm.dayStats.value?.let { updateDayView(it) }
            }
        }
    }

    private fun observeData() {
        vm.monthStats.observe(viewLifecycleOwner) { stats ->
            if (binding.tabLayout.selectedTabPosition == 0) updateMonthView(stats)
        }
        vm.yearStats.observe(viewLifecycleOwner) { stats ->
            if (binding.tabLayout.selectedTabPosition == 1) updateYearView(stats)
        }
        vm.dayStats.observe(viewLifecycleOwner) { stats ->
            if (binding.tabLayout.selectedTabPosition == 2) updateDayView(stats)
        }
    }

    private fun updateMonthView(stats: List<MonthStat>) {
        binding.barChart.visibility = View.VISIBLE
        val labels = stats.map { it.date }
        val entries = stats.mapIndexed { i, s -> BarEntry(i.toFloat(), s.fileCount.toFloat()) }
        updateBarChart(entries, labels)

        val rows = stats.map { s ->
            val displayDate = try { dfMonthOut.format(dfMonthIn.parse(s.date)!!) } catch (_: Exception) { s.date }
            StatsRow(s.date, displayDate, s.fileCount, s.totalSizeMb)
        }
        // Always recreate adapter and re-set on RecyclerView
        monthAdapter = StatsListAdapter(rows) { row -> navigateToImages("month", row.date) }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = monthAdapter
    }
    private fun updateYearView(stats: List<Pair<String, Float>>) {
        binding.barChart.visibility = View.VISIBLE
        val labels = stats.map { it.first }
        val entries = stats.mapIndexed { i, s -> BarEntry(i.toFloat(), s.second) }
        updateBarChart(entries, labels)

        val rows = stats.map { (year, count) ->
            val sizeMb = vm.monthStats.value
                ?.filter { it.date.startsWith(year) }
                ?.sumOf { it.totalSizeMb.toDouble() }?.toFloat() ?: 0f
            StatsRow(year, year, count.toInt(), sizeMb)
        }
        yearAdapter = StatsListAdapter(rows) { row -> navigateToImages("month", "${row.date}-01") }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = yearAdapter
    }

    private fun updateDayView(stats: List<DayStat>) {
        binding.barChart.visibility = View.GONE
        val rows = stats.map { s ->
            val displayDate = try { dfDayOut.format(dfDayIn.parse(s.date)!!) } catch (_: Exception) { s.date }
            StatsRow(s.date, displayDate, s.fileCount, s.totalSizeMb)
        }
        dayAdapter = StatsListAdapter(rows) { row -> navigateToImages("day", row.date) }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = dayAdapter
    }

    private fun navigateToImages(type: String, date: String) {
        val args = if (type == "month") {
            bundleOf("load_month" to date)
        } else {
            bundleOf("load_day" to date)
        }
        findNavController().navigate(R.id.nav_images, args)
    }

    private fun updateBarChart(entries: List<BarEntry>, labels: List<String>) {
        val textColor = if (isDarkMode()) Color.WHITE else Color.BLACK
        val dataSet = BarDataSet(entries, "Files").apply {
            color = requireContext().getColor(R.color.purple_500)
            valueTextColor = textColor
        }
        binding.barChart.apply {
            data = BarData(dataSet)
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.textColor = textColor
            axisLeft.textColor = textColor
            invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
