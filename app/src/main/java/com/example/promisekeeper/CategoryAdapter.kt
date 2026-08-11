package com.example.promisekeeper

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class CategoryAdapter(
    private val categories: List<Category>,
    private val onCategorySelected: (Category) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    private var selectedPosition = 0

    data class Category(val name: String, val iconRes: Int, val hasDropdown: Boolean = false)

    inner class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardCategory)
        val icon: ImageView = view.findViewById(R.id.ivCategoryIcon)
        val name: TextView = view.findViewById(R.id.tvCategoryName)

        fun bind(category: Category, position: Int) {
            name.text = category.name
            icon.setImageResource(category.iconRes)

            val isSelected = position == selectedPosition
            val context = itemView.context

            if (isSelected) {
                card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.accent_red_light))
                card.strokeColor = ContextCompat.getColor(context, R.color.accent_red)
                name.setTextColor(ContextCompat.getColor(context, R.color.accent_red))
                icon.setColorFilter(ContextCompat.getColor(context, R.color.accent_red))
            } else {
                card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.white))
                card.strokeColor = ContextCompat.getColor(context, R.color.light_gray)
                name.setTextColor(ContextCompat.getColor(context, R.color.text_gray))
                icon.setColorFilter(ContextCompat.getColor(context, R.color.text_gray))
            }

            itemView.setOnClickListener {
                val oldPosition = selectedPosition
                selectedPosition = adapterPosition
                notifyItemChanged(oldPosition)
                notifyItemChanged(selectedPosition)
                onCategorySelected(category)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position], position)
    }

    override fun getItemCount() = categories.size
}
