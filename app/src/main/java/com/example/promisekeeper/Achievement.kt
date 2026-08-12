package com.example.promisekeeper

data class Achievement(
    val id: String,
    val name: String,
    val description: String,
    val threshold: Int,
    val iconResId: Int,
    val lockedIconResId: Int,
    val isUnlocked: Boolean = false,
    val isNew: Boolean = false
)
