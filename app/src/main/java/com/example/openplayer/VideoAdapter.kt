package com.example.openplayer

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.util.Locale

class VideoAdapter(
    private val onClick: (Video) -> Unit
) : RecyclerView.Adapter<VideoAdapter.Holder>() {

    private var items: List<Video> = emptyList()

    /** false = lista, true = grade */
    var grid = false

    fun submit(list: List<Video>) {
        items = list
        notifyDataSetChanged()
    }

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.thumb)
        val duration: TextView = v.findViewById(R.id.duration)
        val title: TextView = v.findViewById(R.id.title)
        val info: TextView = v.findViewById(R.id.info)
    }

    override fun getItemViewType(position: Int): Int = if (grid) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val layout = if (viewType == 1) R.layout.item_video_grid else R.layout.item_video
        return Holder(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val v = items[position]
        holder.title.text = v.name
        val size = Formatter.formatShortFileSize(holder.itemView.context, v.sizeBytes)
        holder.info.text = if (grid) size else "$size  •  ${v.folderName}"
        holder.duration.text = formatDuration(v.durationMs)
        Glide.with(holder.itemView).load(v.uri).centerCrop().into(holder.thumb)
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
