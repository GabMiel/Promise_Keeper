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

    companion object {
        private const val ARG_INITIAL_STATUS = "initial_status"

        fun newInstance(status: String? = null): HistoryFragment {
            val fragment = HistoryFragment()
            val args = Bundle()
            args.putString(ARG_INITIAL_STATUS, status)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupInitialFilter()
        setupRecyclerView()
        setupFilters()
        fetchHistory()
    }

    private fun setupInitialFilter() {
        val initialStatus = arguments?.getString(ARG_INITIAL_STATUS)
        if (initialStatus != null) {
            when (initialStatus.uppercase()) {
                PromiseStatus.KEPT.name -> binding.chipKept.isChecked = true
                PromiseStatus.BROKEN.name -> binding.chipBroken.isChecked = true
                PromiseStatus.PENDING.name -> binding.chipPending.isChecked = true
                "REFLECTIONS", "REVIEWS" -> binding.chipReviews.isChecked = true
                else -> binding.chipAll.isChecked = true
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter { dayItem ->
            // Navigate to Review view for that day to view the reflection
            val reviewFragment = ReviewFragment.newInstance(dayItem.timestamp)
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, reviewFragment)
                .addToBackStack(null)
                .commit()
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
        
        db.collection("users").document(userId).collection("promises")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                
                if (snapshot != null) {
                    allPromises = snapshot.toObjects(Promise::class.java).mapIndexed { index, promise ->
                        promise.copy(id = snapshot.documents[index].id)
                    }
                    processAndDisplayData()
                }
            }
    }

    private fun processAndDisplayData() {
        val sdfHeader = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val sdfDate = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        val sdfGroupKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

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
            
            // Extract the first meaningful reflection found for the day
            val reflectionSnippet = promises
                .firstOrNull { !it.improvementPlan.isNullOrBlank() }
                ?.improvementPlan
            
            val hasReflections = reflectionSnippet != null
            
            val percent = if (total > 0) {
                ((kept.toDouble() / total) * 100).roundToInt()
            } else 0

            val matchesFilter = when (selectedFilterId) {
                R.id.chipKept -> kept > 0
                R.id.chipBroken -> broken > 0
                R.id.chipPending -> pending > 0
                R.id.chipReviews -> hasReflections
                else -> true
            }

            if (matchesFilter) {
                if (monthHeader != lastMonthHeader) {
                    historyItems.add(HistoryItem.Header(monthHeader))
                    lastMonthHeader = monthHeader
                }
                
                // Calculate start of day timestamp for navigation consistency
                val cal = Calendar.getInstance().apply { time = date }
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val dayStartTimestamp = cal.timeInMillis

                historyItems.add(HistoryItem.Day(
                    date = sdfDate.format(date),
                    percentage = percent,
                    kept = kept,
                    broken = broken,
                    pending = pending,
                    timestamp = dayStartTimestamp,
                    hasReflection = hasReflections,
                    reflectionSnippet = reflectionSnippet
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
