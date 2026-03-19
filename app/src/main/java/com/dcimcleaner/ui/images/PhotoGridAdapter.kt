package com.dcimcleaner.ui.images

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.dcimcleaner.R
import com.dcimcleaner.data.model.PhotoEntry
import com.dcimcleaner.databinding.ItemPhotoBinding

class PhotoGridAdapter(
    private val onPhotoClick: (PhotoEntry, Int) -> Unit,
    private val onPhotoLongClick: (PhotoEntry) -> Unit
) : ListAdapter<PhotoEntry, PhotoGridAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<PhotoEntry>() {
            override fun areItemsTheSame(a: PhotoEntry, b: PhotoEntry) = a.uri == b.uri
            override fun areContentsTheSame(a: PhotoEntry, b: PhotoEntry) = a == b
        }
    }

    // Track current span so we can resize on toggle
    private var currentSpanCount = 3

    inner class VH(val binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = getItem(position)

        // Always force square based on current span — fixes aspect ratio after grid toggle
        val spanCount = currentSpanCount
        val screenWidth = holder.binding.root.context.resources.displayMetrics.widthPixels
        val cellSize = screenWidth / spanCount
        holder.binding.root.layoutParams = ViewGroup.LayoutParams(cellSize, cellSize)

        Glide.with(holder.binding.image.context)
            .load(Uri.parse(entry.uri))
            .override(300, 300)
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .placeholder(R.drawable.ic_photo_placeholder)
            .into(holder.binding.image)

        holder.binding.tvSize.text = if (entry.sizeMb >= 1f) {
            "${"%.1f".format(entry.sizeMb)}MB"
        } else {
            "${(entry.sizeMb * 1024).toInt()}KB"
        }

        holder.binding.root.setOnClickListener { onPhotoClick(entry, holder.bindingAdapterPosition) }
        holder.binding.root.setOnLongClickListener { onPhotoLongClick(entry); true }
    }

    fun updateSpanCount(spanCount: Int) {
        currentSpanCount = spanCount
        notifyItemRangeChanged(0, itemCount)
    }
}
