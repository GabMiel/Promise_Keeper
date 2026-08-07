package com.example.promisekeeper

import java.util.Date

data class CalendarDay(
    val date: Date,
    val dayName: String, // e.g., "Mon"
    val dayNumber: String, // e.g., "22"
    var isSelected: Boolean = false
)
