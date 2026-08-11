package com.example.promisekeeper

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import java.util.Locale

class EditPromiseBottomSheet : BottomSheetDialogFragment() {

    private var promiseId: String? = null
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private var selectedCategory: String? = null
    private var reminderTime: String? = null
    private var currentPromise: Promise? = null

    companion object {
        fun newInstance(promiseId: String): EditPromiseBottomSheet {
            val fragment = EditPromiseBottomSheet()
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
        return inflater.inflate(R.layout.dialog_edit_promise, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        promiseId = arguments?.getString("promise_id")

        setupCategorySelection(view)
        setupTimePicker(view)
        setupReminderSwitch(view)
        updateCategoryUI(view)

        view.findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            savePromise(view)
        }

        if (promiseId != null) {
            loadPromiseData(view)
        }
    }

    private fun loadPromiseData(view: View) {
        val userId = auth.currentUser?.uid ?: return
        val id = promiseId ?: return
        
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        progressBar.visibility = View.VISIBLE

        db.collection("users").document(userId).collection("promises").document(id).get()
            .addOnSuccessListener { doc ->
                if (!isAdded) return@addOnSuccessListener
                progressBar.visibility = View.GONE
                
                val promise = doc.toObject(Promise::class.java)
                if (promise != null) {
                    currentPromise = promise
                    view.findViewById<EditText>(R.id.etPromise).setText(promise.description)
                    view.findViewById<EditText>(R.id.etNotes).setText(promise.notes)
                    selectedCategory = promise.category
                    reminderTime = promise.reminderTime
                    
                    if (reminderTime != null) {
                        view.findViewById<TextView>(R.id.tvTime).text = reminderTime
                        view.findViewById<SwitchMaterial>(R.id.switchRemind).isChecked = true
                        updateReminderUI(view, true)
                    } else {
                        updateReminderUI(view, false)
                    }
                    updateCategoryUI(view)
                }
            }
            .addOnFailureListener {
                if (isAdded) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(context, "Error loading promise", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun setupCategorySelection(view: View) {
        val categories = mapOf(
            R.id.btnStudy to "Study",
            R.id.btnHealth to "Health",
            R.id.btnPersonal to "Personal",
            R.id.btnOther to "Other"
        )

        categories.forEach { (id, name) ->
            view.findViewById<LinearLayout>(id).setOnClickListener {
                selectedCategory = if (selectedCategory == name) null else name
                updateCategoryUI(view)
            }
        }
    }

    private fun updateCategoryUI(view: View) {
        val categories = mapOf(
            "Study" to R.id.btnStudy,
            "Health" to R.id.btnHealth,
            "Personal" to R.id.btnPersonal,
            "Other" to R.id.btnOther
        )

        val activeColor = ContextCompat.getColor(requireContext(), R.color.accent_red)
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.text_gray)
        val activeBg = ContextCompat.getColor(requireContext(), R.color.accent_red_light)
        val inactiveBg = ContextCompat.getColor(requireContext(), R.color.white)

        categories.forEach { (name, id) ->
            val layout = view.findViewById<LinearLayout>(id)
            val card = layout.getChildAt(0) as MaterialCardView
            val icon = card.getChildAt(0) as ImageView
            val text = layout.getChildAt(1) as TextView

            val isActive = selectedCategory == name
            
            card.setCardBackgroundColor(if (isActive) activeBg else inactiveBg)
            icon.setColorFilter(if (isActive) activeColor else inactiveColor)
            text.setTextColor(if (isActive) activeColor else inactiveColor)
        }
    }

    private fun setupTimePicker(view: View) {
        view.findViewById<LinearLayout>(R.id.btnTimePicker).setOnClickListener {
            showTimePickerDialog(view)
        }
    }

    private fun showTimePickerDialog(view: View) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(requireContext(), { _, hour, minute ->
            val amPm = if (hour < 12) "AM" else "PM"
            val displayHour = if (hour % 12 == 0) 12 else hour % 12
            reminderTime = String.format(Locale.getDefault(), "%d:%02d %s", displayHour, minute, amPm)
            view.findViewById<TextView>(R.id.tvTime).text = reminderTime
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
    }

    private fun setupReminderSwitch(view: View) {
        val switchRemind = view.findViewById<SwitchMaterial>(R.id.switchRemind)
        switchRemind.setOnCheckedChangeListener { _, isChecked ->
            updateReminderUI(view, isChecked)
            if (isChecked && reminderTime == null) {
                showTimePickerDialog(view)
            }
        }
    }

    private fun updateReminderUI(view: View, isEnabled: Boolean) {
        val btnTimePicker = view.findViewById<LinearLayout>(R.id.btnTimePicker)
        btnTimePicker.isEnabled = isEnabled
        btnTimePicker.alpha = if (isEnabled) 1.0f else 0.5f
    }

    private fun savePromise(view: View) {
        val description = view.findViewById<EditText>(R.id.etPromise).text.toString()
        val notes = view.findViewById<EditText>(R.id.etNotes).text.toString()
        val isReminderOn = view.findViewById<SwitchMaterial>(R.id.switchRemind).isChecked

        if (description.isBlank()) {
            Toast.makeText(requireContext(), "Please enter a promise", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedCategory == null) {
            Toast.makeText(requireContext(), "Please select a category", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = auth.currentUser?.uid ?: return
        val id = promiseId ?: return
        
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSave)
        
        progressBar.visibility = View.VISIBLE
        btnSave.isEnabled = false

        val finalReminderTime = if (isReminderOn) (reminderTime ?: "8:00 PM") else null
        
        val updates = mutableMapOf<String, Any?>(
            "description" to description,
            "category" to selectedCategory,
            "reminderTime" to finalReminderTime,
            "notes" to notes
        )

        db.collection("users").document(userId).collection("promises").document(id)
            .update(updates)
            .addOnSuccessListener {
                if (isAdded) {
                    val updatedPromise = currentPromise?.copy(
                        description = description,
                        category = selectedCategory!!,
                        reminderTime = finalReminderTime,
                        notes = notes
                    )
                    if (isReminderOn && updatedPromise != null) {
                        scheduleNotification(updatedPromise)
                    }
                    Toast.makeText(context, "Promise updated", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            }
            .addOnFailureListener {
                if (isAdded) {
                    progressBar.visibility = View.GONE
                    btnSave.isEnabled = true
                    Toast.makeText(context, "Failed to update", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun scheduleNotification(promise: Promise) {
        val timeString = promise.reminderTime ?: return
        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val timeParts = timeString.split(" ", ":")
        if (timeParts.size < 3) return
        
        var hour = timeParts[0].toInt()
        val minute = timeParts[1].toInt()
        val amPm = timeParts[2]
        
        if (amPm == "PM" && hour < 12) hour += 12
        if (amPm == "AM" && hour == 12) hour = 0
        
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }
        
        if (calendar.before(Calendar.getInstance())) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        
        val intent = Intent(requireContext(), NotificationReceiver::class.java).apply {
            putExtra("PROMISE_DESC", promise.description)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            requireContext(),
            promise.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
        } catch (e: Exception) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
    }
}
