package com.example.promisekeeper

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.promisekeeper.databinding.FragmentStatsBinding

class StatsFragment : Fragment() {
    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: StatsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupObservers()
        setupListeners()
    }

    private fun setupObservers() {
        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            stats?.let { updateUI(it) }
        }
        
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.isVisible = isLoading
            binding.statsContent.alpha = if (isLoading) 0.5f else 1.0f
            binding.statsContent.isEnabled = !isLoading
        }
    }

    private fun setupListeners() {
        binding.ivPrevWeek.setOnClickListener {
            viewModel.changeWeek(-1)
        }
        binding.ivNextWeek.setOnClickListener {
            viewModel.changeWeek(1)
        }
    }

    private fun updateUI(stats: WeeklyStats) {
        binding.tvCurrentWeek.text = stats.weekLabel
        binding.tvSuccessRatePercent.text = getString(R.string.percent_format, stats.successPercentage)
        binding.tvSuccessRateSummary.text = getString(R.string.success_rate_summary, stats.keptCount, stats.totalCount)
        
        binding.tvCurrentStreakValue.text = stats.currentStreak.toString()
        binding.tvLongestStreakValue.text = stats.longestStreak.toString()
        
        binding.tvCommonReasonValue.text = stats.commonReason ?: "None"
        binding.tvCommonReasonCount.text = if (stats.commonReasonCount > 0) "(${stats.commonReasonCount} times)" else ""
        binding.tvInsightMessage.text = stats.insightMessage ?: "Keep up the good work!"

        // Update Bar Chart with animation
        binding.barChartView.setProgressFloats(stats.dailyProgress, animate = true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
