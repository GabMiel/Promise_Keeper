package com.example.promisekeeper

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ViewPromiseBottomSheet : BottomSheetDialogFragment() {

    private var promiseId: String? = null
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        fun newInstance(promiseId: String): ViewPromiseBottomSheet {
            val fragment = ViewPromiseBottomSheet()
            val args = Bundle()
            args.putString("promise_id", promiseId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_view_promise, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        promiseId = arguments?.getString("promise_id")

        view.findViewById<MaterialButton>(R.id.btnClose).setOnClickListener {
            dismiss()
        }

        if (promiseId != null) {
            loadPromiseData(view)
        }
    }

    private fun loadPromiseData(view: View) {
        val userId = auth.currentUser?.uid ?: return
        val id = promiseId ?: return
        
        db.collection("users").document(userId).collection("promises").document(id).get()
            .addOnSuccessListener { doc ->
                if (!isAdded) return@addOnSuccessListener
                
                val promise = doc.toObject(Promise::class.java)
                if (promise != null) {
                    displayPromise(view, promise)
                }
            }
            .addOnFailureListener {
                if (isAdded) {
                    Toast.makeText(context, "Error loading promise", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun displayPromise(view: View, promise: Promise) {
        view.findViewById<TextView>(R.id.tvPromise).text = promise.description
        
        val tvCategory = view.findViewById<TextView>(R.id.tvCategoryName)
        val ivCategory = view.findViewById<ImageView>(R.id.ivCategoryIcon)
        val ivWatermark = view.findViewById<ImageView>(R.id.ivCategoryWatermark)
        val layoutCategory = view.findViewById<View>(R.id.layoutCategoryView)
        
        val categoryIcon = when (promise.category.lowercase()) {
            "study" -> R.drawable.ic_study
            "health" -> R.drawable.ic_health
            "personal" -> R.drawable.ic_personal
            else -> R.drawable.ic_other
        }
        
        ivCategory.setImageResource(categoryIcon)
        ivWatermark.setImageResource(categoryIcon)
        tvCategory.text = promise.category

        val statusColorRes = when (promise.status) {
            PromiseStatus.KEPT -> R.color.status_kept
            PromiseStatus.BROKEN -> R.color.status_broken
            PromiseStatus.PENDING -> R.color.status_pending
        }
        val statusColor = ContextCompat.getColor(requireContext(), statusColorRes)
        
        ivCategory.setColorFilter(statusColor)
        tvCategory.setTextColor(statusColor)
        layoutCategory.background.setTint(statusColor.withAlpha(25))

        val tvStatus = view.findViewById<TextView>(R.id.tvStatusBadge)
        val cvStatus = view.findViewById<MaterialCardView>(R.id.cvStatusBadge)
        tvStatus.text = promise.status.name
        tvStatus.setTextColor(statusColor)
        cvStatus.setCardBackgroundColor(statusColor.withAlpha(25))

        val tvNotes = view.findViewById<TextView>(R.id.tvNotes)
        val labelNotes = view.findViewById<View>(R.id.labelNotes)
        if (!promise.notes.isNullOrBlank()) {
            tvNotes.text = promise.notes
            tvNotes.visibility = View.VISIBLE
            labelNotes.visibility = View.VISIBLE
        } else {
            tvNotes.visibility = View.GONE
            labelNotes.visibility = View.GONE
        }

        val layoutReminder = view.findViewById<View>(R.id.layoutReminder)
        val labelReminder = view.findViewById<View>(R.id.labelReminder)
        if (!promise.reminderTime.isNullOrBlank()) {
            view.findViewById<TextView>(R.id.tvReminderTime).text = getString(R.string.reminder_today_format, promise.reminderTime)
            layoutReminder.visibility = View.VISIBLE
            labelReminder.visibility = View.VISIBLE
        } else {
            layoutReminder.visibility = View.GONE
            labelReminder.visibility = View.GONE
        }

        val layoutBroken = view.findViewById<View>(R.id.layoutBrokenReason)
        if (promise.status == PromiseStatus.BROKEN && (!promise.reasonForFailure.isNullOrBlank() || !promise.improvementPlan.isNullOrBlank())) {
            layoutBroken.visibility = View.VISIBLE
            val reasonText = StringBuilder()
            if (!promise.reasonForFailure.isNullOrBlank()) {
                reasonText.append("Reason: ${promise.reasonForFailure}")
            }
            if (!promise.improvementPlan.isNullOrBlank()) {
                if (reasonText.isNotEmpty()) reasonText.append("\n\n")
                reasonText.append("Plan: ${promise.improvementPlan}")
            }
            view.findViewById<TextView>(R.id.tvBrokenReason).text = reasonText.toString()
        } else {
            layoutBroken.visibility = View.GONE
        }
    }

    private fun Int.withAlpha(alpha: Int): Int {
        return (this and 0x00FFFFFF) or (alpha shl 24)
    }
}
