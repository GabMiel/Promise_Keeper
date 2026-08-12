package com.example.promisekeeper

import android.content.Context

object ReminderUtils {
    /**
     * Reschedules the daily review reminder if it is enabled in settings.
     * Note: Currently, scheduling logic is disabled as it is not integrated into the manifest.
     */
    fun rescheduleIfEnabled(context: Context) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("review_reminder_enabled", true)
        
        if (isEnabled) {
            // Future implementation: scheduleReviewReminder(context, savedTime)
        }
    }
}
