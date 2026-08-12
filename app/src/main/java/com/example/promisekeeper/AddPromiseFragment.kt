package com.example.promisekeeper

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Calendar

class AddPromiseFragment : Fragment() {
    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()
    private var selectedCategory: String? = null
    private var reminderTime: String? = null
    private var editingPromiseId: String? = null

    companion object {
        fun newInstance(promiseId: String? = null): AddPromiseFragment {
            val fragment = AddPromiseFragment()
            val args = Bundle()
            args.putString("promise_id", promiseId)
            fragment.arguments = args
            return fragment
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(context, "Notifications are required for reminders", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_add_promise, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        editingPromiseId = arguments?.getString("promise_id")

        view.findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        view.findViewById<ImageView>(R.id.btnDone).setOnClickListener { savePromise(view) }
        view.findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { savePromise(view) }

        setupCategorySelection(view)
        setupTimePicker(view)
        setupReminderSwitch(view)
        updateCategoryUI(view)

        val etPromise = view.findViewById<EditText>(R.id.etPromise)
        etPromise.requestFocus()
        
        requestNotificationPermission()

        if (editingPromiseId != null) {
            loadPromiseData(view, editingPromiseId!!)
        }
    }

    private fun loadPromiseData(view: View, id: String) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).collection("promises").document(id).get()
            .addOnSuccessListener { doc ->
                val promise = doc.toObject(Promise::class.java)
                promise?.let {
                    view.findViewById<EditText>(R.id.etPromise).setText(it.description)
                    view.findViewById<EditText>(R.id.etNotes).setText(it.notes)
                    selectedCategory = it.category
                    reminderTime = it.reminderTime
                    
                    if (reminderTime != null) {
                        view.findViewById<TextView>(R.id.tvTime).text = reminderTime
                        view.findViewById<SwitchMaterial>(R.id.switchRemind).isChecked = true
                        updateReminderUI(view, true)
                    }
                    updateCategoryUI(view)
                }
            }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
        val inactiveBg = ContextCompat.getColor(requireContext(), R.color.card_tan)

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
            reminderTime = String.format("%d:%02d %s", displayHour, minute, amPm)
            view.findViewById<TextView>(R.id.tvTime).text = reminderTime
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
    }

    private fun setupReminderSwitch(view: View) {
        val switchRemind = view.findViewById<SwitchMaterial>(R.id.switchRemind)
        updateReminderUI(view, switchRemind.isChecked)
        
        switchRemind.setOnCheckedChangeListener { _, isChecked ->
            updateReminderUI(view, isChecked)
            if (isChecked) {
                // Automatically enable global notifications if a reminder is set
                val sharedPrefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
                sharedPrefs.edit().putBoolean("notifications_enabled", true).apply()
                
                if (reminderTime == null) {
                    showTimePickerDialog(view)
                }
            }
        }
    }

    private fun updateReminderUI(view: View, isEnabled: Boolean) {
        val btnTimePicker = view.findViewById<LinearLayout>(R.id.btnTimePicker)
        btnTimePicker.isEnabled = isEnabled
        btnTimePicker.alpha = if (isEnabled) 1.0f else 0.5f
    }

    private fun setLoading(view: View, isLoading: Boolean) {
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSave)
        val btnDone = view.findViewById<ImageView>(R.id.btnDone)
        val btnBack = view.findViewById<ImageView>(R.id.btnBack)
        
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnSave.isEnabled = !isLoading
        btnDone.isEnabled = !isLoading
        btnBack.isEnabled = !isLoading
        
        btnSave.alpha = if (isLoading) 0.7f else 1.0f
        btnDone.alpha = if (isLoading) 0.5f else 1.0f
        btnBack.alpha = if (isLoading) 0.5f else 1.0f
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    private fun savePromise(view: View) {
        hideKeyboard()
        val description = view.findViewById<EditText>(R.id.etPromise).text.toString()
        val notes = view.findViewById<EditText>(R.id.etNotes).text.toString()
        val isReminderOn = view.findViewById<SwitchMaterial>(R.id.switchRemind).isChecked

        if (description.isBlank()) {
            Toast.makeText(requireContext(), "Please enter a promise", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(view, true)

        val finalReminderTime = if (isReminderOn) (reminderTime ?: "8:00 PM") else null
        val userId = auth.currentUser?.uid ?: return

        val id = editingPromiseId ?: db.collection("users").document(userId).collection("promises").document().id
        val promise = Promise(
            id = id,
            userId = userId,
            description = description,
            category = selectedCategory ?: "Other",
            reminderTime = finalReminderTime,
            notes = notes
        )

        db.collection("users").document(userId).collection("promises").document(promise.id).set(promise)
            .addOnSuccessListener {
                if (isReminderOn) {
                    scheduleNotification(promise)
                }
                parentFragmentManager.popBackStack()
            }
            .addOnFailureListener {
                setLoading(view, false)
                Toast.makeText(requireContext(), "Failed to save promise", Toast.LENGTH_SHORT).show()
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
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }
}
