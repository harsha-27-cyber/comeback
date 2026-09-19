package com.example.focuslock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.util.Locale
import java.util.concurrent.TimeUnit

class BlockOverlayActivity : AppCompatActivity() {

    private lateinit var lockManager: LockManager
    private lateinit var tvBlockedAppName: TextView
    private lateinit var tvBlockedNotice: TextView
    private lateinit var tvOverlayCountdown: TextView
    private lateinit var btnOverlayHome: MaterialButton
    private lateinit var btnOverlayOpenFocusLock: MaterialButton
    private lateinit var ivLockIcon: ImageView

    private var countdownTimer: CountDownTimer? = null

    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                LockService.ACTION_LOCK_TICK -> {
                    val remaining = intent.getLongExtra(LockService.EXTRA_REMAINING_MS, 0L)
                    tvOverlayCountdown.text = formatDuration(remaining)
                }
                LockService.ACTION_LOCK_FINISHED -> {
                    finish()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Modern API for showing over lockscreen & keeping screen on
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Handle back button modern callback
        onBackPressedDispatcher.addCallback(this) {
            goToHome()
        }

        setContentView(R.layout.activity_block_overlay)

        lockManager = LockManager(this)

        tvBlockedAppName = findViewById(R.id.tvBlockedAppName)
        tvBlockedNotice = findViewById(R.id.tvBlockedNotice)
        tvOverlayCountdown = findViewById(R.id.tvOverlayCountdown)
        btnOverlayHome = findViewById(R.id.btnOverlayHome)
        btnOverlayOpenFocusLock = findViewById(R.id.btnOverlayOpenFocusLock)
        ivLockIcon = findViewById(R.id.ivLockIcon)

        updateBlockedAppUI(intent)

        btnOverlayHome.setOnClickListener {
            goToHome()
        }

        btnOverlayOpenFocusLock.setOnClickListener {
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(mainIntent)
            finish()
        }

        startLocalCountdown()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateBlockedAppUI(intent)
        startLocalCountdown()
    }

    private fun updateBlockedAppUI(intentToUse: Intent?) {
        val blockedPkg = intentToUse?.getStringExtra(EXTRA_BLOCKED_PACKAGE).orEmpty()
        val isTamper = intentToUse?.getBooleanExtra(EXTRA_IS_TAMPER, false) ?: false

        if (isTamper) {
            tvBlockedAppName.text = "Settings Protected"
            tvBlockedNotice.text = "System settings and uninstallation are locked during active focus sessions."
        } else if (blockedPkg.isNotEmpty()) {
            val appLabel = try {
                val appInfo = packageManager.getApplicationInfo(blockedPkg, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                blockedPkg
            }
            tvBlockedAppName.text = "$appLabel is Blocked"
            tvBlockedNotice.text = "Stay focused! This app is inaccessible until the session ends."
        }
    }

    override fun onResume() {
        super.onResume()
        isOverlayActive = true
        if (!lockManager.isLockActive()) {
            finish()
            return
        }

        val filter = IntentFilter().apply {
            addAction(LockService.ACTION_LOCK_TICK)
            addAction(LockService.ACTION_LOCK_FINISHED)
        }
        ContextCompat.registerReceiver(this, tickReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        isOverlayActive = false
        try {
            unregisterReceiver(tickReceiver)
        } catch (e: Exception) {
            // Ignored
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isOverlayActive = false
        countdownTimer?.cancel()
    }

    private fun startLocalCountdown() {
        val remaining = lockManager.getRemainingTime()
        tvOverlayCountdown.text = formatDuration(remaining)

        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(remaining, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                tvOverlayCountdown.text = formatDuration(millisUntilFinished)
            }

            override fun onFinish() {
                finish()
            }
        }.start()
    }

    private fun goToHome() {
        isOverlayActive = false
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    private fun formatDuration(ms: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "extra_blocked_package"
        const val EXTRA_IS_TAMPER = "extra_is_tamper"

        @Volatile
        var isOverlayActive: Boolean = false
    }
}
