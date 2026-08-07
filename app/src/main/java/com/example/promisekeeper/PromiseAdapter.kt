package com.example.promisekeeper

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class PromiseAdapter(
    private var promises: List<Promise>,
    private val onStatusClick: (Promise) -> Unit
) : RecyclerView.Adapter<PromiseAdapter.PromiseViewHolder>() {

    class PromiseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivStatusIcon: ImageView = view.findViewById(R.id.ivStatusIcon)
        val tvDescription: TextView = view.findViewById(R.id.tvPromiseDescription)
        val tvStatusText: TextView = view.findViewById(R.id.tvStatusText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PromiseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_promise, parent, false)
        return PromiseViewHolder(view)
    }

    override fun onBindViewHolder(holder: PromiseViewHolder, position: Int) {
        val promise = promises[position]
        holder.tvDescription.text = promise.description
        
        val context = holder.itemView.context
        
        when (promise.status) {
            PromiseStatus.PENDING -> {
                holder.tvStatusText.text = context.getString(R.string.pending)
                holder.tvStatusText.setTextColor(ContextCompat.getColor(context, R.color.status_pending))
                holder.ivStatusIcon.setImageResource(R.drawable.ic_promise_pending)
                holder.ivStatusIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.status_pending))
            }
            PromiseStatus.KEPT -> {
                holder.tvStatusText.text = context.getString(R.string.kept)
                holder.tvStatusText.setTextColor(ContextCompat.getColor(context, R.color.status_kept))
                holder.ivStatusIcon.setImageResource(R.drawable.ic_promise_kept)
                holder.ivStatusIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.status_kept))
            }
            PromiseStatus.BROKEN -> {
                holder.tvStatusText.text = context.getString(R.string.broken)
                holder.tvStatusText.setTextColor(ContextCompat.getColor(context, R.color.status_broken))
                holder.ivStatusIcon.setImageResource(R.drawable.ic_promise_broken)
                holder.ivStatusIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.status_broken))
            }
        }

        holder.itemView.setOnClickListener {
            onStatusClick(promise)
        }
    }

    override fun getItemCount() = promises.size

    fun updateData(newPromises: List<Promise>) {
        promises = newPromises
        notifyDataSetChanged()
    }
}
