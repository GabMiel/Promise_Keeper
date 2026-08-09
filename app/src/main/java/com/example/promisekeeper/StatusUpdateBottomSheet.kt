package com.example.promisekeeper

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StatusUpdateBottomSheet : BottomSheetDialogFragment() {

    private var promiseId: String? = null
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        fun newInstance(promiseId: String): StatusUpdateBottomSheet {
            val fragment = StatusUpdateBottomSheet()
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
        return inflater.inflate(R.layout.dialog_status_update, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        promiseId = arguments?.getString("promise_id")

        val btnKept = view.findViewById<View>(R.id.btnModalKept)
        val btnBroken = view.findViewById<View>(R.id.btnModalBroken)
        val btnPending = view.findViewById<View>(R.id.btnModalPending)
        val layoutBrokenDetails = view.findViewById<View>(R.id.layoutBrokenDetails)
        val etReason = view.findViewById<EditText>(R.id.etBrokenReason)
        val btnConfirmBroken = view.findViewById<View>(R.id.btnConfirmBroken)

        btnKept.setOnClickListener {
            updateStatus(PromiseStatus.KEPT)
        }

        btnBroken.setOnClickListener {
            layoutBrokenDetails.visibility = View.VISIBLE
            btnPending.visibility = View.GONE
        }

        btnConfirmBroken.setOnClickListener {
            val reason = etReason.text.toString()
            updateStatus(PromiseStatus.BROKEN, reason)
        }

        btnPending.setOnClickListener {
            updateStatus(PromiseStatus.PENDING)
        }
    }

    private fun updateStatus(status: PromiseStatus, reason: String? = null) {
        val userId = auth.currentUser?.uid ?: return
        val id = promiseId ?: return

        val updates = mutableMapOf<String, Any>(
            "status" to status.name
        )
        if (reason != null) {
            updates["reasonForFailure"] = reason
        }

        db.collection("users").document(userId).collection("promises").document(id)
            .update(updates)
            .addOnSuccessListener {
                dismiss()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to update status", Toast.LENGTH_SHORT).show()
            }
    }
}
