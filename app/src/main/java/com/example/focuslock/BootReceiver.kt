package com.example.focuslock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val lockManager = LockManager(context)
            if (lockManager.isLockActive()) {
                Log.i("FocusLockBoot", "Device rebooted during active focus lock. Re-starting LockService.")
                val serviceIntent = Intent(context, LockService::class.java)
                context.startForegroundService(serviceIntent)
            } else {
                lockManager.unlockAll()
            }
        }
    }
}
