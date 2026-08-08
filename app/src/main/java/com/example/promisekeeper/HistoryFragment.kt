package com.example.promisekeeper

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.promisekeeper.databinding.FragmentHistoryBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: HistoryAdapter
    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()
    
    private var allPromises = listOf<Promise>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFilters()
        fetchHistory()
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter { dayItem ->
            // TODO: Navigate to day detail view
        }
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = adapter
    }

    private fun setupFilters() {
        binding.cgFilters.setOnCheckedStateChangeListener { _, _ ->
            processAndDisplayData()
        }
    }

    private fun fetchHistory() {
        val userId = auth.currentUser?.uid ?: return
        
        db.collection("promises")
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                
                if (snapshot != null) {
                    allPromises = snapshot.toObjects(Promise::class.java)
                    processAndDisplayData()
                }
            }
    }

    private fun processAndDisplayData() {
        val sdfHeader = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val sdfDate = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        val sdfGroupKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        // Group promises by day. SortedMap is not strictly needed if Firestore returns ordered data,
        // but groupBy preserves order of keys as they appear in the source list.
        val groupedByDay = allPromises.groupBy { 
            sdfGroupKey.format(Date(it.timestamp)) 
        }

        val historyItems = mutableListOf<HistoryItem>()
        var lastMonthHeader = ""
        val selectedFilterId = binding.cgFilters.checkedChipId

        groupedByDay.forEach { (_, promises) ->
            val date = Date(promises.first().timestamp)
            val monthHeader = sdfHeader.format(date)
            
            val kept = promises.count { it.status == PromiseStatus.KEPT }
            val broken = promises.count { it.status == PromiseStatus.BROKEN }
            val pending = promises.count { it.status == PromiseStatus.PENDING }
            val total = promises.size
            
            // Calculate percentage with rounding
            val percent = if (total > 0) {
                ((kept.toDouble() / total) * 100).roundToInt()
            } else 0

            // Apply filters: Show the day if it has at least one promise matching the filter type
            val matchesFilter = when (selectedFilterId) {
                R.id.chipKept -> kept > 0
                R.id.chipBroken -> broken > 0
                R.id.chipPending -> pending > 0
                else -> true
            }

            if (matchesFilter) {
                if (monthHeader != lastMonthHeader) {
                    historyItems.add(HistoryItem.Header(monthHeader))
                    lastMonthHeader = monthHeader
                }
                historyItems.add(HistoryItem.Day(
                    date = sdfDate.format(date),
                    percentage = percent,
                    kept = kept,
                    broken = broken,
                    pending = pending,
                    timestamp = date.time
                ))
            }
        }
        
        adapter.submitList(historyItems)
        binding.tvEmptyState.visibility = if (historyItems.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

sealed class HistoryItem {
    data class Header(val month: String) : HistoryItem()
    data class Day(
        val date: String,
        val percentage: Int,
        val kept: Int,
        val broken: Int,
        val pending: Int,
        val timestamp: Long
    ) : HistoryItem()
}
