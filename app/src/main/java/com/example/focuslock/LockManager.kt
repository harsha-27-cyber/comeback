package com.example.focuslock

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat

class LockManager(private val context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(context, DeviceAdminReceiver::class.java)
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isDeviceAdminActive(): Boolean {
        return try {
            dpm.isAdminActive(adminComponent)
        } catch (e: Exception) {
            false
        }
    }

    fun isDeviceOwner(): Boolean {
        return try {
            dpm.isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) {
            false
        }
    }

    fun isAccessibilityEnabled(): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        val targetServiceId = "${context.packageName}/${FocusAccessibilityService::class.java.canonicalName}"
        return enabledServices.any { it.id.equals(targetServiceId, ignoreCase = true) || it.resolveInfo.serviceInfo.packageName == context.packageName }
    }

    fun isNotificationListenerEnabled(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    fun isLockActive(): Boolean {
        val active = prefs.getBoolean(KEY_LOCK_ACTIVE, false)
        return active && getRemainingTime() > 0
    }

    fun getRemainingTime(): Long {
        val unlockTime = prefs.getLong(KEY_UNLOCK_TIME, 0L)
        return maxOf(0L, unlockTime - System.currentTimeMillis())
    }

    fun isWhitelistMode(): Boolean {
        return prefs.getBoolean(KEY_IS_WHITELIST, true)
    }

    fun getAllowedApps(): Set<String> {
        return prefs.getStringSet(KEY_ALLOWED_APPS, emptySet()) ?: emptySet()
    }

    fun getBlockedApps(): Set<String> {
        return prefs.getStringSet(KEY_BLOCKED_APPS, emptySet()) ?: emptySet()
    }

    fun isAppAllowed(packageName: String): Boolean {
        // Essential system components that must always be allowed
        if (packageName == context.packageName) return true
        if (packageName == "android" || packageName == "com.android.systemui") return true

        // Phone dialers and emergency calls must always work
        if (isDialerOrEmergencyApp(packageName)) return true

        // Default home launcher must be visible so user can navigate to allowed apps
        if (isDefaultLauncher(packageName)) return true

        // Check user selection
        return if (isWhitelistMode()) {
            getAllowedApps().contains(packageName)
        } else {
            !getBlockedApps().contains(packageName)
        }
    }

    private fun isDialerOrEmergencyApp(packageName: String): Boolean {
        val lower = packageName.lowercase()
        if (lower.contains("dialer") || lower.contains("telecom") || lower.contains("emergency") || lower.contains("incallui")) {
            return true
        }

        try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val defaultDialer = tm?.defaultDialerPackage
            if (defaultDialer != null && defaultDialer == packageName) {
                return true
            }
        } catch (e: Exception) {
            // Ignored
        }

        return false
    }

    private fun isDefaultLauncher(packageName: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName == packageName
        } catch (e: Exception) {
            false
        }
    }

    fun lockApps(apps: Set<String>, durationMs: Long, isWhitelist: Boolean): Boolean {
        val unlockTime = System.currentTimeMillis() + durationMs

        val editor = prefs.edit()
            .putLong(KEY_UNLOCK_TIME, unlockTime)
            .putBoolean(KEY_LOCK_ACTIVE, true)
            .putBoolean(KEY_IS_WHITELIST, isWhitelist)

        if (isWhitelist) {
            editor.putStringSet(KEY_ALLOWED_APPS, apps)
        } else {
            editor.putStringSet(KEY_BLOCKED_APPS, apps)
        }
        editor.apply()

        // If app has Device Owner privileges, also leverage OS-level package suspension and anti-uninstall
        if (isDeviceOwner()) {
            try {
                dpm.setUninstallBlocked(adminComponent, context.packageName, true)
                if (!isWhitelist && apps.isNotEmpty()) {
                    dpm.setPackagesSuspended(adminComponent, apps.toTypedArray(), true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Device owner actions failed: ${e.message}")
            }
        }

        Log.i(TAG, "Lock activated for ${durationMs / 1000} seconds (whitelist=$isWhitelist, count=${apps.size})")
        return true
    }

    fun unlockAll() {
        if (isDeviceOwner()) {
            try {
                val blocked = getBlockedApps()
                if (blocked.isNotEmpty()) {
                    dpm.setPackagesSuspended(adminComponent, blocked.toTypedArray(), false)
                }
                dpm.setUninstallBlocked(adminComponent, context.packageName, false)
            } catch (e: Exception) {
                Log.e(TAG, "Device owner unlock error: ${e.message}")
            }
        }

        prefs.edit()
            .remove(KEY_UNLOCK_TIME)
            .putBoolean(KEY_LOCK_ACTIVE, false)
            .apply()

        Log.i(TAG, "All apps unlocked and lock session cleared")
    }

    companion object {
        private const val TAG = "LockManager"
        private const val PREFS_NAME = "focuslock_prefs"
        private const val KEY_UNLOCK_TIME = "unlock_time"
        private const val KEY_LOCK_ACTIVE = "lock_active"
        private const val KEY_IS_WHITELIST = "is_whitelist"
        private const val KEY_ALLOWED_APPS = "allowed_apps"
        private const val KEY_BLOCKED_APPS = "blocked_apps"
    }
}
