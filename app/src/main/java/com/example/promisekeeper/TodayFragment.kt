package com.example.promisekeeper

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TodayFragment : Fragment() {
    private lateinit var adapter: PromiseAdapter
    private lateinit var calendarAdapter: CalendarAdapter
    private val allPromises = mutableListOf<Promise>()
    private val displayedPromises = mutableListOf<Promise>()
    private val calendarDays = mutableListOf<CalendarDay>()
    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()
    private var selectedDate: Date = Calendar.getInstance().time

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_today, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupGreeting(view)
        setupCalendar(view)
        setupRecyclerView(view)
        setupButtons(view)
        observePromises(view)
    }

    private fun setupGreeting(view: View) {
        val tvGreeting = view.findViewById<TextView>(R.id.tvGreeting)
        val name = auth.currentUser?.displayName?.split(" ")?.firstOrNull() ?: "there"
        tvGreeting.text = getString(R.string.greeting_format, name)
    }

    private fun setupCalendar(view: View) {
        val rvCalendar = view.findViewById<RecyclerView>(R.id.rvCalendar)
        val calendar = Calendar.getInstance()
        val sdfDayName = SimpleDateFormat("EEE", Locale.getDefault())
        val sdfDayNum = SimpleDateFormat("d", Locale.getDefault())
        
        calendar.add(Calendar.DAY_OF_YEAR, -3)
        calendarDays.clear()
        for (i in 0 until 7) {
            val date = calendar.time
            val isToday = android.text.format.DateUtils.isToday(date.time)
            calendarDays.add(CalendarDay(
                date = date,
                dayName = sdfDayName.format(date),
                dayNumber = sdfDayNum.format(date),
                isSelected = isToday
            ))
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        calendarAdapter = CalendarAdapter(calendarDays) { selectedDay ->
            selectedDate = selectedDay.date
            filterPromisesAndUpdateStats(view)
        }
        
        rvCalendar.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvCalendar.adapter = calendarAdapter
        
        val todayIndex = calendarDays.indexOfFirst { it.isSelected }
        if (todayIndex != -1) {
            rvCalendar.scrollToPosition(todayIndex)
        }
    }

    private fun setupRecyclerView(view: View) {
        val rvPromises = view.findViewById<RecyclerView>(R.id.rvPromises)
        adapter = PromiseAdapter(displayedPromises) { promise ->
            showStatusUpdateDialog(promise)
        }
        rvPromises.layoutManager = LinearLayoutManager(requireContext())
        rvPromises.adapter = adapter
    }

    private fun setupButtons(view: View) {
        view.findViewById<ExtendedFloatingActionButton>(R.id.btnAddPromise).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, AddPromiseFragment())
                .addToBackStack(null)
                .commit()
        }

        view.findViewById<TextView>(R.id.btnReviewDay).setOnClickListener {
            openReviewFragment()
        }

        view.findViewById<MaterialButton>(R.id.btnCompleteDay).setOnClickListener {
            openReviewFragment()
        }
    }

    private fun openReviewFragment() {
        val reviewFragment = ReviewFragment.newInstance(selectedDate.time)
        parentFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, reviewFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun observePromises(view: View) {
        val userId = auth.currentUser?.uid ?: return
        
        db.collection("promises")
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener

                if (snapshot != null) {
                    val newList = snapshot.toObjects(Promise::class.java)
                    allPromises.clear()
                    allPromises.addAll(newList)
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
        
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = calendar.timeInMillis
        
        val filteredList = allPromises.filter { it.timestamp in startOfDay until endOfDay }
        
        displayedPromises.clear()
        displayedPromises.addAll(filteredList)
        adapter.updateData(displayedPromises)
        
        updateSummaryStats(view, filteredList)
        TransitionManager.beginDelayedTransition(view as ViewGroup)
    }

    private fun updateSummaryStats(view: View, filteredList: List<Promise>) {
        val kept = filteredList.count { it.status == PromiseStatus.KEPT }
        val broken = filteredList.count { it.status == PromiseStatus.BROKEN }
        val pending = filteredList.count { it.status == PromiseStatus.PENDING }
        val total = filteredList.size

        view.findViewById<TextView>(R.id.tvKeptCount).text = kept.toString()
        view.findViewById<TextView>(R.id.tvBrokenCount).text = broken.toString()
        view.findViewById<TextView>(R.id.tvPendingCount).text = pending.toString()

        val progress = if (total > 0) (kept * 100) / total else 0
        view.findViewById<ProgressBar>(R.id.pbSummary).progress = progress
        view.findViewById<TextView>(R.id.tvProgressPercent).text = getString(R.string.percent_format, progress)
    }

    private fun showStatusUpdateDialog(promise: Promise) {
        val options = arrayOf("Kept", "Broken", "Pending")
        AlertDialog.Builder(requireContext())
            .setTitle("Update Status")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> updatePromiseStatus(promise, PromiseStatus.KEPT)
                    1 -> updatePromiseStatus(promise, PromiseStatus.BROKEN)
                    2 -> updatePromiseStatus(promise, PromiseStatus.PENDING)
                }
            }
            .show()
    }

    private fun updatePromiseStatus(promise: Promise, status: PromiseStatus) {
        val updatedPromise = promise.copy(status = status)
        db.collection("promises").document(promise.id).set(updatedPromise)
    }
}
