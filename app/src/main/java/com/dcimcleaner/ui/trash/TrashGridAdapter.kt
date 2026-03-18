package com.dcimcleaner.ui.trash

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.dcimcleaner.R
import com.dcimcleaner.data.repository.TrashedPhoto
import com.dcimcleaner.databinding.ItemTrashPhotoBinding

class TrashGridAdapter(
    private val onClick: (TrashedPhoto, Int) -> Unit
) : ListAdapter<TrashedPhoto, TrashGridAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<TrashedPhoto>() {
            override fun areItemsTheSame(a: TrashedPhoto, b: TrashedPhoto) = a.uri == b.uri
            override fun areContentsTheSame(a: TrashedPhoto, b: TrashedPhoto) = a == b
        }
    }

    inner class VH(val b: ItemTrashPhotoBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemTrashPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val size = parent.width / 3
        b.root.layoutParams = ViewGroup.LayoutParams(size, size)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val photo = getItem(position)

        Glide.with(holder.b.root.context)
            .load(photo.uri)
            .override(300)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .placeholder(R.drawable.ic_photo_placeholder)
            .into(holder.b.ivPhoto)

        holder.b.tvSize.text = if (photo.sizeMb >= 1024f)
            "${"%.1f".format(photo.sizeMb / 1024f)} GB"
        else "${"%.1f".format(photo.sizeMb)} MB"

        holder.b.root.setOnClickListener { onClick(photo, position) }
    }
}
