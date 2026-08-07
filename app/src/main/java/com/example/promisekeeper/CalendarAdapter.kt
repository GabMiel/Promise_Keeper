package com.example.promisekeeper

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class CalendarAdapter(
    private var days: List<CalendarDay>,
    private val onDateSelected: (CalendarDay) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder>() {

    class CalendarViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDayName: TextView = view.findViewById(R.id.tvDayName)
        val tvDayNumber: TextView = view.findViewById(R.id.tvDayNumber)
        val cvDateCircle: MaterialCardView = view.findViewById(R.id.cvDateCircle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_day_item, parent, false)
        return CalendarViewHolder(view)
    }

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
        val day = days[position]
        holder.tvDayName.text = day.dayName
        holder.tvDayNumber.text = day.dayNumber

        val context = holder.itemView.context
        if (day.isSelected) {
            holder.cvDateCircle.setCardBackgroundColor(ContextCompat.getColor(context, R.color.accent_red))
            holder.tvDayNumber.setTextColor(ContextCompat.getColor(context, R.color.white))
        } else {
            holder.cvDateCircle.setCardBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
            holder.tvDayNumber.setTextColor(ContextCompat.getColor(context, R.color.text_dark))
        }

        holder.itemView.setOnClickListener {
            // Update selection logic
            days.forEach { it.isSelected = false }
            day.isSelected = true
            notifyDataSetChanged()
            onDateSelected(day)
        }
    }

    override fun getItemCount() = days.size

    fun updateDays(newDays: List<CalendarDay>) {
        days = newDays
        notifyDataSetChanged()
    }
}
