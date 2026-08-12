package com.example.promisekeeper

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

data class WeeklyStats(
    val weekLabel: String,
    val successPercentage: Int,
    val keptCount: Int,
    val brokenCount: Int,
    val totalCount: Int,
    val allTimeTotal: Int,
    val allTimeKeptCount: Int,
    val allTimeBrokenCount: Int,
    val allTimeSuccessPercentage: Int,
    val totalXp: Int,
    val currentMultiplier: Int,
    val dailyProgress: List<Float>,
    val currentStreak: Int,
    val longestStreak: Int,
    val commonReason: String?,
    val commonReasonCount: Int,
    val insightMessage: String?
)

class StatsViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()

    private val _stats = MutableLiveData<WeeklyStats?>()
    val stats: LiveData<WeeklyStats?> = _stats

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private var currentWeekOffset = 0
    private var cachedAllPromises: List<Promise>? = null

    init {
        loadStats(forceRefresh = true)
    }

    fun changeWeek(delta: Int) {
        currentWeekOffset += delta
        val cached = cachedAllPromises
        if (cached != null) {
            processStats(cached)
        } else {
            loadStats()
        }
    }

    fun refresh() {
        loadStats(forceRefresh = true)
    }

    private fun loadStats(forceRefresh: Boolean = false) {
        val userId = auth.currentUser?.uid ?: return
        
        val cached = cachedAllPromises
        if (!forceRefresh && cached != null) {
            processStats(cached)
            return
        }

        _isLoading.value = true
        db.collection("users").document(userId).collection("promises")
            .get()
            .addOnSuccessListener { snapshot ->
                val allPromises = snapshot.toObjects(Promise::class.java)
                cachedAllPromises = allPromises
                processStats(allPromises)
                _isLoading.value = false
            }
            .addOnFailureListener {
                _isLoading.value = false
            }
    }

    private fun processStats(allPromises: List<Promise>) {
        val offset = currentWeekOffset
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.add(Calendar.WEEK_OF_YEAR, offset)
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfWeek = calendar.timeInMillis
        
        val weekLabel = if (offset == 0) {
            "This Week"
        } else {
            val start = calendar.time
            val tempCal = calendar.clone() as Calendar
            tempCal.add(Calendar.DAY_OF_WEEK, 6)
            val end = tempCal.time
            val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
            "${sdf.format(start)} - ${sdf.format(end)}"
        }

        val tempCalEnd = calendar.clone() as Calendar
        tempCalEnd.add(Calendar.DAY_OF_WEEK, 7)
        val endOfWeek = tempCalEnd.timeInMillis

        val weeklyPromises = allPromises.filter { it.timestamp in startOfWeek until endOfWeek }
        calculateWeeklyStats(weekLabel, weeklyPromises, allPromises)
    }

    private fun calculateWeeklyStats(label: String, weekly: List<Promise>, all: List<Promise>) {
        val total = weekly.size
        val kept = weekly.count { it.status == PromiseStatus.KEPT }
        val broken = weekly.count { it.status == PromiseStatus.BROKEN }
        val percent = if (total > 0) (kept * 100) / total else 0

        val allTimeTotalCount = all.size
        val allTimeKeptCount = all.count { it.status == PromiseStatus.KEPT }
        val allTimeBrokenCount = all.count { it.status == PromiseStatus.BROKEN }
        val allTimeSuccessPercent = if (allTimeTotalCount > 0) (allTimeKeptCount * 100) / allTimeTotalCount else 0
        
        val sortedDates = all.groupBy {
            val c = Calendar.getInstance()
            c.timeInMillis = it.timestamp
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            c.timeInMillis
        }.toSortedMap()

        // Forgiving Streak & Multiplier XP Logic
        var totalXp = 0
        var currentStreak = 0
        var longestStreak = 0
        var lastDate: Long? = null
        var currentMultiplier = 1

        sortedDates.forEach { (date, dayPromises) ->
            // Streak only breaks if a day is skipped (gap in sortedDates)
            val last = lastDate
            if (last == null || date == last + 86400000L) {
                currentStreak++
            } else {
                currentStreak = 1 // Streak reset because a day was missed
            }
            
            if (currentStreak > longestStreak) longestStreak = currentStreak
            
            // Multiplier increases every 7 days (x2, x3, etc.)
            currentMultiplier = (currentStreak / 7) + 1
            
            val keptInDay = dayPromises.count { it.status == PromiseStatus.KEPT }
            val brokenInDay = dayPromises.count { it.status == PromiseStatus.BROKEN }
            
            totalXp += (keptInDay * 10 * currentMultiplier)
            totalXp += (brokenInDay * 5) // Reward for honesty
            
            lastDate = date
        }

        val dailyProgress = MutableList(7) { 0f }
        val dailyTotals = IntArray(7) { 0 }
        val dailyKept = IntArray(7) { 0 }
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        
        weekly.forEach { promise ->
            cal.timeInMillis = promise.timestamp
            var day = cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY
            if (day < 0) day += 7
            
            if (day in 0..6) {
                dailyTotals[day]++
                if (promise.status == PromiseStatus.KEPT) dailyKept[day]++
            }
        }
        for (i in 0 until 7) {
            if (dailyTotals[i] > 0) dailyProgress[i] = dailyKept[i].toFloat() / dailyTotals[i].toFloat()
        }

        val allBroken = all.filter { it.status == PromiseStatus.BROKEN && it.failureTag != null }
        val reasonCounts = allBroken.groupingBy { it.failureTag!! }.eachCount()
        val mostCommon = reasonCounts.maxByOrNull { it.value }

        val insight = generateInsight(allBroken)

        _stats.postValue(WeeklyStats(
            weekLabel = label,
            successPercentage = percent,
            keptCount = kept,
            brokenCount = broken,
            totalCount = total,
            allTimeTotal = allTimeTotalCount,
            allTimeKeptCount = allTimeKeptCount,
            allTimeBrokenCount = allTimeBrokenCount,
            allTimeSuccessPercentage = allTimeSuccessPercent,
            totalXp = totalXp,
            currentMultiplier = currentMultiplier,
            dailyProgress = dailyProgress,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            commonReason = mostCommon?.key,
            commonReasonCount = mostCommon?.value ?: 0,
            insightMessage = insight
        ))
    }

    private fun generateInsight(allBroken: List<Promise>): String {
        if (allBroken.isEmpty()) return "You're doing great! Keep keeping those promises."

        val brokenByDay = allBroken.groupBy {
            val c = Calendar.getInstance()
            c.timeInMillis = it.timestamp
            c.get(Calendar.DAY_OF_WEEK)
        }
        val worstDayEntry = brokenByDay.maxByOrNull { it.value.size }
        
        val brokenByCategory = allBroken.groupBy { it.category }
        val worstCategoryEntry = brokenByCategory.maxByOrNull { it.value.size }

        return when {
            worstDayEntry != null && worstDayEntry.value.size > 2 -> {
                val dayName = getDayName(worstDayEntry.key)
                "You tend to break promises on $dayName. Try to be extra mindful then!"
            }
            worstCategoryEntry != null && worstCategoryEntry.value.size > 2 -> {
                "You struggle most with ${worstCategoryEntry.key} promises. Maybe set smaller goals?"
            }
            else -> "Stay consistent! Small steps lead to big changes."
        }
    }

    private fun getDayName(dayOfWeek: Int): String = when (dayOfWeek) {
        Calendar.MONDAY -> "Mondays"
        Calendar.TUESDAY -> "Tuesdays"
        Calendar.WEDNESDAY -> "Wednesdays"
        Calendar.THURSDAY -> "Thursdays"
        Calendar.FRIDAY -> "Fridays"
        Calendar.SATURDAY -> "Saturdays"
        Calendar.SUNDAY -> "Sundays"
        else -> "some days"
    }
}
