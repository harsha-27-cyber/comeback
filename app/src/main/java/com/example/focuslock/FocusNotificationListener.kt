package com.example.focuslock

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class FocusNotificationListener : NotificationListenerService() {

    private lateinit var lockManager: LockManager

    override fun onCreate() {
        super.onCreate()
        lockManager = LockManager(this)
        Log.d(TAG, "FocusNotificationListener created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (!lockManager.isLockActive()) return

        val pkg = sbn.packageName ?: return

        // Always allow our own notifications (countdown timer) and system notifications
        if (pkg == packageName || pkg == "android") return

        if (!lockManager.isAppAllowed(pkg)) {
            Log.i(TAG, "Cancelling notification from blocked app: $pkg")
            try {
                cancelNotification(sbn.key)
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling notification: ${e.message}")
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")
        clearBlockedNotifications()
    }

    private fun clearBlockedNotifications() {
        if (!lockManager.isLockActive()) return

        try {
            val notifications = activeNotifications ?: return
            for (sbn in notifications) {
                val pkg = sbn.packageName ?: continue
                if (pkg != packageName && pkg != "android" && !lockManager.isAppAllowed(pkg)) {
                    cancelNotification(sbn.key)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing active notifications: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "FocusNotification"
    }
}
