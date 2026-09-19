package com.example.focuslock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class FocusAccessibilityService : AccessibilityService() {

    private lateinit var lockManager: LockManager
    private var lastBlockedPackage: String? = null
    private var lastBlockTime: Long = 0

    override fun onCreate() {
        super.onCreate()
        lockManager = LockManager(this)
        Log.d(TAG, "FocusAccessibilityService created")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!lockManager.isLockActive()) return

        val pkgName = event.packageName?.toString() ?: return

        // Ignore our own app UI to prevent loops
        if (pkgName == packageName) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handleWindowStateChanged(pkgName, event)
        }
    }

    private fun handleWindowStateChanged(pkgName: String, event: AccessibilityEvent) {
        val className = event.className?.toString() ?: ""

        // Anti-Bypass Protection:
        // Prevent user from accessing Settings -> Apps -> FocusLock or Device Admin settings to uninstall/force stop
        if (isAntiTamperTarget(pkgName, className, event)) {
            Log.w(TAG, "Anti-tamper triggered for package: $pkgName, class: $className")
            blockAccess(pkgName, isTamperAttempt = true)
            return
        }

        // App Blocking: Check if the opened app is allowed
        if (!lockManager.isAppAllowed(pkgName)) {
            val now = System.currentTimeMillis()

            // If the overlay is ALREADY active on screen for this package,
            // ignore duplicate window events within 300ms.
            // If the overlay is NOT visible on screen, NEVER skip blocking!
            if (BlockOverlayActivity.isOverlayActive && pkgName == lastBlockedPackage && (now - lastBlockTime) < 300) {
                return
            }

            lastBlockedPackage = pkgName
            lastBlockTime = now

            Log.i(TAG, "Blocking unallowed app: $pkgName")
            blockAccess(pkgName, isTamperAttempt = false)
        } else {
            // Reset when navigating to an allowed app or launcher
            lastBlockedPackage = null
        }
    }

    private fun isAntiTamperTarget(pkgName: String, className: String, event: AccessibilityEvent): Boolean {
        // Prevent uninstall dialogs
        if (pkgName.contains("packageinstaller") || pkgName.contains("uninstall")) {
            return true
        }

        // Detect if user is inside Settings looking at FocusLock app details
        if (pkgName == "com.android.settings") {
            val eventTexts = event.text.map { it.toString().lowercase() }
            val containsAppName = eventTexts.any { it.contains("comeback") || it.contains("focuslock") }
            val isAppDetailsClass = className.contains("InstalledAppDetails", ignoreCase = true) ||
                    className.contains("AppInfo", ignoreCase = true) ||
                    className.contains("DeviceAdmin", ignoreCase = true) ||
                    className.contains("Accessibility", ignoreCase = true)

            if (containsAppName || isAppDetailsClass) {
                return true
            }
        }

        return false
    }

    private fun blockAccess(blockedPkg: String, isTamperAttempt: Boolean) {
        // Immediately display the full-screen lock overlay over the blocked app.
        // We do NOT call performGlobalAction(GLOBAL_ACTION_HOME) here because
        // it asynchronously dismisses the overlay back to the launcher, allowing
        // rapid subsequent taps to bypass the block.
        val overlayIntent = Intent(this, BlockOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            putExtra(BlockOverlayActivity.EXTRA_BLOCKED_PACKAGE, blockedPkg)
            putExtra(BlockOverlayActivity.EXTRA_IS_TAMPER, isTamperAttempt)
        }
        startActivity(overlayIntent)
    }

    override fun onInterrupt() {
        Log.w(TAG, "FocusAccessibilityService interrupted")
    }

    companion object {
        private const val TAG = "FocusAccessibility"
    }
}
