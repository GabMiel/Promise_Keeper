package com.example.promisekeeper

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class HomeFragment : Fragment() {
    private lateinit var adapter: PromiseAdapter
    private lateinit var calendarAdapter: CalendarAdapter
    private val reviewRepository = ReviewRepository()
    private val allPromises = mutableListOf<Promise>()
    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()
    
    private var selectedDate: Date = Calendar.getInstance().time
    private var currentWeekStart: Calendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private var cardPendingReview: MaterialCardView? = null
    private var tvPendingReviewText: TextView? = null
    private var snapshotListener: ListenerRegistration? = null
    private var emptyStateContainer: View? = null
    private var shimmerContainer: ShimmerFrameLayout? = null
    private var tvSummaryTitle: TextView? = null
    private var rvPromises: RecyclerView? = null
    private var mainContent: ViewGroup? = null
    
    private var progressAnimator: ValueAnimator? = null
    private var isFirstLoad = true
    private var updateJob: Job? = null

    private val dateChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_DATE_CHANGED || intent?.action == Intent.ACTION_TIME_CHANGED) {
                refreshLiveUI()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        mainContent = view.findViewById(R.id.mainContent)
        rvPromises = view.findViewById(R.id.rvPromises)
        cardPendingReview = view.findViewById(R.id.cardPendingReview)
        tvPendingReviewText = view.findViewById(R.id.tvPendingReviewText)
        emptyStateContainer = view.findViewById(R.id.emptyStateContainer)
        shimmerContainer = view.findViewById(R.id.shimmerContainer)
        tvSummaryTitle = view.findViewById(R.id.tvSummaryTitle)
        
        setupGreeting(view)
        setupCalendar(view)
        setupRecyclerView(view)
        setupButtons(view)
        setupSwipeGestures(view)
        
        if (isFirstLoad) {
            shimmerContainer?.visibility = View.VISIBLE
            shimmerContainer?.startShimmer()
            rvPromises?.visibility = View.GONE
        } else {
            shimmerContainer?.visibility = View.GONE
            shimmerContainer?.stopShimmer()
            rvPromises?.visibility = View.VISIBLE
        }
        
        observePromises(view)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
        }
        requireContext().registerReceiver(dateChangedReceiver, filter)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeGestures(view: View) {
        val gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            view.findViewById<View>(R.id.ivPrevWeek).performClick()
                        } else {
                            view.findViewById<View>(R.id.ivNextWeek).performClick()
                        }
                        return true
                    }
                }
                return false
            }
        })

        view.findViewById<View>(R.id.mainContent).setOnTouchListener { v, event ->
            if (gestureDetector.onTouchEvent(event)) return@setOnTouchListener true
            if (event.action == MotionEvent.ACTION_UP) {
                v.performClick()
            }
            false
        }
    }

    private fun refreshLiveUI() {
        view?.let {
            setupGreeting(it)
            updateCalendarGrid(it)
            filterPromisesAndUpdateStats(it)
        }
    }

    private fun setupGreeting(view: View) {
        val tvGreeting = view.findViewById<TextView>(R.id.tvGreeting)
        val name = auth.currentUser?.displayName?.split(" ")?.firstOrNull() ?: "there"
        
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 0..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
        
        tvGreeting.text = "$greeting, $name!"
    }

    private fun setupCalendar(view: View) {
        val rvCalendar = view.findViewById<RecyclerView>(R.id.rvCalendar)
        val ivPrev = view.findViewById<ImageView>(R.id.ivPrevWeek)
        val ivNext = view.findViewById<ImageView>(R.id.ivNextWeek)
        val tvCurrentWeek = view.findViewById<TextView>(R.id.tvCurrentWeek)

        calendarAdapter = CalendarAdapter { selectedDay ->
            if (!isSameDay(selectedDate, selectedDay.date)) {
                selectedDate = selectedDay.date
                updateCalendarGrid(view)
                
                // Debounce the update to prevent rapid switching crashes
                updateJob?.cancel()
                updateJob = lifecycleScope.launch {
                    delay(50) // Tiny delay to catch rapid clicks
                    if (isAdded && view != null) {
                        filterPromisesAndUpdateStats(view)
                    }
                }
            }
        }
        
        rvCalendar.layoutManager = GridLayoutManager(requireContext(), 7)
        rvCalendar.adapter = calendarAdapter
        rvCalendar.itemAnimator = null 

        ivPrev?.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            currentWeekStart.add(Calendar.DAY_OF_MONTH, -7)
            updateCalendarGrid(view)
        }

        ivNext?.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            currentWeekStart.add(Calendar.DAY_OF_MONTH, 7)
            updateCalendarGrid(view)
        }

        tvCurrentWeek?.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            jumpToToday(view)
        }

        updateCalendarGrid(view)
    }

    private fun jumpToToday(view: View) {
        val now = Calendar.getInstance()
        selectedDate = now.time
        currentWeekStart.time = now.time
        currentWeekStart.set(Calendar.DAY_OF_WEEK, currentWeekStart.firstDayOfWeek)
        currentWeekStart.set(Calendar.HOUR_OF_DAY, 0)
        currentWeekStart.set(Calendar.MINUTE, 0)
        currentWeekStart.set(Calendar.SECOND, 0)
        currentWeekStart.set(Calendar.MILLISECOND, 0)
        
        updateCalendarGrid(view)
        filterPromisesAndUpdateStats(view)
    }

    private fun updateCalendarGrid(view: View) {
        val tvCurrentWeek = view.findViewById<TextView>(R.id.tvCurrentWeek)
        val weekEnd = currentWeekStart.clone() as Calendar
        weekEnd.add(Calendar.DAY_OF_MONTH, 6)
        
        val sdfMonth = SimpleDateFormat("MMM d", Locale.getDefault())
        val sdfYear = SimpleDateFormat("yyyy", Locale.getDefault())
        tvCurrentWeek?.text = "${sdfMonth.format(currentWeekStart.time)} - ${sdfMonth.format(weekEnd.time)}, ${sdfYear.format(weekEnd.time)}"

        val calendar = currentWeekStart.clone() as Calendar
        val newList = mutableListOf<CalendarDay>()
        val sdfDayNum = SimpleDateFormat("d", Locale.getDefault())
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = today.timeInMillis

        for (i in 0 until 7) {
            val date = calendar.time
            val dayStart = calendar.timeInMillis
            val dayEnd = dayStart + 86400000
            
            val dayPromises = allPromises.filter { it.timestamp in dayStart until dayEnd }
            val status = calculateDayStatus(dayPromises, startOfToday)

            newList.add(CalendarDay(
                date = date,
                dayNumber = sdfDayNum.format(date),
                isSelected = isSameDay(date, selectedDate),
                isToday = isSameDay(date, today.time),
                status = status
            ))
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        calendarAdapter.submitList(newList)
    }

    private fun calculateDayStatus(promises: List<Promise>, startOfToday: Long): DayStatus {
        if (promises.isEmpty()) return DayStatus.NONE
        
        val hasBroken = promises.any { 
            it.status == PromiseStatus.BROKEN || (it.status == PromiseStatus.PENDING && it.timestamp < startOfToday) 
        }
        
        return when {
            hasBroken -> DayStatus.HAS_BROKEN
            promises.all { it.status == PromiseStatus.KEPT } -> DayStatus.ALL_KEPT
            else -> DayStatus.NONE
        }
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun setupRecyclerView(view: View) {
        adapter = PromiseAdapter(
            onPromiseClick = { promise, _ ->
                PromiseEntryBottomSheet.newInstance(promise.id).show(childFragmentManager, "PromiseEntry")
            },
            onPromiseLongClick = { promise, itemView ->
                itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                animateItemUpdate(itemView)
                togglePromiseStatus(promise)
            }
        )
        rvPromises?.layoutManager = LinearLayoutManager(requireContext())
        rvPromises?.adapter = adapter
        rvPromises?.itemAnimator = null 
    }

    private fun animateItemUpdate(view: View) {
        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.05f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.05f, 1f)
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 300
            interpolator = OvershootInterpolator()
            start()
        }
    }

    private fun togglePromiseStatus(promise: Promise) {
        val userId = auth.currentUser?.uid ?: return
        val newStatus = if (promise.status == PromiseStatus.KEPT) PromiseStatus.PENDING else PromiseStatus.KEPT
        
        db.collection("users").document(userId).collection("promises").document(promise.id)
            .update("status", newStatus.name)
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to update promise", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupButtons(view: View) {
        view.findViewById<ExtendedFloatingActionButton>(R.id.btnAddPromise).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, AddPromiseFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun checkAutoExpirationAndPendingReviews() {
        val userId = auth.currentUser?.uid ?: return
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        db.collection("users").document(userId).collection("promises")
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val batch = db.batch()
                    var hasUpdates = false
                    snapshot.documents.forEach { doc ->
                        val status = doc.getString("status")
                        val timestamp = doc.getLong("timestamp") ?: 0
                        if (status == PromiseStatus.PENDING.name && timestamp < startOfToday) {
                            batch.update(doc.reference, "status", PromiseStatus.BROKEN.name)
                            hasUpdates = true
                        }
                    }
                    if (hasUpdates) {
                        batch.commit().addOnSuccessListener {
                            detectUnreviewedDays()
                        }
                    } else {
                        detectUnreviewedDays()
                    }
                } else {
                    detectUnreviewedDays()
                }
            }
    }

    private fun detectUnreviewedDays() {
        lifecycleScope.launch {
            val unreviewedDays = reviewRepository.getUnreviewedDays()
            if (unreviewedDays.isNotEmpty()) {
                if (!isAdded || view == null) return@launch
                mainContent?.let { TransitionManager.beginDelayedTransition(it, AutoTransition().apply { excludeTarget(rvPromises!!, true) }) }
                cardPendingReview?.visibility = View.VISIBLE
                
                val count = unreviewedDays.size
                tvPendingReviewText?.text = if (count == 1) {
                    getString(R.string.pending_review_msg)
                } else {
                    getString(R.string.pending_review_multi_msg, count)
                }
                
                cardPendingReview?.setOnClickListener {
                    val reviewFragment = DayReviewFragment.newInstance(unreviewedDays.first())
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.nav_host_fragment, reviewFragment)
                        .addToBackStack(null)
                        .commit()
                }

                delay(5000)
                if (isAdded && view != null) {
                    mainContent?.let { TransitionManager.beginDelayedTransition(it) }
                    cardPendingReview?.visibility = View.GONE
                }
            } else {
                cardPendingReview?.visibility = View.GONE
            }
        }
    }

    private fun observePromises(view: View) {
        val userId = auth.currentUser?.uid ?: return
        
        snapshotListener?.remove()
        snapshotListener = db.collection("users").document(userId).collection("promises")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null || !isAdded) return@addSnapshotListener

                if (snapshot != null) {
                    val newList = snapshot.toObjects(Promise::class.java)
                    allPromises.clear()
                    allPromises.addAll(newList)
                    
                    shimmerContainer?.stopShimmer()
                    shimmerContainer?.visibility = View.GONE
                    rvPromises?.visibility = View.VISIBLE
                    isFirstLoad = false
                    
                    checkAutoExpirationAndPendingReviews()
                    updateCalendarGrid(view)
                    filterPromisesAndUpdateStats(view)
                }
            }
    }

    private fun filterPromisesAndUpdateStats(view: View) {
        val calendar = Calendar.getInstance()
        calendar.time = selectedDate
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        val endOfDay = startOfDay + 86400000
        
        val filteredList = allPromises.filter { it.timestamp in startOfDay until endOfDay }
        
        mainContent?.let {
            TransitionManager.beginDelayedTransition(it, AutoTransition().apply { 
                duration = 150 
                excludeTarget(R.id.rvCalendar, true)
                excludeTarget(R.id.rvPromises, true)
            })
        }
        
        adapter.submitList(filteredList.map { it as Any })
        
        val sdfTitle = SimpleDateFormat("MMMM d", Locale.getDefault())
        val isToday = isSameDay(selectedDate, Calendar.getInstance().time)
        tvSummaryTitle?.text = if (isToday) getString(R.string.today_summary) else getString(R.string.summary_for_date, sdfTitle.format(selectedDate))
        
        emptyStateContainer?.visibility = if (filteredList.isEmpty() && !isFirstLoad) View.VISIBLE else View.GONE
        
        updateSummaryStats(view, filteredList)
    }

    private fun updateSummaryStats(view: View, filteredList: List<Promise>) {
        val kept = filteredList.count { it.status == PromiseStatus.KEPT }
        val broken = filteredList.count { it.status == PromiseStatus.BROKEN }
        val pending = filteredList.count { it.status == PromiseStatus.PENDING }
        val total = filteredList.size

        val tvKeptCount = view.findViewById<TextView>(R.id.tvKeptCount)
        val tvBrokenCount = view.findViewById<TextView>(R.id.tvBrokenCount)
        val tvPendingCount = view.findViewById<TextView>(R.id.tvPendingCount)
        val pbSummary = view.findViewById<ProgressBar>(R.id.pbSummary)
        val tvProgressPercent = view.findViewById<TextView>(R.id.tvProgressPercent)
        val progressContainer = view.findViewById<View>(R.id.progressContainer)

        val targetProgress = if (total > 0) ((kept.toFloat() / total) * 100).roundToInt() else 0
        val startProgress = pbSummary.progress
        val startKept = tvKeptCount.text.toString().toIntOrNull() ?: 0
        val startBroken = tvBrokenCount.text.toString().toIntOrNull() ?: 0
        val startPending = tvPendingCount.text.toString().toIntOrNull() ?: 0

        if (targetProgress > startProgress) {
            animatePop(progressContainer)
        }

        progressAnimator?.cancel()
        progressAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                val currentProgress = (startProgress + (targetProgress - startProgress) * fraction).toInt()
                pbSummary.progress = currentProgress
                tvProgressPercent.text = getString(R.string.percent_format, currentProgress)
                
                tvKeptCount.text = (startKept + (kept - startKept) * fraction).toInt().toString()
                tvBrokenCount.text = (startBroken + (broken - startBroken) * fraction).toInt().toString()
                tvPendingCount.text = (startPending + (pending - startPending) * fraction).toInt().toString()
            }
            start()
        }
    }

    private fun animatePop(view: View) {
        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.15f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.15f, 1f)
        
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 400
            interpolator = OvershootInterpolator()
            start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        updateJob?.cancel()
        progressAnimator?.cancel()
        try {
            requireContext().unregisterReceiver(dateChangedReceiver)
        } catch (e: Exception) {}
        snapshotListener?.remove()
        snapshotListener = null
        cardPendingReview = null
        tvPendingReviewText = null
        emptyStateContainer = null
        shimmerContainer = null
        tvSummaryTitle = null
        rvPromises = null
        mainContent = null
    }
}
