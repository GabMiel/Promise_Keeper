package com.example.promisekeeper

import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class CalendarAdapter(
    private val onDateSelected: (CalendarDay) -> Unit
) : ListAdapter<CalendarDay, CalendarAdapter.CalendarViewHolder>(CalendarDiffCallback()) {

    class CalendarViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDayNumber: TextView = view.findViewById(R.id.tvDayNumber)
        val rootCard: MaterialCardView = view.findViewById(R.id.rootCard)
        val viewTodayIndicator: View = view.findViewById(R.id.viewTodayIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_day_item, parent, false)
        return CalendarViewHolder(view)
    }

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
        val day = getItem(position)
        holder.tvDayNumber.text = day.dayNumber

        val context = holder.itemView.context
        
        // Show indicator if it's today
        holder.viewTodayIndicator.visibility = if (day.isToday) View.VISIBLE else View.INVISIBLE
        
        when {
            day.isSelected -> {
                // Selected State (Primary Highlight)
                holder.rootCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.accent_red))
                holder.rootCard.strokeWidth = 0
                holder.tvDayNumber.setTextColor(ContextCompat.getColor(context, R.color.white))
                holder.viewTodayIndicator.setBackgroundResource(R.drawable.dot_indicator_white)
            }
            day.status == DayStatus.ALL_KEPT -> {
                // Performance: All Kept (Subtle Green)
                holder.rootCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.status_kept_light))
                holder.rootCard.strokeColor = ContextCompat.getColor(context, R.color.status_kept)
                holder.rootCard.strokeWidth = dpToPx(1, context)
                holder.tvDayNumber.setTextColor(ContextCompat.getColor(context, R.color.status_kept))
                holder.viewTodayIndicator.setBackgroundResource(R.drawable.dot_indicator)
            }
            day.status == DayStatus.HAS_BROKEN -> {
                // Performance: Has Broken/Expired (Subtle Red)
                holder.rootCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.status_broken_light))
                holder.rootCard.strokeColor = ContextCompat.getColor(context, R.color.status_broken)
                holder.rootCard.strokeWidth = dpToPx(1, context)
                holder.tvDayNumber.setTextColor(ContextCompat.getColor(context, R.color.status_broken))
                holder.viewTodayIndicator.setBackgroundResource(R.drawable.dot_indicator)
            }
            else -> {
                // Neutral State: Use the dark tan color to contrast with light tan background
                holder.rootCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_tan))
                holder.rootCard.strokeColor = ContextCompat.getColor(context, R.color.light_gray)
                holder.rootCard.strokeWidth = dpToPx(1, context)
                holder.tvDayNumber.setTextColor(ContextCompat.getColor(context, R.color.text_dark))
                holder.viewTodayIndicator.setBackgroundResource(R.drawable.dot_indicator)
            }
        }

        holder.itemView.setOnClickListener {
            if (!day.isSelected) {
                holder.itemView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onDateSelected(day)
            }
        }
    }

    private fun dpToPx(dp: Int, context: android.content.Context): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    class CalendarDiffCallback : DiffUtil.ItemCallback<CalendarDay>() {
        override fun areItemsTheSame(oldItem: CalendarDay, newItem: CalendarDay): Boolean {
            return oldItem.date == newItem.date
        }

        override fun areContentsTheSame(oldItem: CalendarDay, newItem: CalendarDay): Boolean {
            return oldItem == newItem
        }
    }
}
