package com.example.focuslock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.util.Locale
import java.util.concurrent.TimeUnit

class LockService : Service() {

    private lateinit var lockManager: LockManager
    private lateinit var notificationManager: NotificationManager
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate() {
        super.onCreate()
        lockManager = LockManager(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val initialNotification = buildNotification("Focus lock starting...")
        startForeground(NOTIFICATION_ID, initialNotification)

        val remainingMs = lockManager.getRemainingTime()
        if (remainingMs <= 0) {
            lockManager.unlockAll()
            stopSelf()
            return START_NOT_STICKY
        }

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(remainingMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val formatted = formatDuration(millisUntilFinished)
                val updatedNotification = buildNotification("Focus session active — $formatted remaining")
                notificationManager.notify(NOTIFICATION_ID, updatedNotification)

                val tickIntent = Intent(ACTION_LOCK_TICK).apply {
                    putExtra(EXTRA_REMAINING_MS, millisUntilFinished)
                    setPackage(packageName)
                }
                sendBroadcast(tickIntent)
            }

            override fun onFinish() {
                lockManager.unlockAll()
                val finishNotification = NotificationCompat.Builder(this@LockService, CHANNEL_ID)
                    .setContentTitle("Focus Session Complete")
                    .setContentText("All apps have been unlocked!")
                    .setSmallIcon(R.drawable.ic_lock)
                    .setAutoCancel(true)
                    .build()
                notificationManager.notify(COMPLETION_NOTIFICATION_ID, finishNotification)

                val finishIntent = Intent(ACTION_LOCK_FINISHED).apply {
                    setPackage(packageName)
                }
                sendBroadcast(finishIntent)

                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }.start()

        return START_STICKY
    }

    private fun buildNotification(text: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Comeback Active")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_lock)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Focus Lock Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows the active countdown for blocked apps and notifications"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun formatDuration(ms: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "focus_lock_channel"
        const val NOTIFICATION_ID = 1001
        const val COMPLETION_NOTIFICATION_ID = 1002
        const val ACTION_LOCK_TICK = "com.example.focuslock.ACTION_LOCK_TICK"
        const val ACTION_LOCK_FINISHED = "com.example.focuslock.ACTION_LOCK_FINISHED"
        const val EXTRA_REMAINING_MS = "remaining_ms"
    }
}
