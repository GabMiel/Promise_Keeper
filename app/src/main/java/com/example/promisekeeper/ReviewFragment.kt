package com.example.promisekeeper

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReviewFragment : Fragment() {
    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()
    private var selectedDate: Date = Calendar.getInstance().time
    
    private lateinit var tabLayout: TabLayout
    private lateinit var tvDate: TextView
    private lateinit var cgFailureTags: ChipGroup
    private lateinit var etReason: TextInputEditText
    private lateinit var etImprovement: TextInputEditText
    private lateinit var btnSaveReview: MaterialButton
    
    private lateinit var ivStatusIcon: ImageView
    private lateinit var tvPromiseDescription: TextView
    private lateinit var tvStatusLabel: TextView
    
    private lateinit var headerCard: MaterialCardView
    private lateinit var cvHeaderIcon: MaterialCardView
    private lateinit var ivHeaderIcon: ImageView
    private lateinit var tvHeaderTitle: TextView
    private lateinit var tvHeaderSubtitle: TextView
    private lateinit var feedbackContainer: View
    
    private var brokenPromises = mutableListOf<Promise>()
    private var keptPromises = mutableListOf<Promise>()
    private var pendingPromises = mutableListOf<Promise>()
    private var currentlyReviewing: Promise? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_review, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val dateMillis = arguments?.getLong("selected_date") ?: System.currentTimeMillis()
        selectedDate = Date(dateMillis)
        
        initViews(view)
        setupDate()
        loadPromises()
        
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        
        btnSaveReview.setOnClickListener {
            saveReview()
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
        cgFailureTags = view.findViewById(R.id.cgFailureTags)
        etReason = view.findViewById(R.id.etReason)
        etImprovement = view.findViewById(R.id.etImprovement)
        btnSaveReview = view.findViewById(R.id.btnSaveReview)
        
        ivStatusIcon = view.findViewById(R.id.ivStatusIcon)
        tvPromiseDescription = view.findViewById(R.id.tvPromiseDescription)
        tvStatusLabel = view.findViewById(R.id.tvStatusLabel)

        headerCard = view.findViewById(R.id.headerCard)
        cvHeaderIcon = view.findViewById(R.id.cvHeaderIcon)
        ivHeaderIcon = view.findViewById(R.id.ivHeaderIcon)
        tvHeaderTitle = view.findViewById(R.id.tvHeaderTitle)
        tvHeaderSubtitle = view.findViewById(R.id.tvHeaderSubtitle)
        feedbackContainer = view.findViewById(R.id.feedbackContainer)
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

        db.collection("promises")
            .whereEqualTo("userId", userId)
            .whereGreaterThanOrEqualTo("timestamp", startOfDay)
            .whereLessThan("timestamp", endOfDay)
            .get()
            .addOnSuccessListener { snapshot ->
                val promises = snapshot.toObjects(Promise::class.java)
                
                keptPromises = promises.filter { it.status == PromiseStatus.KEPT }.toMutableList()
                brokenPromises = promises.filter { it.status == PromiseStatus.BROKEN }.toMutableList()
                pendingPromises = promises.filter { it.status == PromiseStatus.PENDING }.toMutableList()
                
                updateTabCounts()
                updateHeaderCard()
                
                // Select default tab: Broken if failures exist, otherwise Kept, otherwise Pending
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
            // Dynamic Reflection Header for Broken Promises
            tvHeaderTitle.text = getString(R.string.review_reflect_title)
            tvHeaderSubtitle.text = getString(R.string.review_reflect_subtitle)
            ivHeaderIcon.setImageResource(R.drawable.ic_promise_broken)
            ivHeaderIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.status_broken))
            cvHeaderIcon.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.accent_red_light))
        } else {
            // Standard Success Header
            tvHeaderTitle.text = getString(R.string.review_success_title)
            tvHeaderSubtitle.text = getString(R.string.review_success_subtitle)
            ivHeaderIcon.setImageResource(R.drawable.ic_check)
            ivHeaderIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.status_kept))
            cvHeaderIcon.setCardBackgroundColor(Color.parseColor("#E8F5E9")) // Light Green
        }
    }

    private fun updateUIForSelectedTab() {
        val selectedTab = tabLayout.selectedTabPosition
        
        // Update Tab Colors dynamically based on status
        val statusColor = when (selectedTab) {
            0 -> ContextCompat.getColor(requireContext(), R.color.status_kept)
            1 -> ContextCompat.getColor(requireContext(), R.color.status_broken)
            else -> ContextCompat.getColor(requireContext(), R.color.status_pending)
        }
        tabLayout.setSelectedTabIndicatorColor(statusColor)
        tabLayout.setTabTextColors(ContextCompat.getColor(requireContext(), R.color.text_gray), statusColor)

        val promise = when (selectedTab) {
            0 -> keptPromises.firstOrNull()
            1 -> brokenPromises.firstOrNull()
            else -> pendingPromises.firstOrNull()
        }
        
        currentlyReviewing = promise
        
        if (promise != null) {
            tvPromiseDescription.text = promise.description
            
            when (promise.status) {
                PromiseStatus.KEPT -> {
                    tvStatusLabel.text = getString(R.string.kept)
                    ivStatusIcon.setImageResource(R.drawable.ic_promise_kept)
                    ivStatusIcon.setColorFilter(statusColor)
                    tvStatusLabel.setTextColor(statusColor)
                    feedbackContainer.visibility = View.GONE
                }
                PromiseStatus.BROKEN -> {
                    tvStatusLabel.text = getString(R.string.broken)
                    ivStatusIcon.setImageResource(R.drawable.ic_promise_broken)
                    ivStatusIcon.setColorFilter(statusColor)
                    tvStatusLabel.setTextColor(statusColor)
                    showFeedbackForm(promise)
                }
                PromiseStatus.PENDING -> {
                    tvStatusLabel.text = getString(R.string.pending)
                    ivStatusIcon.setImageResource(R.drawable.ic_promise_pending)
                    ivStatusIcon.setColorFilter(statusColor)
                    tvStatusLabel.setTextColor(statusColor)
                    feedbackContainer.visibility = View.GONE
                }
            }
        } else {
            tvPromiseDescription.text = "No promises in this category"
            tvStatusLabel.text = ""
            feedbackContainer.visibility = View.GONE
        }
    }

    private fun showFeedbackForm(promise: Promise) {
        feedbackContainer.visibility = View.VISIBLE
        etReason.setText(promise.reasonForFailure)
        etImprovement.setText(promise.improvementPlan)
        
        cgFailureTags.clearCheck()
        promise.failureTag?.let { tag ->
            for (i in 0 until cgFailureTags.childCount) {
                val chip = cgFailureTags.getChildAt(i) as Chip
                if (chip.text.toString() == tag) {
                    chip.isChecked = true
                    break
                }
            }
        }
    }

    private fun saveReview() {
        val promise = currentlyReviewing ?: return
        if (promise.status != PromiseStatus.BROKEN) return

        val selectedChipId = cgFailureTags.checkedChipId
        val failureTag = if (selectedChipId != View.NO_ID) {
            view?.findViewById<Chip>(selectedChipId)?.text.toString()
        } else null
        
        val updatedPromise = promise.copy(
            failureTag = failureTag,
            reasonForFailure = etReason.text.toString(),
            improvementPlan = etImprovement.text.toString()
        )

        db.collection("promises").document(promise.id).set(updatedPromise)
            .addOnSuccessListener {
                Toast.makeText(context, "Review saved!", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
    }

    companion object {
        fun newInstance(date: Long): ReviewFragment {
            return ReviewFragment().apply {
                arguments = Bundle().apply { putLong("selected_date", date) }
            }
        }
    }
}
