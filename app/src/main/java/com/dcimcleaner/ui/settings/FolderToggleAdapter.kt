package com.dcimcleaner.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dcimcleaner.databinding.ItemFolderToggleBinding

data class FolderItem(val name: String, var ignored: Boolean)

class FolderToggleAdapter(
    private val items: List<FolderItem>,
    private val onToggle: (folderName: String, ignored: Boolean) -> Unit
) : RecyclerView.Adapter<FolderToggleAdapter.VH>() {

    inner class VH(val b: ItemFolderToggleBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemFolderToggleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.b.tvFolderName.text = item.name
        holder.b.switchIgnore.isChecked = item.ignored
        holder.b.switchIgnore.setOnCheckedChangeListener { _, isChecked ->
            item.ignored = isChecked
            onToggle(item.name, isChecked)
        }
        holder.b.root.setOnClickListener {
            holder.b.switchIgnore.toggle()
        }
    }

    override fun getItemCount() = items.size
}
