package com.example.promisekeeper

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class PromiseAdapter(
    private val onPromiseClick: (Promise) -> Unit,
    private val onPromiseLongClick: (Promise) -> Unit,
    private val onFooterClick: (PromiseStatus) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<Any, RecyclerView.ViewHolder>(AnyDiffCallback()) {

    private var isSelectionMode = false
    private val selectedIds = mutableSetOf<String>()

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
        private const val TYPE_FOOTER = 2
    }

    data class HeaderItem(val title: String, val count: Int, val colorRes: Int)
    data class FooterItem(val actionText: String, val status: PromiseStatus)

    fun setSelectionMode(enabled: Boolean) {
        if (isSelectionMode != enabled) {
            isSelectionMode = enabled
            if (!enabled) selectedIds.clear()
            notifyItemRangeChanged(0, itemCount)
        }
    }

    fun toggleSelection(promiseId: String) {
        if (selectedIds.contains(promiseId)) {
            selectedIds.remove(promiseId)
        } else {
            selectedIds.add(promiseId)
        }
        
        val index = currentList.indexOfFirst { it is Promise && it.id == promiseId }
        if (index != -1) {
            notifyItemChanged(index)
        }
        
        onSelectionChanged(selectedIds.size)
    }

    fun getSelectedPromises(): List<Promise> {
        return currentList.filterIsInstance<Promise>().filter { selectedIds.contains(it.id) }
    }

    fun isSelectionMode() = isSelectionMode

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is HeaderItem -> TYPE_HEADER
            is FooterItem -> TYPE_FOOTER
            else -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.item_promise_header, parent, false))
            TYPE_FOOTER -> FooterViewHolder(inflater.inflate(R.layout.item_promise_footer, parent, false))
            else -> PromiseViewHolder(inflater.inflate(R.layout.item_promise, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is HeaderViewHolder -> holder.bind(item as HeaderItem)
            is FooterViewHolder -> holder.bind(item as FooterItem)
            is PromiseViewHolder -> holder.bind(item as Promise)
        }
    }

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTitle: TextView = view.findViewById(R.id.tvHeaderTitle)
        private val tvCount: TextView = view.findViewById(R.id.tvHeaderCount)

        fun bind(header: HeaderItem) {
            tvTitle.text = header.title
            tvTitle.setTextColor(ContextCompat.getColor(itemView.context, header.colorRes))
            tvCount.text = itemView.context.getString(R.string.count_suffix, header.count)
        }
    }

    inner class FooterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvAction: TextView = view.findViewById(R.id.tvFooterAction)
        fun bind(footer: FooterItem) {
            tvAction.text = footer.actionText
            itemView.setOnClickListener { onFooterClick(footer.status) }
        }
    }

    inner class PromiseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val checkBox: CheckBox = view.findViewById(R.id.checkbox)
        private val cvStatusIcon: MaterialCardView = view.findViewById(R.id.cvStatusIcon)
        private val ivStatusIcon: ImageView = view.findViewById(R.id.ivStatusIcon)
        private val tvDescription: TextView = view.findViewById(R.id.tvDescription)
        private val cvCategoryTag: MaterialCardView = view.findViewById(R.id.cvCategoryTag)
        private val ivCategorySmallIcon: ImageView = view.findViewById(R.id.ivCategorySmallIcon)
        private val tvCategorySmallName: TextView = view.findViewById(R.id.tvCategorySmallName)
        private val tvDateTime: TextView = view.findViewById(R.id.tvDateTime)
        private val tvReason: TextView = view.findViewById(R.id.tvReason)
        private val ivChevron: ImageView = view.findViewById(R.id.ivChevron)

        fun bind(promise: Promise) {
            tvDescription.text = promise.description
            tvDateTime.text = promise.reminderTime
            tvCategorySmallName.text = promise.category

            val categoryIcon = when (promise.category.lowercase()) {
                "study" -> R.drawable.ic_study
                "health" -> R.drawable.ic_health
                "personal" -> R.drawable.ic_personal
                else -> R.drawable.ic_other
            }
            ivCategorySmallIcon.setImageResource(categoryIcon)

            checkBox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            checkBox.isChecked = selectedIds.contains(promise.id)
            ivChevron.visibility = if (isSelectionMode) View.GONE else View.VISIBLE

            applyStatusColors(promise)

            itemView.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(promise.id)
                } else {
                    onPromiseClick(promise)
                }
            }
            
            itemView.setOnLongClickListener {
                if (!isSelectionMode) {
                    onPromiseLongClick(promise)
                    true
                } else false
            }
            
            checkBox.setOnClickListener {
                toggleSelection(promise.id)
            }
        }

        private fun applyStatusColors(promise: Promise) {
            val context = itemView.context
            val (bgColor, iconRes, mainColor) = when (promise.status) {
                PromiseStatus.KEPT -> Triple(R.color.status_kept_light, R.drawable.ic_check, R.color.status_kept)
                PromiseStatus.BROKEN -> Triple(R.color.status_broken_light, R.drawable.ic_promise_broken, R.color.status_broken)
                PromiseStatus.PENDING -> Triple(R.color.status_pending_light, R.drawable.ic_promise_pending, R.color.status_pending)
            }

            cvStatusIcon.setCardBackgroundColor(ContextCompat.getColor(context, bgColor))
            ivStatusIcon.setImageResource(iconRes)
            ivStatusIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, mainColor))
            
            cvCategoryTag.setCardBackgroundColor(ContextCompat.getColor(context, bgColor))
            tvCategorySmallName.setTextColor(ContextCompat.getColor(context, mainColor))
            ivCategorySmallIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, mainColor))
            
            if (promise.status == PromiseStatus.BROKEN && !promise.reasonForFailure.isNullOrBlank()) {
                tvReason.visibility = View.VISIBLE
                tvReason.text = context.getString(R.string.reason_prefix, promise.reasonForFailure)
            } else {
                tvReason.visibility = View.GONE
            }
        }
    }

    class AnyDiffCallback : DiffUtil.ItemCallback<Any>() {
        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
            if (oldItem is Promise && newItem is Promise) return oldItem.id == newItem.id
            if (oldItem is HeaderItem && newItem is HeaderItem) return oldItem.title == newItem.title
            if (oldItem is FooterItem && newItem is FooterItem) return oldItem.status == newItem.status
            return false
        }

        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            return oldItem == newItem
        }
    }
}
