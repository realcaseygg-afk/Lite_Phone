package com.litephone.launcher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast

/**
 * Watches which app comes to the front. If it isn't allowed, sends the phone
 * straight back to the Lite Phone home screen. This catches every way of
 * opening an app: notifications, links, voice assistants, edge panels, etc.
 */
class GuardService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val handler = Handler(Looper.getMainLooper())
    private val recheck = Runnable { enforceCurrentApp() }
    private var lastToastAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Prefs.get(this).registerOnSharedPreferenceChangeListener(this)
        scheduleRecheck()
    }

    override fun onDestroy() {
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(this)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onInterrupt() {}

    /** Settings changed (locked, unlocked, allow list edited): re-check right away. */
    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        scheduleRecheck()
    }

    private fun scheduleRecheck() {
        handler.removeCallbacks(recheck)
        handler.postDelayed(recheck, 300)
        // When a temporary unlock runs out, close whatever is open.
        val remaining = Policy.pauseRemainingMs(this)
        if (remaining > 0) handler.postDelayed(recheck, remaining + 500)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString()
        if (!Policy.isEnforcing(this)) return
        if (Policy.isAllowed(this, pkg, cls)) return
        // Only act on real app screens, not small overlays or pop-ups.
        if (isActivity(pkg, cls) || activeAppPackage() == pkg) block(pkg)
    }

    private fun enforceCurrentApp() {
        if (!Policy.isEnforcing(this)) return
        val pkg = activeAppPackage() ?: return
        if (!Policy.isAllowed(this, pkg, null)) block(pkg)
    }

    @Suppress("DEPRECATION")
    private fun isActivity(pkg: String, cls: String?): Boolean {
        if (cls.isNullOrEmpty()) return false
        return try {
            packageManager.getActivityInfo(ComponentName(pkg, cls), 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /** Package of the full app window the user is looking at, if any. */
    private fun activeAppPackage(): String? {
        val list = try {
            windows
        } catch (e: Exception) {
            return null
        }
        for (w in list) {
            if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION && (w.isActive || w.isFocused)) {
                return w.root?.packageName?.toString()
            }
        }
        return null
    }

    private fun block(pkg: String) {
        if (Policy.isDefaultHome(this)) performGlobalAction(GLOBAL_ACTION_HOME)
        try {
            startActivity(
                Intent(this, LauncherActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
        } catch (e: Exception) { }

        val now = SystemClock.elapsedRealtime()
        if (now - lastToastAt > 3000) {
            lastToastAt = now
            Toast.makeText(this, "${labelOf(pkg)} isn't allowed on Lite Phone", Toast.LENGTH_SHORT).show()
        }
        // Some apps open a second screen right after the first; catch that too.
        handler.postDelayed(recheck, 800)
    }

    private fun labelOf(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        "That app"
    }
}
