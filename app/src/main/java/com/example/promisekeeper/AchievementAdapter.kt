package com.example.promisekeeper

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.RecyclerView
import com.example.promisekeeper.databinding.ItemAchievementBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AchievementAdapter(private val achievements: List<Achievement>) :
    RecyclerView.Adapter<AchievementAdapter.AchievementViewHolder>() {

    class AchievementViewHolder(private val binding: ItemAchievementBinding) :
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(achievement: Achievement) {
            val context = binding.root.context
            binding.tvAchievementName.text = achievement.name
            
            // Show threshold instead of generic level
            binding.tvAchievementLevel.text = context.getString(
                if (achievement.threshold == 1) R.string.count_suffix_single else R.string.count_suffix_plural,
                achievement.threshold
            )

            // Lock/Unlock Visuals
            if (!achievement.isUnlocked) {
                binding.ivAchievementIcon.setImageResource(achievement.lockedIconResId)
                binding.root.alpha = 0.6f
                binding.ivAchievementIcon.clearColorFilter()
            } else {
                binding.ivAchievementIcon.setImageResource(achievement.iconResId)
                binding.root.alpha = 1.0f
                binding.ivAchievementIcon.clearColorFilter()
            }
            
            // Sub Prompt 4: New Badge & Animation
            binding.tvNewBadge.visibility = if (achievement.isNew) View.VISIBLE else View.GONE
            if (achievement.isNew) {
                val pulse = AnimationUtils.loadAnimation(context, R.anim.pulse)
                binding.tvNewBadge.startAnimation(pulse)
            }

            // Sub Prompt 6: Interactive Achievement Tooltips
            binding.root.setOnClickListener {
                val statusText = if (achievement.isUnlocked) {
                    context.getString(R.string.achievement_status_unlocked)
                } else {
                    context.getString(R.string.achievement_status_locked)
                }
                
                val earnText = if (!achievement.isUnlocked) {
                    "\n\n" + context.getString(R.string.achievement_how_to_earn, achievement.description)
                } else {
                    "\n\n" + achievement.description
                }

                MaterialAlertDialogBuilder(context)
                    .setTitle(achievement.name)
                    .setMessage("$statusText$earnText")
                    .setPositiveButton(R.string.dialog_got_it, null)
                    .show()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AchievementViewHolder {
        val binding = ItemAchievementBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AchievementViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AchievementViewHolder, position: Int) {
        holder.bind(achievements[position])
    }

    override fun getItemCount() = achievements.size
}
