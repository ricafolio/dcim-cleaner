package com.dcimcleaner.ui.fullscreen

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.dcimcleaner.data.model.PhotoEntry
import com.dcimcleaner.databinding.ItemFullscreenPhotoBinding

class FullscreenAdapter(
    private var photos: List<PhotoEntry>,
    private val onPhotoVisible: (PhotoEntry) -> Unit
) : RecyclerView.Adapter<FullscreenAdapter.VH>() {

    inner class VH(val binding: ItemFullscreenPhotoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemFullscreenPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = photos[position]
        Glide.with(holder.binding.image.context)
            .load(Uri.parse(entry.uri))
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .into(holder.binding.image)
        onPhotoVisible(entry)
    }

    override fun getItemCount() = photos.size

    fun updatePhotos(newPhotos: List<PhotoEntry>) {
        photos = newPhotos
        notifyDataSetChanged()
    }
}
