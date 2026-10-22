package com.josercha.openplayer

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.util.Locale

class VideoAdapter(
    private val onClick: (Video) -> Unit,
    private val onLongClick: (Video) -> Unit = {},
    private val onMoreOptionsClick: (Video, View) -> Unit = { _, _ -> },
    private val onSelectionChanged: () -> Unit = {}
) : RecyclerView.Adapter<VideoAdapter.Holder>() {

    private var items: List<Video> = emptyList()
    var grid = false

    // Modo de seleção (toque longo num vídeo liga; marcar vários e excluir de uma vez)
    var isSelectionMode = false
        private set
    val selectedItems = mutableSetOf<Video>()

    val isAllSelected: Boolean
        get() = items.isNotEmpty() && selectedItems.size == items.size

    fun selectedVideos(): List<Video> = items.filter { it in selectedItems }

    fun submit(list: List<Video>) {
        items = list
        selectedItems.clear()
        isSelectionMode = false
        notifyDataSetChanged()
        onSelectionChanged()
    }

    fun toggleSelection(video: Video) {
        val wasMode = isSelectionMode
        if (!selectedItems.remove(video)) selectedItems.add(video)
        isSelectionMode = selectedItems.isNotEmpty()
        if (wasMode != isSelectionMode) {
            notifyDataSetChanged() // entrou/saiu do modo: todos mudam (caixinhas aparecem/somem)
        } else {
            val idx = items.indexOf(video)
            if (idx >= 0) notifyItemChanged(idx)
        }
        onSelectionChanged()
    }

    fun selectAll() {
        selectedItems.addAll(items)
        isSelectionMode = selectedItems.isNotEmpty()
        notifyDataSetChanged()
        onSelectionChanged()
    }

    fun clearSelection() {
        selectedItems.clear()
        isSelectionMode = false
        notifyDataSetChanged()
        onSelectionChanged()
    }

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        // Views do modo Lista
        var thumb: ImageView? = v.findViewById(R.id.thumb)
        var duration: TextView? = v.findViewById(R.id.duration)
        var title: TextView? = v.findViewById(R.id.title)
        var info: TextView? = v.findViewById(R.id.info)
        
        // Views do modo Grade (podem ser nulas se o layout for lista)
        var check: CheckBox? = v.findViewById(R.id.check)
        var btnMoreOptions: ImageButton? = v.findViewById(R.id.btnMoreOptions)
    }

    override fun getItemViewType(position: Int): Int = if (grid) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val layout = if (viewType == 1) R.layout.item_video_grid else R.layout.item_video
        return Holder(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val v = items[position]
        val size = Formatter.formatShortFileSize(holder.itemView.context, v.sizeBytes)
        
        holder.title?.text = v.name
        holder.info?.text = if (grid) size else "$size  •  ${v.folderName}"
        holder.duration?.text = formatDuration(v.durationMs)
        
        holder.thumb?.let { Glide.with(holder.itemView).load(v.uri).centerCrop().into(it) }

        // Lógica de Seleção (apenas se o layout tiver os botões)
        val isSelected = selectedItems.contains(v)
        
        holder.check?.let {
            it.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            it.isChecked = isSelected
        }

        // Cliques
        holder.itemView.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(v)
            } else {
                onClick(v)
            }
        }

        holder.itemView.setOnLongClickListener {
            if (!isSelectionMode) {
                toggleSelection(v)
                onLongClick(v)
            }
            true
        }

        // No modo de seleção a caixinha ocupa o lugar dos 3 pontinhos
        holder.btnMoreOptions?.visibility = if (isSelectionMode) View.GONE else View.VISIBLE

        // Botão de 3 pontinhos (se existir no layout)
        holder.btnMoreOptions?.setOnClickListener { view ->
            onMoreOptionsClick(v, view)
        }
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