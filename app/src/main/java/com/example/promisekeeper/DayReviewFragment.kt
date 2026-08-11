package com.example.promisekeeper

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DayReviewFragment : Fragment() {
    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()
    private var selectedDate: Date = Calendar.getInstance().time
    
    private lateinit var tabLayout: TabLayout
    private lateinit var tvDate: TextView
    private lateinit var rvDayPromises: RecyclerView
    private lateinit var adapter: PromiseAdapter
    
    private lateinit var headerCard: MaterialCardView
    private lateinit var cvHeaderIcon: MaterialCardView
    private lateinit var ivHeaderIcon: ImageView
    private lateinit var tvHeaderTitle: TextView
    private lateinit var tvHeaderSubtitle: TextView
    
    private lateinit var layoutEmpty: View
    private lateinit var ivEmptyIcon: ImageView
    private lateinit var tvEmptyMessage: TextView

    private var brokenPromises = mutableListOf<Promise>()
    private var keptPromises = mutableListOf<Promise>()
    private var pendingPromises = mutableListOf<Promise>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_day_review, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val dateMillis = arguments?.getLong("selected_date") ?: System.currentTimeMillis()
        selectedDate = Date(dateMillis)
        
        initViews(view)
        setupRecyclerView()
        setupDate()
        loadPromises()
        
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                updateUIForSelectedTab()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun initViews(view: View) {
        tabLayout = view.findViewById(R.id.tabLayout)
        tvDate = view.findViewById(R.id.tvDate)
        rvDayPromises = view.findViewById(R.id.rvDayPromises)
        headerCard = view.findViewById(R.id.headerCard)
        cvHeaderIcon = view.findViewById(R.id.cvHeaderIcon)
        ivHeaderIcon = view.findViewById(R.id.ivHeaderIcon)
        tvHeaderTitle = view.findViewById(R.id.tvHeaderTitle)
        tvHeaderSubtitle = view.findViewById(R.id.tvHeaderSubtitle)
        
        layoutEmpty = view.findViewById(R.id.layoutEmpty)
        ivEmptyIcon = view.findViewById(R.id.ivEmptyIcon)
        tvEmptyMessage = view.findViewById(R.id.tvEmptyMessage)
    }

    private fun setupRecyclerView() {
        adapter = PromiseAdapter(
            onPromiseClick = { promise, _ ->
                ViewPromiseBottomSheet.newInstance(promise.id).show(childFragmentManager, "ViewPromise")
            }
        )
        rvDayPromises.layoutManager = LinearLayoutManager(requireContext())
        rvDayPromises.adapter = adapter
    }

    private fun setupDate() {
        val sdf = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        tvDate.text = sdf.format(selectedDate)
    }

    private fun loadPromises() {
        val userId = auth.currentUser?.uid ?: return
        
        val calendar = Calendar.getInstance()
        calendar.time = selectedDate
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        val endOfDay = startOfDay + 86400000

        db.collection("users").document(userId).collection("promises")
            .whereGreaterThanOrEqualTo("timestamp", startOfDay)
            .whereLessThan("timestamp", endOfDay)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener
                val promises = snapshot.toObjects(Promise::class.java).mapIndexed { index, promise ->
                    promise.copy(id = snapshot.documents[index].id)
                }
                
                keptPromises = promises.filter { it.status == PromiseStatus.KEPT }.toMutableList()
                brokenPromises = promises.filter { it.status == PromiseStatus.BROKEN }.toMutableList()
                pendingPromises = promises.filter { it.status == PromiseStatus.PENDING }.toMutableList()
                
                updateTabCounts()
                updateHeaderCard()
                
                if (brokenPromises.isNotEmpty()) {
                    tabLayout.getTabAt(1)?.select()
                } else if (keptPromises.isNotEmpty()) {
                    tabLayout.getTabAt(0)?.select()
                } else {
                    tabLayout.getTabAt(2)?.select()
                }
                updateUIForSelectedTab()
            }
    }

    private fun updateTabCounts() {
        tabLayout.getTabAt(0)?.text = getString(R.string.kept_count, keptPromises.size)
        tabLayout.getTabAt(1)?.text = getString(R.string.broken_count, brokenPromises.size)
        tabLayout.getTabAt(2)?.text = getString(R.string.pending_count, pendingPromises.size)
    }

    private fun updateHeaderCard() {
        if (brokenPromises.isNotEmpty()) {
            tvHeaderTitle.text = getString(R.string.review_reflect_title)
            tvHeaderSubtitle.text = getString(R.string.review_reflect_subtitle)
            ivHeaderIcon.setImageResource(R.drawable.ic_promise_broken)
            ivHeaderIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.status_broken))
            cvHeaderIcon.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.accent_red_light))
        } else {
            tvHeaderTitle.text = getString(R.string.review_success_title)
            tvHeaderSubtitle.text = getString(R.string.review_success_subtitle)
            ivHeaderIcon.setImageResource(R.drawable.ic_check)
            ivHeaderIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.status_kept))
            cvHeaderIcon.setCardBackgroundColor(Color.parseColor("#E8F5E9"))
        }
    }

    private fun updateUIForSelectedTab() {
        val selectedTab = tabLayout.selectedTabPosition
        val statusColor = when (selectedTab) {
            0 -> ContextCompat.getColor(requireContext(), R.color.status_kept)
            1 -> ContextCompat.getColor(requireContext(), R.color.status_broken)
            else -> ContextCompat.getColor(requireContext(), R.color.status_pending)
        }
        tabLayout.setSelectedTabIndicatorColor(statusColor)
        tabLayout.setTabTextColors(ContextCompat.getColor(requireContext(), R.color.text_gray), statusColor)

        val currentList = when (selectedTab) {
            0 -> keptPromises
            1 -> brokenPromises
            else -> pendingPromises
        }
        
        adapter.submitList(currentList.map { it as Any })
        
        val isEmpty = currentList.isEmpty()
        rvDayPromises.isVisible = !isEmpty
        layoutEmpty.isVisible = isEmpty
        
        if (isEmpty) {
            val (iconRes, messageRes) = when (selectedTab) {
                0 -> R.drawable.ic_promise_kept to R.string.no_promises_category_kept
                1 -> R.drawable.ic_promise_broken to R.string.no_promises_category_broken
                else -> R.drawable.ic_promise_pending to R.string.no_promises_category_pending
            }
            ivEmptyIcon.setImageResource(iconRes)
            ivEmptyIcon.setColorFilter(statusColor)
            tvEmptyMessage.text = getString(messageRes)
        }
    }

    companion object {
        fun newInstance(date: Long): DayReviewFragment {
            return DayReviewFragment().apply {
                arguments = Bundle().apply { putLong("selected_date", date) }
            }
        }
    }
}
