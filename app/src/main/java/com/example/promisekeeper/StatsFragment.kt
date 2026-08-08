package com.example.promisekeeper

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider

class StatsFragment : Fragment() {
    private lateinit var viewModel: StatsViewModel

    private lateinit var tvCurrentWeek: TextView
    private lateinit var tvSuccessRatePercent: TextView
    private lateinit var tvSuccessRateSummary: TextView
    private lateinit var barChartView: WeeklyBarChartView
    private lateinit var tvCurrentStreakValue: TextView
    private lateinit var tvLongestStreakValue: TextView
    private lateinit var tvCommonReasonValue: TextView
    private lateinit var tvCommonReasonCount: TextView
    private lateinit var tvInsightMessage: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_stats, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[StatsViewModel::class.java]
        
        initViews(view)
        setupObservers()
        setupListeners(view)
    }

    private fun initViews(view: View) {
        tvCurrentWeek = view.findViewById(R.id.tvCurrentWeek)
        tvSuccessRatePercent = view.findViewById(R.id.tvSuccessRatePercent)
        tvSuccessRateSummary = view.findViewById(R.id.tvSuccessRateSummary)
        barChartView = view.findViewById(R.id.barChartView)
        tvCurrentStreakValue = view.findViewById(R.id.tvCurrentStreakValue)
        tvLongestStreakValue = view.findViewById(R.id.tvLongestStreakValue)
        tvCommonReasonValue = view.findViewById(R.id.tvCommonReasonValue)
        tvCommonReasonCount = view.findViewById(R.id.tvCommonReasonCount)
        tvInsightMessage = view.findViewById(R.id.tvInsightMessage)
    }

    private fun setupObservers() {
        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            stats?.let { updateUI(it) }
        }
    }

    private fun setupListeners(view: View) {
        view.findViewById<ImageView>(R.id.ivPrevWeek).setOnClickListener {
            viewModel.changeWeek(-1)
        }
        view.findViewById<ImageView>(R.id.ivNextWeek).setOnClickListener {
            viewModel.changeWeek(1)
        }
    }

    private fun updateUI(stats: WeeklyStats) {
        tvCurrentWeek.text = stats.weekLabel
        tvSuccessRatePercent.text = getString(R.string.percent_format, stats.successPercentage)
        tvSuccessRateSummary.text = getString(R.string.success_rate_summary, stats.keptCount, stats.totalCount)
        
        tvCurrentStreakValue.text = stats.currentStreak.toString()
        tvLongestStreakValue.text = stats.longestStreak.toString()
        
        tvCommonReasonValue.text = stats.commonReason ?: "None"
        tvCommonReasonCount.text = if (stats.commonReasonCount > 0) "(${stats.commonReasonCount} times)" else ""
        tvInsightMessage.text = stats.insightMessage ?: "Keep up the good work!"

        // Update Bar Chart using the custom view
        barChartView.setProgressFloats(stats.dailyProgress)
    }
}
