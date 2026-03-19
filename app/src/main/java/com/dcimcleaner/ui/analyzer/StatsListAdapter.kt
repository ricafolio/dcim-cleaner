package com.dcimcleaner.ui.analyzer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.dcimcleaner.R
import com.dcimcleaner.databinding.ItemStatsRowBinding

data class StatsRow(val date: String, val displayDate: String, val count: Int, val sizeMb: Float)

enum class SortColumn { DATE, COUNT, SIZE }
enum class SortDir { ASC, DESC }

class StatsListAdapter(
    private var rows: List<StatsRow>,
    private val onRowClick: (StatsRow) -> Unit
) : RecyclerView.Adapter<StatsListAdapter.VH>() {

    private var sortColumn = SortColumn.DATE
    private var sortDir = SortDir.DESC
    private var displayRows = rows.toMutableList()

    init { applySort() }

    inner class VH(val b: ItemStatsRowBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemStatsRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = displayRows[position]
        holder.b.tvDate.text = row.displayDate
        holder.b.tvCount.text = "${row.count} files"
        holder.b.tvSize.text = if (row.sizeMb >= 1024f)
            "${"%.1f".format(row.sizeMb / 1024f)} GB"
        else
            "${"%.1f".format(row.sizeMb)} MB"
        holder.b.root.setOnClickListener { onRowClick(row) }
    }

    override fun getItemCount() = displayRows.size

    fun updateData(newRows: List<StatsRow>) {
        rows = newRows
        applySort()
    }

    fun sortBy(col: SortColumn) {
        if (sortColumn == col) {
            sortDir = if (sortDir == SortDir.ASC) SortDir.DESC else SortDir.ASC
        } else {
            sortColumn = col
            sortDir = SortDir.DESC
        }
        applySort()
    }

    fun getCurrentSort() = Pair(sortColumn, sortDir)

    private fun applySort() {
        displayRows = when (sortColumn) {
            SortColumn.DATE -> if (sortDir == SortDir.ASC) rows.sortedBy { it.date } else rows.sortedByDescending { it.date }
            SortColumn.COUNT -> if (sortDir == SortDir.ASC) rows.sortedBy { it.count } else rows.sortedByDescending { it.count }
            SortColumn.SIZE -> if (sortDir == SortDir.ASC) rows.sortedBy { it.sizeMb } else rows.sortedByDescending { it.sizeMb }
        }.toMutableList()
        notifyDataSetChanged()
    }
}
