package com.example.promisekeeper

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import coil.load
import coil.request.CachePolicy
import coil.transform.CircleCropTransformation
import com.example.promisekeeper.databinding.FragmentProfileBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val statsViewModel: StatsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        observeStats()
        setupMotto()
        setupEmptyState()
    }

    override fun onResume() {
        super.onResume()
        setupUserInfo()
        statsViewModel.refresh()
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_settings -> {
                    parentFragmentManager.beginTransaction()
                        .setCustomAnimations(
                            R.anim.slide_in_right,
                            R.anim.slide_out_left,
                            R.anim.slide_in_left,
                            R.anim.slide_out_right
                        )
                        .replace(R.id.nav_host_fragment, SettingsFragment())
                        .addToBackStack(null)
                        .commit()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupUserInfo() {
        val user = auth.currentUser
        val sharedPrefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val customPhotoUri = sharedPrefs.getString("custom_profile_photo", null)
        
        val motto = sharedPrefs.getString("user_motto", null)
        binding.tvMotto.text = motto ?: getString(R.string.motto_hint)

        if (user != null) {
            binding.userName.text = user.displayName ?: "User"
            binding.userEmail.text = user.email
            binding.userEmail.visibility = View.VISIBLE
            binding.btnSignInFooter.visibility = View.GONE
            
            val photoSource = customPhotoUri ?: user.photoUrl?.toString()?.replace("/s96-c/", "/s400-c/")
            loadProfileImage(photoSource)
        } else {
            binding.userName.text = getString(R.string.nav_profile)
            binding.userEmail.text = "Guest Account"
            binding.userEmail.visibility = View.VISIBLE
            binding.btnSignInFooter.visibility = View.VISIBLE
            
            loadProfileImage(customPhotoUri ?: R.drawable.ic_personal)
        }
    }

    private fun loadProfileImage(source: Any?) {
        binding.profileImage.load(source) {
            crossfade(true)
            placeholder(R.drawable.ic_personal)
            error(R.drawable.ic_personal)
            diskCachePolicy(CachePolicy.ENABLED)
            transformations(CircleCropTransformation())
        }
    }

    private fun setupMotto() {
        binding.tvMotto.setOnClickListener {
            val sharedPrefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
            val currentMotto = sharedPrefs.getString("user_motto", "")

            val editText = EditText(requireContext()).apply {
                setText(currentMotto)
                hint = getString(R.string.motto_edit_hint)
                setSelection(currentMotto?.length ?: 0)
            }

            val container = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(60, 20, 60, 0)
                addView(editText)
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.set_motto_title)
                .setView(container)
                .setPositiveButton("Save") { _, _ ->
                    val newMotto = editText.text.toString().trim()
                    sharedPrefs.edit().putString("user_motto", if (newMotto.isEmpty()) null else newMotto).apply()
                    setupUserInfo()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun observeStats() {
        statsViewModel.stats.observe(viewLifecycleOwner) { stats ->
            if (stats != null) {
                binding.tvTotalCount.text = stats.allTimeTotal.toString()
                binding.tvSuccessRate.text = getString(R.string.percent_format, stats.allTimeSuccessPercentage)
                binding.tvLongestStreak.text = stats.longestStreak.toString()

                // Multiplier Logic
                if (stats.currentMultiplier > 1) {
                    binding.tvMultiplier.visibility = View.VISIBLE
                    binding.tvMultiplier.text = getString(R.string.multiplier_format, stats.currentMultiplier)
                } else {
                    binding.tvMultiplier.visibility = View.GONE
                }

                // Incremental XP & Leveling Logic
                val totalXp = stats.totalXp
                
                var level = 1
                var xpForNextLevel = 10
                var tempXp = totalXp
                
                while (tempXp >= xpForNextLevel) {
                    tempXp -= xpForNextLevel
                    level++
                    xpForNextLevel += 10
                }
                
                val currentXpInLevel = tempXp
                val maxXpForLevel = xpForNextLevel

                binding.tvUserLevel.text = getString(R.string.level_gaming_format, level)
                binding.tvXpProgress.text = getString(R.string.xp_format, currentXpInLevel, maxXpForLevel)
                binding.xpProgressBar.max = maxXpForLevel
                binding.xpProgressBar.setProgress(currentXpInLevel, true)

                updateAchievementsAndMilestones(stats.allTimeKeptCount)
            }
        }
    }

    private fun updateAchievementsAndMilestones(keptCount: Int) {
        val sharedPrefs = requireContext().getSharedPreferences("achievements", Context.MODE_PRIVATE)
        val lastViewedCount = sharedPrefs.getInt("last_viewed_count", 0)

        val allMilestones = listOf(
            Achievement("1", getString(R.string.achievement_1_name), getString(R.string.achievement_1_desc), 1, R.drawable.milestone_1, R.drawable.milestone_1_locked),
            Achievement("5", getString(R.string.achievement_5_name), getString(R.string.achievement_5_desc), 5, R.drawable.milestone_2, R.drawable.milestone_2_locked),
            Achievement("10", getString(R.string.achievement_10_name), getString(R.string.achievement_10_desc), 10, R.drawable.milestone_3, R.drawable.milestone_3_locked),
            Achievement("25", getString(R.string.achievement_25_name), getString(R.string.achievement_25_desc), 25, R.drawable.milestone_4, R.drawable.milestone_4_locked),
            Achievement("50", getString(R.string.achievement_50_name), getString(R.string.achievement_50_desc), 50, R.drawable.milestone_5, R.drawable.milestone_5_locked),
            Achievement("100", getString(R.string.achievement_100_name), getString(R.string.achievement_100_desc), 100, R.drawable.milestone_6, R.drawable.milestone_6_locked),
            Achievement("250", getString(R.string.achievement_250_name), getString(R.string.achievement_250_desc), 250, R.drawable.milestone_7, R.drawable.milestone_7_locked),
            Achievement("500", getString(R.string.achievement_500_name), getString(R.string.achievement_500_desc), 500, R.drawable.milestone_8, R.drawable.milestone_8_locked),
            Achievement("1000", getString(R.string.achievement_1000_name), getString(R.string.achievement_1000_desc), 1000, R.drawable.milestone_9, R.drawable.milestone_9_locked),
            Achievement("2000", getString(R.string.achievement_2000_name), getString(R.string.achievement_2000_desc), 2000, R.drawable.milestone_10, R.drawable.milestone_10_locked),
            Achievement("3000", getString(R.string.achievement_3000_name), getString(R.string.achievement_3000_desc), 3000, R.drawable.milestone_11, R.drawable.milestone_11_locked),
            Achievement("4000", getString(R.string.achievement_4000_name), getString(R.string.achievement_4000_desc), 4000, R.drawable.milestone_12, R.drawable.milestone_12_locked),
            Achievement("5000", getString(R.string.achievement_5000_name), getString(R.string.achievement_5000_desc), 5000, R.drawable.milestone_13, R.drawable.milestone_13_locked),
            Achievement("6000", getString(R.string.achievement_6000_name), getString(R.string.achievement_6000_desc), 6000, R.drawable.milestone_14, R.drawable.milestone_14_locked),
            Achievement("7000", getString(R.string.achievement_7000_name), getString(R.string.achievement_7000_desc), 7000, R.drawable.milestone_15, R.drawable.milestone_15_locked),
            Achievement("8000", getString(R.string.achievement_8000_name), getString(R.string.achievement_8000_desc), 8000, R.drawable.milestone_16, R.drawable.milestone_16_locked),
            Achievement("9000", getString(R.string.achievement_9000_name), getString(R.string.achievement_9000_desc), 9000, R.drawable.milestone_17, R.drawable.milestone_17_locked),
            Achievement("10000", getString(R.string.achievement_10000_name), getString(R.string.achievement_10000_desc), 10000, R.drawable.milestone_18, R.drawable.milestone_18_locked)
        )

        val processedMilestones = allMilestones.map { ach ->
            val isUnlocked = keptCount >= ach.threshold
            ach.copy(
                isUnlocked = isUnlocked,
                isNew = isUnlocked && ach.threshold > lastViewedCount
            )
        }

        val nextMilestone = processedMilestones.firstOrNull { !it.isUnlocked }
        if (nextMilestone != null) {
            val remaining = nextMilestone.threshold - keptCount
            binding.milestoneCard.visibility = View.VISIBLE
            binding.tvMilestoneTeaser.text = getString(
                R.string.milestone_teaser_format_badge,
                remaining,
                nextMilestone.name
            )
        } else {
            binding.milestoneCard.visibility = View.GONE
        }

        val hasNew = processedMilestones.any { it.isNew }
        binding.tvSectionNewBadge.visibility = if (hasNew) View.VISIBLE else View.GONE
        binding.tvAchievementsHeader.text = getString(R.string.achievements_header)

        binding.rvAchievements.visibility = View.VISIBLE
        binding.emptyAchievementsState.visibility = View.GONE
        binding.rvAchievements.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = AchievementAdapter(processedMilestones)
        }

        sharedPrefs.edit().putInt("last_viewed_count", keptCount).apply()
    }

    private fun setupEmptyState() {
        binding.btnStartFirstPromise.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
