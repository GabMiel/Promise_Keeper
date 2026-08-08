package com.example.promisekeeper

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class WeeklyBarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        orientation = HORIZONTAL
        weightSum = 7f
        val days = listOf("M", "T", "W", "T", "F", "S", "S")
        for (day in days) {
            val view = LayoutInflater.from(context).inflate(R.layout.view_bar_item, this, false)
            val params = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            view.layoutParams = params
            view.findViewById<TextView>(R.id.tvBarLabel).text = day
            addView(view)
        }
    }

    /**
     * Set progress for each day (0-100)
     */
    fun setProgress(progressValues: List<Int>) {
        if (progressValues.size != 7) return
        
        val density = resources.displayMetrics.density
        val maxHeight = 60 * density // Max bar height in dp
        val minHeight = 4 * density  // Min bar height in dp

        for (i in 0 until 7) {
            val barItem = getChildAt(i)
            val bar = barItem.findViewById<View>(R.id.viewBar)
            val progress = progressValues[i].coerceIn(0, 100)
            
            val params = bar.layoutParams
            params.height = (minHeight + (progress / 100f) * (maxHeight - minHeight)).toInt()
            bar.layoutParams = params
            
            // Update alpha based on progress
            bar.alpha = if (progress > 0) 1.0f else 0.4f
        }
    }

    /**
     * Set progress for each day using float values (0.0 to 1.0)
     */
    fun setProgressFloats(progressValues: List<Float>) {
        setProgress(progressValues.map { (it * 100).toInt() })
    }
}
