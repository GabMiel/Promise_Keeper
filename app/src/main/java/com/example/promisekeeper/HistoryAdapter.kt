package com.example.promisekeeper

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.promisekeeper.databinding.ItemHistoryDayBinding
import com.example.promisekeeper.databinding.ItemHistoryHeaderBinding

class HistoryAdapter(private val onItemClick: (HistoryItem.Day) -> Unit) : 
    ListAdapter<HistoryItem, RecyclerView.ViewHolder>(HistoryDiffCallback()) {

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is HistoryItem.Header -> TYPE_HEADER
            is HistoryItem.Day -> TYPE_DAY
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(ItemHistoryHeaderBinding.inflate(inflater, parent, false))
            TYPE_DAY -> DayViewHolder(ItemHistoryDayBinding.inflate(inflater, parent, false), onItemClick)
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is HeaderViewHolder -> holder.bind(item as HistoryItem.Header)
            is DayViewHolder -> {
                val dayItem = item as HistoryItem.Day
                val isFirstInGroup = position == 0 || getItem(position - 1) is HistoryItem.Header
                val isLastInGroup = position == itemCount - 1 || getItem(position + 1) is HistoryItem.Header
                holder.bind(dayItem, isFirstInGroup, isLastInGroup)
            }
        }
    }

    class HeaderViewHolder(private val binding: ItemHistoryHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: HistoryItem.Header) {
            binding.tvHeaderMonth.text = header.month
        }
    }

    class DayViewHolder(
        private val binding: ItemHistoryDayBinding,
        private val onClick: (HistoryItem.Day) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(day: HistoryItem.Day, isFirst: Boolean, isLast: Boolean) {
            val context = itemView.context
            binding.tvDate.text = day.date
            binding.tvPercentage.text = context.getString(R.string.percent_format, day.percentage)
            binding.tvKeptCount.text = context.getString(R.string.stat_format_kept, day.kept)
            binding.tvBrokenCount.text = context.getString(R.string.stat_format_broken, day.broken)
            binding.tvPendingCount.text = context.getString(R.string.stat_format_pending, day.pending)

            // Reflection Snippet handling
            if (!day.reflectionSnippet.isNullOrBlank()) {
                binding.tvReflectionSnippet.visibility = View.VISIBLE
                binding.tvReflectionSnippet.text = "Insight: ${day.reflectionSnippet}"
                binding.tvReviewLink.text = context.getString(R.string.view_reflection)
                binding.tvReviewLink.setTextColor(ContextCompat.getColor(context, R.color.status_kept))
            } else {
                binding.tvReflectionSnippet.visibility = View.GONE
                binding.tvReviewLink.text = context.getString(R.string.review_day_link)
                binding.tvReviewLink.setTextColor(ContextCompat.getColor(context, R.color.accent_red))
            }

            val backgroundRes = when {
                isFirst && isLast -> R.drawable.bg_input_field
                isFirst -> R.drawable.bg_history_item_top
                isLast -> R.drawable.bg_history_item_bottom
                else -> R.drawable.bg_history_item_middle
            }
            binding.rootLayout.setBackgroundResource(backgroundRes)
            binding.divider.visibility = if (isLast) View.GONE else View.VISIBLE
            
            binding.root.setOnClickListener { onClick(day) }
        }
    }

    class HistoryDiffCallback : DiffUtil.ItemCallback<HistoryItem>() {
        override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
            return if (oldItem is HistoryItem.Header && newItem is HistoryItem.Header) {
                oldItem.month == newItem.month
            } else if (oldItem is HistoryItem.Day && newItem is HistoryItem.Day) {
                oldItem.timestamp == newItem.timestamp
            } else false
        }
        override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean = oldItem == newItem
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_DAY = 1
    }
}
