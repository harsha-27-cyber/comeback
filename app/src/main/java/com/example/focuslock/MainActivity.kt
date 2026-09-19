package com.example.focuslock

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.RadioGroup
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var lockManager: LockManager
    private lateinit var prefs: SharedPreferences

    private lateinit var tvCountdownLabel: TextView
    private lateinit var tvCountdownTime: TextView
    private lateinit var tvCountdownSubtitle: TextView
    private lateinit var layoutConfig: View
    private lateinit var cardPermissions: View

    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var btnEnableAccessibility: Button
    private lateinit var tvNotificationStatus: TextView
    private lateinit var btnEnableNotification: Button
    private lateinit var tvAdminStatus: TextView
    private lateinit var btnEnableAdmin: Button

    private lateinit var rgLockMode: RadioGroup
    private lateinit var btnPickApps: MaterialButton
    private lateinit var btnDeactivateAdmin: MaterialButton
    private lateinit var npHours: NumberPicker
    private lateinit var npMinutes: NumberPicker
    private lateinit var npSeconds: NumberPicker
    private lateinit var btnStartLock: MaterialButton

    private val selectedApps = mutableListOf<String>()
    private var isWhitelistMode = true

    private val appPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val list = result.data?.getStringArrayListExtra(AppPickerActivity.EXTRA_SELECTED_APPS)
            selectedApps.clear()
            if (list != null) {
                selectedApps.addAll(list)
            }
            saveSelectedApps()
            updateAppButtonText()
        }
    }

    private val lockStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                LockService.ACTION_LOCK_TICK -> {
                    val remainingMs = intent.getLongExtra(LockService.EXTRA_REMAINING_MS, 0L)
                    tvCountdownTime.text = formatDuration(remainingMs)
                }
                LockService.ACTION_LOCK_FINISHED -> {
                    refreshUI()
                    Toast.makeText(this@MainActivity, "Focus session finished! All apps and notifications unlocked.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lockManager = LockManager(this)
        prefs = getSharedPreferences("focuslock_ui_prefs", Context.MODE_PRIVATE)

        tvCountdownLabel = findViewById(R.id.tvCountdownLabel)
        tvCountdownTime = findViewById(R.id.tvCountdownTime)
        tvCountdownSubtitle = findViewById(R.id.tvCountdownSubtitle)
        layoutConfig = findViewById(R.id.layoutConfig)
        cardPermissions = findViewById(R.id.cardPermissions)

        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        btnEnableAccessibility = findViewById(R.id.btnEnableAccessibility)
        tvNotificationStatus = findViewById(R.id.tvNotificationStatus)
        btnEnableNotification = findViewById(R.id.btnEnableNotification)
        tvAdminStatus = findViewById(R.id.tvAdminStatus)
        btnEnableAdmin = findViewById(R.id.btnEnableAdmin)

        rgLockMode = findViewById(R.id.rgLockMode)
        btnPickApps = findViewById(R.id.btnPickApps)
        btnDeactivateAdmin = findViewById(R.id.btnDeactivateAdmin)
        npHours = findViewById(R.id.npHours)
        npMinutes = findViewById(R.id.npMinutes)
        npSeconds = findViewById(R.id.npSeconds)
        btnStartLock = findViewById(R.id.btnStartLock)

        loadSavedSettings()
        updateAppButtonText()
        setupTimerPickers()

        setupPermissionButtons()

        rgLockMode.setOnCheckedChangeListener { _, checkedId ->
            isWhitelistMode = (checkedId == R.id.rbModeWhitelist)
            prefs.edit().putBoolean("pref_is_whitelist", isWhitelistMode).apply()
            updateAppButtonText()
        }

        btnPickApps.setOnClickListener {
            val intent = Intent(this, AppPickerActivity::class.java).apply {
                putStringArrayListExtra(AppPickerActivity.EXTRA_SELECTED_APPS, ArrayList(selectedApps))
                putExtra(AppPickerActivity.EXTRA_IS_WHITELIST, isWhitelistMode)
            }
            appPickerLauncher.launch(intent)
        }

        btnDeactivateAdmin.setOnClickListener {
            try {
               // This is the most direct intent to open the Device Admin apps list
                val intent = Intent("android.settings.DEVICE_ADMIN_SETTINGS")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    // Fallback 1: Try security settings
                    val intent = Intent("android.settings.SECURITY_SETTINGS")
                    startActivity(intent)
                } catch (e2: Exception) {
                    // Fallback 2: General settings
                    val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
                    startActivity(intent)
                }
            }
            Toast.makeText(this, "Find Comeback in the list and toggle it OFF to uninstall", Toast.LENGTH_LONG).show()
        }

        btnStartLock.setOnClickListener {
            handleStartLockClick()
        }

        requestNotificationPermissionIfNeeded()
    }

    private fun setupTimerPickers() {
        npHours.minValue = 0
        npHours.maxValue = 23
        npHours.wrapSelectorWheel = true

        npMinutes.minValue = 0
        npMinutes.maxValue = 59
        npMinutes.wrapSelectorWheel = true

        npSeconds.minValue = 0
        npSeconds.maxValue = 59
        npSeconds.wrapSelectorWheel = true
    }

    private fun setupPermissionButtons() {
        btnEnableAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Find 'Comeback' and turn it ON", Toast.LENGTH_LONG).show()
        }

        btnEnableNotification.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Allow notification access for 'Comeback'", Toast.LENGTH_LONG).show()
        }

        btnEnableAdmin.setOnClickListener {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, lockManager.adminComponent)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.device_admin_description))
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(LockService.ACTION_LOCK_TICK)
            addAction(LockService.ACTION_LOCK_FINISHED)
        }
        ContextCompat.registerReceiver(this, lockStatusReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        refreshUI()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(lockStatusReceiver)
        } catch (e: Exception) {
            // Ignored
        }
    }

    private fun refreshUI() {
        val isLocked = lockManager.isLockActive()

        // Permissions check
        val hasAccessibility = lockManager.isAccessibilityEnabled()
        val hasNotification = lockManager.isNotificationListenerEnabled()
        val hasAdmin = lockManager.isDeviceAdminActive()

        tvAccessibilityStatus.text = if (hasAccessibility) "✓ App Blocker (Active)" else "⚠️ App Blocker (Needs Permission)"
        tvAccessibilityStatus.setTextColor(if (hasAccessibility) Color.parseColor("#15803D") else Color.parseColor("#B91C1C"))
        btnEnableAccessibility.visibility = if (hasAccessibility) View.GONE else View.VISIBLE

        tvNotificationStatus.text = if (hasNotification) "✓ Notification Blocker (Active)" else "⚠️ Notification Blocker (Needs Permission)"
        tvNotificationStatus.setTextColor(if (hasNotification) Color.parseColor("#15803D") else Color.parseColor("#B91C1C"))
        btnEnableNotification.visibility = if (hasNotification) View.GONE else View.VISIBLE

        tvAdminStatus.text = if (hasAdmin) "✓ Anti-Uninstall (Active)" else "⚠️ Anti-Uninstall (Needs Permission)"
        tvAdminStatus.setTextColor(if (hasAdmin) Color.parseColor("#15803D") else Color.parseColor("#B91C1C"))
        btnEnableAdmin.visibility = if (hasAdmin) View.GONE else View.VISIBLE

        val allPermissionsGranted = hasAccessibility && hasNotification && hasAdmin
        cardPermissions.visibility = if (allPermissionsGranted && isLocked) View.GONE else View.VISIBLE

        if (isLocked) {
            val remainingMs = lockManager.getRemainingTime()
            tvCountdownLabel.text = "FOCUS LOCK ACTIVE"
            tvCountdownTime.text = formatDuration(remainingMs)
            tvCountdownSubtitle.text = "Distracting apps and notifications are blocked"

            layoutConfig.visibility = View.GONE
            btnStartLock.visibility = View.GONE
        } else {
            tvCountdownLabel.text = "READY TO FOCUS"
            tvCountdownTime.text = "00:00:00"
            tvCountdownSubtitle.text = "Select apps and duration below"

            layoutConfig.visibility = View.VISIBLE
            btnStartLock.visibility = View.VISIBLE
            updateAppButtonText()
        }
    }

    private fun handleStartLockClick() {
        // Verify permissions
        if (!lockManager.isAccessibilityEnabled()) {
            Toast.makeText(this, "Please enable Accessibility permission to block apps", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            return
        }

        if (!lockManager.isNotificationListenerEnabled()) {
            Toast.makeText(this, "Please enable Notification Access to block notifications", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
            return
        }

        if (!lockManager.isDeviceAdminActive()) {
            Toast.makeText(this, "Please enable Device Admin to prevent uninstallation", Toast.LENGTH_LONG).show()
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, lockManager.adminComponent)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.device_admin_description))
            }
            startActivity(intent)
            return
        }

        if (!isWhitelistMode && selectedApps.isEmpty()) {
            Toast.makeText(this, "Please select at least one app to block", Toast.LENGTH_SHORT).show()
            return
        }

        val hours = npHours.value
        val minutes = npMinutes.value
        val seconds = npSeconds.value
        val durationMs = (hours * 3600000L) + (minutes * 60000L) + (seconds * 1000L)

        if (durationMs <= 0) {
            Toast.makeText(this, "Please select a duration of at least 1 second", Toast.LENGTH_SHORT).show()
            return
        }

        val durationText = if (hours > 0) "${hours}h ${minutes}m ${seconds}s" else "${minutes}m ${seconds}s"
        val modeDescription = if (isWhitelistMode) {
            "• All apps will be BLOCKED except your ${selectedApps.size} allowed apps.\n• Essential phone & emergency calls always work."
        } else {
            "• ${selectedApps.size} selected apps will be BLOCKED."
        }

        AlertDialog.Builder(this)
            .setTitle("Confirm Focus Lock ($durationText)")
            .setMessage("Are you sure? During this session:\n\n$modeDescription\n• Notifications from blocked apps will be silenced.\n• Comeback CANNOT be uninstalled until the timer finishes.\n\nReady?")
            .setPositiveButton("Start Lock") { _, _ ->
                val success = lockManager.lockApps(selectedApps.toSet(), durationMs, isWhitelistMode)
                if (success) {
                    val serviceIntent = Intent(this, LockService::class.java)
                    startForegroundService(serviceIntent)
                    refreshUI()
                    Toast.makeText(this, "Focus session started for $durationText!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to start lock.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateAppButtonText() {
        val count = selectedApps.size
        btnPickApps.text = if (isWhitelistMode) {
            "Allowed Apps ($count allowed)"
        } else {
            "Blocked Apps ($count blocked)"
        }
    }

    private fun saveSelectedApps() {
        prefs.edit().putStringSet("saved_selected_apps", selectedApps.toSet()).apply()
    }

    private fun loadSavedSettings() {
        isWhitelistMode = prefs.getBoolean("pref_is_whitelist", true)
        if (isWhitelistMode) {
            rgLockMode.check(R.id.rbModeWhitelist)
        } else {
            rgLockMode.check(R.id.rbModeBlacklist)
        }

        val saved = prefs.getStringSet("saved_selected_apps", emptySet())
        if (saved != null) {
            selectedApps.clear()
            selectedApps.addAll(saved)
        }
    }

    private fun formatDuration(ms: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }
}
