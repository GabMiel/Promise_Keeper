package com.example.promisekeeper

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import coil.load
import coil.transform.CircleCropTransformation
import com.example.promisekeeper.databinding.FragmentProfileBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import java.util.Calendar
import java.util.Locale

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val statsViewModel: StatsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUserInfo()
        observeStats()
        setupReminderSettings()

        binding.btnLogout.setOnClickListener {
            showSignOutConfirmation()
        }

        binding.btnAbout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.about_app)
                .setMessage("Promise Keeper v1.0\nBuilt to help you stay accountable to yourself.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun setupUserInfo() {
        val user = auth.currentUser
        if (user != null) {
            binding.userName.text = user.displayName ?: getString(R.string.app_name)
            binding.userEmail.text = user.email ?: ""
            
            binding.profileImage.load(user.photoUrl) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_menu_gallery)
                transformations(CircleCropTransformation())
            }
        }
    }

    private fun observeStats() {
        statsViewModel.stats.observe(viewLifecycleOwner) { stats ->
            if (stats != null) {
                binding.tvTotalCount.text = stats.allTimeTotal.toString()
                binding.tvSuccessRate.text = getString(R.string.percent_format, stats.successPercentage)
                binding.tvLongestStreak.text = stats.longestStreak.toString()
            }
        }
    }

    private fun setupReminderSettings() {
        val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("review_reminder_enabled", true)
        val savedTime = prefs.getString("review_reminder_time", "11:00 PM") ?: "11:00 PM"

        binding.switchReviewReminder.isChecked = isEnabled
        binding.tvReviewReminderTime.text = savedTime
        binding.tvReviewReminderTime.alpha = if (isEnabled) 1.0f else 0.5f

        binding.switchReviewReminder.setOnCheckedChangeListener { _, checked ->
            prefs.edit { putBoolean("review_reminder_enabled", checked) }
            binding.tvReviewReminderTime.alpha = if (checked) 1.0f else 0.5f
            if (checked) {
                scheduleReviewReminder(savedTime)
            } else {
                cancelReviewReminder()
            }
        }

        binding.btnReviewReminderTime.setOnClickListener {
            if (binding.switchReviewReminder.isChecked) {
                showTimePickerDialog(savedTime)
            }
        }
    }

    private fun showTimePickerDialog(currentTime: String) {
        val timeParts = currentTime.split(" ", ":")
        if (timeParts.size < 3) return
        
        var hour = timeParts[0].toInt()
        val minute = timeParts[1].toInt()
        val amPm = timeParts[2]
        
        if (amPm == "PM" && hour < 12) hour += 12
        if (amPm == "AM" && hour == 12) hour = 0

        TimePickerDialog(requireContext(), { _, h, m ->
            val newAmPm = if (h < 12) "AM" else "PM"
            val displayHour = if (h % 12 == 0) 12 else h % 12
            val newTime = String.format(Locale.getDefault(), "%d:%02d %s", displayHour, m, newAmPm)
            
            binding.tvReviewReminderTime.text = newTime
            requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
                putString("review_reminder_time", newTime)
            }
            
            scheduleReviewReminder(newTime)
        }, hour, minute, false).show()
    }

    private fun scheduleReviewReminder(timeString: String) {
        val intent = Intent(requireContext(), ReviewReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            requireContext(), 
            1001, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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

        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    private fun cancelReviewReminder() {
        val intent = Intent(requireContext(), ReviewReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            requireContext(), 
            1001, 
            intent, 
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
        }
    }

    private fun showSignOutConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sign_out)
            .setMessage(R.string.sign_out_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.sign_out) { _, _ ->
                signOut()
            }
            .show()
    }

    private fun signOut() {
        auth.signOut()
        
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        
        GoogleSignIn.getClient(requireActivity(), gso).signOut().addOnCompleteListener {
            val intent = Intent(requireContext(), MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
