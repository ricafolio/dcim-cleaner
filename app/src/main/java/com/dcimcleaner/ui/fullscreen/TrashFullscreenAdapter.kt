package com.dcimcleaner.ui.fullscreen

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.dcimcleaner.databinding.ItemTrashFullscreenBinding

class TrashFullscreenAdapter : ListAdapter<String, TrashFullscreenAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(a: String, b: String) = a == b
            override fun areContentsTheSame(a: String, b: String) = a == b
        }
    }

    inner class VH(val b: ItemTrashFullscreenBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemTrashFullscreenBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val uri = Uri.parse(getItem(position))
        Glide.with(holder.b.root.context)
            .load(uri)
            .into(holder.b.ivPhoto)
    }
}
