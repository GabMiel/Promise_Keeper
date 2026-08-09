package com.example.promisekeeper

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReviewRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun getUnreviewedDays(): List<Long> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val todayStart = calendar.timeInMillis
        val sevenDaysAgo = todayStart - (7 * 86400000L)
        
        // 1. Fetch all reviews from the last 7 days
        val reviewsSnapshot = db.collection("users").document(userId).collection("reviews")
            .whereGreaterThanOrEqualTo("timestamp", sevenDaysAgo)
            .whereLessThan("timestamp", todayStart)
            .get()
            .await()
        
        val reviewedTimestamps = reviewsSnapshot.documents.mapNotNull { it.getLong("timestamp") }.toSet()

        // 2. Fetch all promises from the last 7 days
        val promisesSnapshot = db.collection("users").document(userId).collection("promises")
            .whereGreaterThanOrEqualTo("timestamp", sevenDaysAgo)
            .whereLessThan("timestamp", todayStart)
            .get()
            .await()

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
        // 3. Find days that have promises but no review document
        val unreviewedDays = promisesSnapshot.documents
            .mapNotNull { it.getLong("timestamp") }
            .groupBy { 
                val cal = Calendar.getInstance().apply { timeInMillis = it }
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            .keys
            .filter { it !in reviewedTimestamps }
            .sorted()

        return unreviewedDays
    }

    suspend fun saveReview(review: Review) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).collection("reviews")
            .document(review.timestamp.toString()) // Use timestamp as ID to prevent duplicates
            .set(review)
            .await()
    }
}
