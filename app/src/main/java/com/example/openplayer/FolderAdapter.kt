package com.example.openplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.openplayer.databinding.ItemFolderBinding

class FolderAdapter(
    private val onOpen: (Folder) -> Unit,
    private val onLongPress: (Folder) -> Unit,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<FolderAdapter.Holder>() {

    private var items: List<Folder> = emptyList()
    private var hidden: Set<Long> = emptySet()

    var selecting = false
        private set
    val selected = LinkedHashSet<Long>()

    fun submit(list: List<Folder>, hiddenIds: Set<Long>) {
        items = list
        hidden = hiddenIds
        notifyDataSetChanged()
    }

    fun startSelecting(initial: Long?) {
        selecting = true
        selected.clear()
        if (initial != null) selected.add(initial)
        notifyDataSetChanged()
        onSelectionChanged()
    }

    fun stopSelecting() {
        selecting = false
        selected.clear()
        notifyDataSetChanged()
        onSelectionChanged()
    }

    class Holder(val b: ItemFolderBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val f = items[position]
        val res = holder.itemView.resources
        val isHidden = f.id in hidden
        var count = res.getQuantityString(R.plurals.video_count, f.count, f.count)
        if (isHidden) count += "  •  " + res.getString(R.string.hidden_tag)
        holder.b.name.text = f.name
        holder.b.count.text = count
        holder.b.root.alpha = if (isHidden) 0.5f else 1f
        holder.b.check.visibility = if (selecting) View.VISIBLE else View.GONE
        holder.b.check.isChecked = f.id in selected

        holder.itemView.setOnClickListener {
            if (selecting) {
                if (!selected.add(f.id)) selected.remove(f.id)
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos)
                onSelectionChanged()
            } else {
                onOpen(f)
            }
        }
        holder.itemView.setOnLongClickListener {
            if (!selecting) onLongPress(f)
            true
        }
    }
}
