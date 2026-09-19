package com.example.focuslock

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class DeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "FocusLock Device Admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "FocusLock Device Admin disabled")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        val lockManager = LockManager(context)
        if (lockManager.isLockActive()) {
            return "⚠️ Comeback cannot be deactivated or uninstalled while a focus session is in progress!"
        }
        return null
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val lockManager = LockManager(context)
            if (lockManager.isLockActive()) {
                val serviceIntent = Intent(context, LockService::class.java)
                context.startForegroundService(serviceIntent)
            } else {
                lockManager.unlockAll()
            }
        }
    }

    companion object {
        private const val TAG = "FocusLockAdmin"
    }
}
