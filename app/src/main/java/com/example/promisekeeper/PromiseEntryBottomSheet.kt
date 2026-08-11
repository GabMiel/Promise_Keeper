package com.example.promisekeeper

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PromiseEntryBottomSheet : BottomSheetDialogFragment() {

    private var promiseId: String? = null
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_promise_entry, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        promiseId = arguments?.getString("promise_id")

        if (promiseId != null) {
            loadPromiseDetails(view)
        }

        setupButtons(view)
    }

    private fun loadPromiseDetails(view: View) {
        val userId = auth.currentUser?.uid ?: return
        val id = promiseId ?: return
        
        val pbLoading = view.findViewById<ProgressBar>(R.id.pbLoading)
        val layoutContent = view.findViewById<View>(R.id.layoutContent)

        pbLoading.visibility = View.VISIBLE
        layoutContent.visibility = View.GONE

        db.collection("users").document(userId).collection("promises").document(id).get()
            .addOnSuccessListener { doc ->
                if (!isAdded || getView() == null) return@addOnSuccessListener

                val promise = doc.toObject(Promise::class.java)
                if (promise == null) {
                    Toast.makeText(context, "Promise not found", Toast.LENGTH_SHORT).show()
                    dismiss()
                    return@addOnSuccessListener
                }

                TransitionManager.beginDelayedTransition(view as ViewGroup, AutoTransition().setDuration(250))
                
                view.findViewById<TextView>(R.id.tvDetailDescription).text = promise.description
                view.findViewById<TextView>(R.id.tvDetailCategory).text = promise.category
                
                val timeText = if (!promise.reminderTime.isNullOrBlank()) "${promise.reminderTime} Today" else "No reminder set"
                view.findViewById<TextView>(R.id.tvDetailTime).text = timeText

                val tvNotes = view.findViewById<TextView>(R.id.tvDetailNotes)
                if (!promise.notes.isNullOrBlank()) {
                    tvNotes.visibility = View.VISIBLE
                    tvNotes.text = promise.notes
                } else {
                    tvNotes.visibility = View.GONE
                }

                if (promise.status == PromiseStatus.BROKEN) {
                    view.findViewById<View>(R.id.layoutBrokenDetails).visibility = View.VISIBLE
                    view.findViewById<EditText>(R.id.etBrokenReason).setText(promise.reasonForFailure)
                    view.findViewById<EditText>(R.id.etImprovementPlan).setText(promise.improvementPlan)
                    
                    val cgFailureTags = view.findViewById<ChipGroup>(R.id.cgFailureTags)
                    for (i in 0 until cgFailureTags.childCount) {
                        val chip = cgFailureTags.getChildAt(i) as Chip
                        if (chip.text.toString() == promise.failureTag) {
                            chip.isChecked = true
                            break
                        }
                    }
                }

                applyCategoryColor(view, promise.category)
                highlightActiveStatus(view, promise.status)

                pbLoading.visibility = View.GONE
                layoutContent.visibility = View.VISIBLE
            }
            .addOnFailureListener {
                if (isAdded) {
                    Toast.makeText(context, "Error loading promise", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            }
    }

    private fun highlightActiveStatus(view: View, status: PromiseStatus) {
        val btnKept = view.findViewById<View>(R.id.btnModalKept)
        val btnBroken = view.findViewById<View>(R.id.btnModalBroken)
        val btnPending = view.findViewById<View>(R.id.btnModalPending)

        btnKept.alpha = if (status == PromiseStatus.KEPT) 1.0f else 0.5f
        btnBroken.alpha = if (status == PromiseStatus.BROKEN) 1.0f else 0.5f
        btnPending.alpha = if (status == PromiseStatus.PENDING) 1.0f else 0.5f
    }

    private fun applyCategoryColor(view: View, category: String) {
        val colorRes = when (category.lowercase()) {
            "study" -> R.color.status_pending
            "health" -> R.color.status_kept
            "personal" -> R.color.accent_red
            else -> R.color.text_gray
        }
        val colorInt = ContextCompat.getColor(requireContext(), colorRes)
        view.findViewById<MaterialCardView>(R.id.cvCategoryTag).setCardBackgroundColor(colorInt.withAlpha(25))
        view.findViewById<TextView>(R.id.tvDetailCategory).setTextColor(colorInt)
    }

    private fun Int.withAlpha(alpha: Int): Int {
        return (this and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun setupButtons(view: View) {
        val btnKept = view.findViewById<View>(R.id.btnModalKept)
        val btnBroken = view.findViewById<View>(R.id.btnModalBroken)
        val btnPending = view.findViewById<View>(R.id.btnModalPending)
        
        val layoutKeptDetails = view.findViewById<View>(R.id.layoutKeptDetails)
        val layoutPendingDetails = view.findViewById<View>(R.id.layoutPendingDetails)
        val layoutBrokenDetails = view.findViewById<View>(R.id.layoutBrokenDetails)
        
        val etReason = view.findViewById<EditText>(R.id.etBrokenReason)
        val etImprovement = view.findViewById<EditText>(R.id.etImprovementPlan)
        val cgFailureTags = view.findViewById<ChipGroup>(R.id.cgFailureTags)
        
        val btnConfirmKept = view.findViewById<View>(R.id.btnConfirmKept)
        val btnConfirmPending = view.findViewById<View>(R.id.btnConfirmPending)
        val btnConfirmBroken = view.findViewById<View>(R.id.btnConfirmBroken)
        
        val btnEdit = view.findViewById<View>(R.id.btnEditPromise)
        val btnDelete = view.findViewById<View>(R.id.btnDeletePromise)
        val layoutContent = view.findViewById<ViewGroup>(R.id.layoutContent)

        btnKept.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            TransitionManager.beginDelayedTransition(layoutContent, AutoTransition().setDuration(300))
            
            layoutKeptDetails.visibility = View.VISIBLE
            layoutPendingDetails.visibility = View.GONE
            layoutBrokenDetails.visibility = View.GONE
            
            highlightActiveStatus(view, PromiseStatus.KEPT)
        }
        
        btnPending.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            TransitionManager.beginDelayedTransition(layoutContent, AutoTransition().setDuration(300))
            
            layoutPendingDetails.visibility = View.VISIBLE
            layoutKeptDetails.visibility = View.GONE
            layoutBrokenDetails.visibility = View.GONE
            
            highlightActiveStatus(view, PromiseStatus.PENDING)
        }

        btnBroken.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            TransitionManager.beginDelayedTransition(layoutContent, AutoTransition().setDuration(300))
            
            layoutBrokenDetails.visibility = View.VISIBLE
            layoutKeptDetails.visibility = View.GONE
            layoutPendingDetails.visibility = View.GONE
            
            highlightActiveStatus(view, PromiseStatus.BROKEN)
            
            view.post {
                val scrollView = view as? androidx.core.widget.NestedScrollView
                scrollView?.smoothScrollTo(0, layoutBrokenDetails.top)
            }
        }

        btnConfirmKept.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            updateStatus(PromiseStatus.KEPT)
        }

        btnConfirmPending.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            updateStatus(PromiseStatus.PENDING)
        }

        btnConfirmBroken.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            
            val selectedChipId = cgFailureTags.checkedChipId
            val failureTag = if (selectedChipId != View.NO_ID) {
                view.findViewById<Chip>(selectedChipId).text.toString()
            } else null
            
            val reason = etReason.text.toString()
            val improvement = etImprovement.text.toString()
            
            updateStatus(PromiseStatus.BROKEN, failureTag, reason, improvement)
        }

        btnEdit.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val id = promiseId ?: return@setOnClickListener
            val editBottomSheet = EditPromiseBottomSheet.newInstance(id)
            editBottomSheet.show(parentFragmentManager, "EditPromiseBottomSheet")
            dismiss()
        }

        btnDelete.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showDeleteConfirmation()
        }
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Promise")
            .setMessage("Are you sure you want to delete this promise? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deletePromise() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePromise() {
        val userId = auth.currentUser?.uid ?: return
        val id = promiseId ?: return

        db.collection("users").document(userId).collection("promises").document(id)
            .delete()
            .addOnSuccessListener {
                if (isAdded) {
                    Toast.makeText(context, "Promise deleted successfully", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            }
            .addOnFailureListener {
                if (isAdded) {
                    Toast.makeText(context, "Failed to delete promise", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun updateStatus(
        status: PromiseStatus, 
        failureTag: String? = null, 
        reason: String? = null, 
        improvement: String? = null
    ) {
        val userId = auth.currentUser?.uid ?: return
        val id = promiseId ?: return

        val updates = mutableMapOf<String, Any>("status" to status.name)
        
        if (status == PromiseStatus.BROKEN) {
            updates["failureTag"] = failureTag ?: ""
            updates["reasonForFailure"] = reason ?: ""
            updates["improvementPlan"] = improvement ?: ""
        } else {
            // Clear broken details if switching back to Kept or Pending
            updates["failureTag"] = ""
            updates["reasonForFailure"] = ""
            updates["improvementPlan"] = ""
        }

        db.collection("users").document(userId).collection("promises").document(id)
            .update(updates)
            .addOnSuccessListener { 
                if (isAdded) dismiss() 
            }
            .addOnFailureListener {
                if (isAdded) Toast.makeText(context, "Failed to update status", Toast.LENGTH_SHORT).show()
            }
    }

    companion object {
        fun newInstance(promiseId: String): PromiseEntryBottomSheet {
            val fragment = PromiseEntryBottomSheet()
            val args = Bundle()
            args.putString("promise_id", promiseId)
            fragment.arguments = args
            return fragment
        }
    }
}
