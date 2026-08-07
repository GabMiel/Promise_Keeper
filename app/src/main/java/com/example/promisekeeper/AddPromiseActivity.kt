package com.example.promisekeeper

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Calendar

class AddPromiseActivity : AppCompatActivity() {
    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()
    private var selectedCategory = "Study"
    private var reminderTime: String? = "8:00 PM"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_promise)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.btnDone).setOnClickListener { savePromise() }
        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { savePromise() }

        setupCategorySelection()
        setupTimePicker()
    }

    private fun setupCategorySelection() {
        val categories = mapOf(
            R.id.btnStudy to "Study",
            R.id.btnHealth to "Health",
            R.id.btnPersonal to "Personal",
            R.id.btnOther to "Other"
        )

        categories.forEach { (id, name) ->
            findViewById<LinearLayout>(id).setOnClickListener {
                selectedCategory = name
                updateCategoryUI()
            }
        }
    }

    private fun updateCategoryUI() {
        // Implementation for changing colors based on selection
    }

    private fun setupTimePicker() {
        findViewById<LinearLayout>(R.id.btnTimePicker).setOnClickListener {
            val calendar = Calendar.getInstance()
            TimePickerDialog(this, { _, hour, minute ->
                val amPm = if (hour < 12) "AM" else "PM"
                val displayHour = if (hour % 12 == 0) 12 else hour % 12
                reminderTime = String.format("%d:%02d %s", displayHour, minute, amPm)
                findViewById<TextView>(R.id.tvTime).text = reminderTime
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
        }
    }

    private fun savePromise() {
        val description = findViewById<EditText>(R.id.etPromise).text.toString()
        val notes = findViewById<EditText>(R.id.etNotes).text.toString()
        val isReminderOn = findViewById<SwitchMaterial>(R.id.switchRemind).isChecked

        if (description.isBlank()) {
            Toast.makeText(this, "Please enter a promise", Toast.LENGTH_SHORT).show()
            return
        }

        val promise = Promise(
            id = db.collection("promises").document().id,
            userId = auth.currentUser?.uid ?: "",
            description = description,
            category = selectedCategory,
            reminderTime = if (isReminderOn) reminderTime else null,
            notes = notes
        )

        db.collection("promises").document(promise.id).set(promise)
            .addOnSuccessListener {
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save promise", Toast.LENGTH_SHORT).show()
            }
    }
}
