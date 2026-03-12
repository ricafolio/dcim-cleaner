package com.dcimcleaner.ui.images

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
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

    inner class VH(val binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        val spanCount = (parent as? RecyclerView)?.let {
            (it.layoutManager as? GridLayoutManager)?.spanCount
        } ?: 3
        val size = parent.width.takeIf { it > 0 }
            ?: parent.context.resources.displayMetrics.widthPixels
        val cellSize = size / spanCount
        binding.root.layoutParams = ViewGroup.LayoutParams(cellSize, cellSize)

        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = getItem(position)

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
}