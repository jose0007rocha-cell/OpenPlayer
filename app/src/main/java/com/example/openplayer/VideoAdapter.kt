package com.example.openplayer

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.openplayer.databinding.ItemVideoBinding
import java.util.Locale

class VideoAdapter(
    private val onClick: (Video) -> Unit
) : RecyclerView.Adapter<VideoAdapter.Holder>() {

    private var items: List<Video> = emptyList()

    fun submit(list: List<Video>) {
        items = list
        notifyDataSetChanged()
    }

    class Holder(val b: ItemVideoBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val v = items[position]
        holder.b.title.text = v.name
        val size = Formatter.formatShortFileSize(holder.itemView.context, v.sizeBytes)
        holder.b.info.text = "${formatDuration(v.durationMs)}  •  $size"
        holder.itemView.setOnClickListener { onClick(v) }
    }

    private fun formatDuration(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
        else String.format(Locale.getDefault(), "%d:%02d", m, s)
    }
}
