package com.example.promisekeeper

import java.util.Date

enum class DayStatus {
    NONE,       // No promises
    ALL_KEPT,   // All promises kept (Green)
    HAS_BROKEN  // At least one broken or expired (Red)
}

data class CalendarDay(
    val date: Date,
    val dayNumber: String,
    var isSelected: Boolean = false,
    var isToday: Boolean = false,
    var status: DayStatus = DayStatus.NONE
)
