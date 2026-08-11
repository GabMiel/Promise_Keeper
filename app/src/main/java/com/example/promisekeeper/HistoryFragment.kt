package com.example.promisekeeper

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.example.promisekeeper.databinding.FragmentHistoryBinding
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
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
    private var searchQuery: String = ""
    private var snapshotListener: ListenerRegistration? = null

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
        setupSearch()
        fetchHistory()
        updateChipStyles()
    }

    private fun setupInitialFilter() {
        val initialStatus = arguments?.getString(ARG_INITIAL_STATUS)
        if (initialStatus != null) {
            when (initialStatus.uppercase()) {
                PromiseStatus.KEPT.name -> binding.chipKept.isChecked = true
                PromiseStatus.BROKEN.name -> binding.chipBroken.isChecked = true
                PromiseStatus.PENDING.name -> binding.chipPending.isChecked = true
                else -> binding.chipAll.isChecked = true
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter { dayItem ->
            val reviewFragment = DayReviewFragment.newInstance(dayItem.timestamp)
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.nav_host_fragment, reviewFragment)
                .addToBackStack(null)
                .commit()
        }
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = adapter
    }

    private fun setupFilters() {
        binding.cgFilters.setOnCheckedStateChangeListener { _, _ ->
            updateChipStyles()
            processAndDisplayData()
        }
    }

    private fun setupSearch() {
        binding.ivSearch.setOnClickListener {
            TransitionManager.beginDelayedTransition(binding.root as ViewGroup, AutoTransition().setDuration(250))
            if (binding.cardSearch.isVisible) {
                binding.cardSearch.isVisible = false
                binding.etSearch.text?.clear()
                hideKeyboard()
            } else {
                binding.cardSearch.isVisible = true
                binding.etSearch.requestFocus()
                showKeyboard()
            }
        }

        binding.ivCloseSearch.setOnClickListener {
            TransitionManager.beginDelayedTransition(binding.root as ViewGroup, AutoTransition().setDuration(250))
            binding.etSearch.text?.clear()
            binding.cardSearch.isVisible = false
            hideKeyboard()
        }

        binding.etSearch.addTextChangedListener { text ->
            searchQuery = text.toString()
            processAndDisplayData()
        }
    }

    private fun showKeyboard() {
        binding.etSearch.post {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    private fun updateChipStyles() {
        val chips = listOf(
            binding.chipAll to R.color.accent_red,
            binding.chipKept to R.color.status_kept,
            binding.chipBroken to R.color.status_broken,
            binding.chipPending to R.color.status_pending
        )

        chips.forEach { (chip, activeColorRes) ->
            val isChecked = chip.isChecked
            val activeColor = ContextCompat.getColor(requireContext(), activeColorRes)
            
            if (isChecked) {
                chip.chipBackgroundColor = ColorStateList.valueOf(activeColor)
                chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                chip.chipStrokeWidth = 0f
            } else {
                chip.chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
                chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_gray))
                chip.chipStrokeColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.light_gray))
                chip.chipStrokeWidth = 1f * resources.displayMetrics.density
            }
        }
    }

    private fun fetchHistory() {
        val userId = auth.currentUser?.uid ?: return
        
        snapshotListener?.remove()
        snapshotListener = db.collection("users").document(userId).collection("promises")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null || _binding == null || !isAdded) return@addSnapshotListener
                
                if (snapshot != null) {
                    allPromises = snapshot.toObjects(Promise::class.java).mapIndexed { index, promise ->
                        promise.copy(id = snapshot.documents[index].id)
                    }
                    processAndDisplayData()
                }
            }
    }

    private fun processAndDisplayData() {
        if (_binding == null) return
        
        val sdfHeader = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val sdfDate = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        val sdfGroupKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        var filteredPromises = allPromises
        if (searchQuery.isNotBlank()) {
            filteredPromises = filteredPromises.filter { it.description.contains(searchQuery, ignoreCase = true) }
        }

        val groupedByDay = filteredPromises.groupBy { 
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
            
            val reflectionSnippet = promises
                .firstOrNull { !it.improvementPlan.isNullOrBlank() }
                ?.improvementPlan
            
            val percent = if (total > 0) {
                ((kept.toDouble() / total) * 100).roundToInt()
            } else 0

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
                    hasReflection = reflectionSnippet != null,
                    reflectionSnippet = reflectionSnippet
                ))
            }
        }
        
        adapter.submitList(historyItems)
        
        val isEmpty = historyItems.isEmpty()
        binding.layoutEmpty.isVisible = isEmpty
        binding.rvHistory.isVisible = !isEmpty
        
        if (isEmpty) {
            if (searchQuery.isNotBlank()) {
                binding.ivEmptyState.setImageResource(android.R.drawable.ic_menu_search)
                binding.tvEmptyState.text = getString(R.string.no_search_history_results, searchQuery)
            } else {
                binding.ivEmptyState.setImageResource(R.drawable.ic_diamond) // Or appropriate empty history icon
                binding.tvEmptyState.text = getString(R.string.no_history_found)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        snapshotListener?.remove()
        snapshotListener = null
        _binding = null
    }
}
