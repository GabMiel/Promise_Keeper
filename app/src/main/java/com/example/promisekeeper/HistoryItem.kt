package com.example.promisekeeper

sealed class HistoryItem {
    data class Header(val month: String) : HistoryItem()
    data class Day(
        val date: String,
        val percentage: Int,
        val kept: Int,
        val broken: Int,
        val pending: Int,
        val timestamp: Long,
        val hasReflection: Boolean = false,
        val reflectionSnippet: String? = null
    ) : HistoryItem()
}
