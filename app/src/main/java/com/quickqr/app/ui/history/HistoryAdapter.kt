package com.quickqr.app.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.quickqr.app.R
import com.quickqr.app.data.ScanEntity
import com.quickqr.app.databinding.ItemHistoryBinding
import com.quickqr.app.util.ContentParser
import com.quickqr.app.util.ContentTypeLabel
import com.quickqr.app.util.ParsedContent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val onItemClick: (ScanEntity) -> Unit,
    private val onToggleFavorite: (ScanEntity) -> Unit,
    private val onCopy: (ScanEntity) -> Unit,
    private val onShare: (ScanEntity) -> Unit,
    private val onOpenUrl: (ScanEntity) -> Unit,
    private val onDelete: (ScanEntity) -> Unit
) : ListAdapter<ScanEntity, HistoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("MMM d, yyyy \u00b7 h:mm a", Locale.getDefault())

        fun bind(scan: ScanEntity) {
            val parsed: ParsedContent = ContentParser.parse(scan.content)
            val (emoji, label) = ContentTypeLabel.of(parsed)

            binding.textTypeLabel.text = "$emoji $label"
            binding.textPreview.text = ContentTypeLabel.previewOf(parsed)
            binding.textTimestamp.text = dateFormat.format(Date(scan.timestamp))
            binding.btnFavorite.setImageResource(
                if (scan.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )

            binding.root.setOnClickListener { onItemClick(scan) }
            binding.btnFavorite.setOnClickListener { onToggleFavorite(scan) }
            binding.btnMore.setOnClickListener { anchor ->
                val popup = PopupMenu(anchor.context, anchor)
                popup.menu.add(0, 1, 0, R.string.copy_result)
                popup.menu.add(0, 2, 1, R.string.share_result)
                if (parsed is ParsedContent.Url) {
                    popup.menu.add(0, 3, 2, R.string.open_url)
                }
                popup.menu.add(0, 4, 3, R.string.delete)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> onCopy(scan)
                        2 -> onShare(scan)
                        3 -> onOpenUrl(scan)
                        4 -> onDelete(scan)
                    }
                    true
                }
                popup.show()
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ScanEntity>() {
        override fun areItemsTheSame(oldItem: ScanEntity, newItem: ScanEntity) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ScanEntity, newItem: ScanEntity) = oldItem == newItem
    }
}
