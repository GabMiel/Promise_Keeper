package com.example.promisekeeper

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView

class WeeklyBarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val animators = mutableListOf<ValueAnimator>()

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
     * Set progress for each day (0-100) with animation
     */
    fun setProgress(progressValues: List<Int>, animate: Boolean = true) {
        if (progressValues.size != 7) return
        
        cancelAnimations()
        
        val density = resources.displayMetrics.density
        // Use a larger portion of the container height. 
        // We'll assume the container is around 120dp based on fragment_stats.xml
        val maxHeight = 100 * density 
        val minHeight = 6 * density  // Slightly thicker base line

        for (i in 0 until 7) {
            val barItem = getChildAt(i)
            val bar = barItem.findViewById<View>(R.id.viewBar)
            val progress = progressValues[i].coerceIn(0, 100)
            
            // Adjust calculation: Ensure 0% still has minHeight, but 100% fills maxHeight
            val targetHeight = (minHeight + (progress / 100f) * (maxHeight - minHeight)).toInt()
            
            if (animate) {
                val startHeight = bar.layoutParams.height.coerceAtLeast(minHeight.toInt())
                val animator = ValueAnimator.ofInt(startHeight, targetHeight).apply {
                    duration = 1000 + (i * 60L) 
                    interpolator = DecelerateInterpolator(1.5f)
                    addUpdateListener { valueAnimator ->
                        val params = bar.layoutParams
                        params.height = valueAnimator.animatedValue as Int
                        bar.layoutParams = params
                    }
                    start()
                }
                animators.add(animator)
            } else {
                val params = bar.layoutParams
                params.height = targetHeight
                bar.layoutParams = params
            }
            
            // Update alpha based on progress
            bar.alpha = if (progress > 0) 1.0f else 0.3f
        }
    }

    /**
     * Set progress for each day using float values (0.0 to 1.0)
     */
    fun setProgressFloats(progressValues: List<Float>, animate: Boolean = true) {
        setProgress(progressValues.map { (it * 100).toInt() }, animate)
    }

    private fun cancelAnimations() {
        animators.forEach { it.cancel() }
        animators.clear()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelAnimations()
    }
}
