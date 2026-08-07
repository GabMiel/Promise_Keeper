package com.example.promisekeeper

data class Promise(
    val id: String = "",
    val userId: String = "",
    val description: String = "",
    val category: String = "",
    val reminderTime: String? = null,
    val notes: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val status: PromiseStatus = PromiseStatus.PENDING,
    val reasonForFailure: String? = null,
    val improvementPlan: String? = null
)

enum class PromiseStatus {
    PENDING,
    KEPT,
    BROKEN
}
