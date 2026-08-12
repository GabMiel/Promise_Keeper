package com.example.promisekeeper

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import coil.load
import coil.request.CachePolicy
import coil.transform.CircleCropTransformation
import com.example.promisekeeper.databinding.DialogImageSourceBinding
import com.example.promisekeeper.databinding.FragmentSettingsBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import java.io.File

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()

    private lateinit var pickMedia: ActivityResultLauncher<PickVisualMediaRequest>
    private lateinit var takePhoto: ActivityResultLauncher<Uri>
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private var tempImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupImageLaunchers()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupUserInfo()
        setupSwitches()
        setupClickListeners()
        updateAccountVisibility()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupUserInfo() {
        val user = auth.currentUser
        val sharedPrefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val customPhotoUri = sharedPrefs.getString("custom_profile_photo", null)

        val photoSource = customPhotoUri ?: user?.photoUrl?.toString()?.replace("/s96-c/", "/s400-c/")
        loadProfileImage(photoSource)
    }

    private fun loadProfileImage(source: Any?) {
        binding.profileImage.load(source) {
            crossfade(true)
            placeholder(R.drawable.ic_personal)
            error(R.drawable.ic_personal)
            diskCachePolicy(CachePolicy.ENABLED)
            transformations(CircleCropTransformation())
        }
    }

    private fun setupImageLaunchers() {
        pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { saveAndDisplayPhoto(it) }
        }

        takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) tempImageUri?.let { saveAndDisplayPhoto(it) }
        }

        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) launchCamera()
            else Toast.makeText(requireContext(), "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAndDisplayPhoto(uri: Uri) {
        val sharedPrefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("custom_profile_photo", uri.toString()).apply()
        loadProfileImage(uri)
    }

    private fun showImageSourceDialog() {
        val dialogBinding = DialogImageSourceBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnClose.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnPhotos.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            dialog.dismiss()
        }
        dialogBinding.btnCamera.setOnClickListener {
            checkCameraPermissionAndLaunch()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val tempFile = File.createTempFile("profile_photo_", ".jpg", requireContext().cacheDir).apply { deleteOnExit() }
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", tempFile)
        tempImageUri = uri
        takePhoto.launch(uri)
    }

    private fun updateAccountVisibility() {
        val user = auth.currentUser
        val isGuest = user == null
        binding.tvAccountSection.visibility = if (isGuest) View.GONE else View.VISIBLE
        binding.cardLogout.visibility = if (isGuest) View.GONE else View.VISIBLE
    }

    private fun setupSwitches() {
        val sharedPrefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        
        binding.switchNotifications.isChecked = sharedPrefs.getBoolean("notifications_enabled", true)
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean("notifications_enabled", isChecked).apply()
        }

        binding.switchAutoBackup.isChecked = sharedPrefs.getBoolean("auto_backup", true)
        binding.switchAutoBackup.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && auth.currentUser == null) {
                binding.switchAutoBackup.isChecked = false
                Toast.makeText(requireContext(), "Sign in required", Toast.LENGTH_SHORT).show()
            } else {
                sharedPrefs.edit().putBoolean("auto_backup", isChecked).apply()
            }
        }

        binding.switchSyncDevices.isChecked = sharedPrefs.getBoolean("sync_devices", false)
        binding.switchSyncDevices.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && auth.currentUser == null) {
                binding.switchSyncDevices.isChecked = false
                Toast.makeText(requireContext(), "Sign in required", Toast.LENGTH_SHORT).show()
            } else {
                sharedPrefs.edit().putBoolean("sync_devices", isChecked).apply()
            }
        }
    }

    private fun setupClickListeners() {
        val comingSoon = { Toast.makeText(requireContext(), "Coming soon", Toast.LENGTH_SHORT).show() }
        
        binding.btnEditPhoto.setOnClickListener { showImageSourceDialog() }
        binding.btnAppearance.setOnClickListener { comingSoon() }
        binding.btnHelp.setOnClickListener { comingSoon() }
        binding.btnPrivacy.setOnClickListener { comingSoon() }
        
        binding.btnAbout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.about_app)
                .setMessage("Promise Keeper v1.0\nBuilt to help you stay accountable to yourself.")
                .setPositiveButton("OK", null)
                .show()
        }

        binding.btnLogout.setOnClickListener { showSignOutConfirmation() }
    }

    private fun showSignOutConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sign_out)
            .setMessage(R.string.sign_out_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.sign_out) { _, _ -> signOut() }
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
