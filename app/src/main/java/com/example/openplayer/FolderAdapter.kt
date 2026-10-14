package com.example.openplayer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.openplayer.databinding.ItemFolderBinding

class FolderAdapter(
    private val onClick: (Folder) -> Unit
) : RecyclerView.Adapter<FolderAdapter.Holder>() {

    private var items: List<Folder> = emptyList()

    fun submit(list: List<Folder>) {
        items = list
        notifyDataSetChanged()
    }

    class Holder(val b: ItemFolderBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val f = items[position]
        holder.b.name.text = f.name
        holder.b.count.text =
            holder.itemView.resources.getQuantityString(R.plurals.video_count, f.count, f.count)
        holder.itemView.setOnClickListener { onClick(f) }
    }
}
