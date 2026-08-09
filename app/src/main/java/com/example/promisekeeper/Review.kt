package com.example.promisekeeper

data class Review(
    val id: String = "",
    val userId: String = "",
    val timestamp: Long = 0L, // Start of the day being reviewed
    val completedAt: Long = System.currentTimeMillis(),
    val summary: String? = null
)
