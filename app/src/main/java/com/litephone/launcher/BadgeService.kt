package com.litephone.launcher

import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.telecom.TelecomManager

/**
 * Counts missed calls and unread texts for the home screen bubbles, clears
 * notifications from blocked apps while locked, and clears food app notifications
 * always. Nothing is stored or sent anywhere.
 */
class BadgeService : NotificationListenerService(), android.content.SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        @Volatile
        var counts: Map<String, Int> = emptyMap()
            private set

        private val listeners = LinkedHashSet<() -> Unit>()
        private val main = Handler(Looper.getMainLooper())

        fun addListener(l: () -> Unit) { listeners.add(l) }
        fun removeListener(l: () -> Unit) { listeners.remove(l) }

        // Missed-call notifications can come from these; show them on the Phone app.
        private val CALL_PACKAGES = setOf(
            "com.android.server.telecom", "com.android.phone",
            "com.samsung.android.incallui", "com.android.incallui",
        )

        private fun publish(newCounts: Map<String, Int>) {
            counts = newCounts
            main.post { listeners.toList().forEach { it() } }
        }
    }

    override fun onListenerConnected() {
        Prefs.get(this).registerOnSharedPreferenceChangeListener(this)
        recount()
    }

    override fun onListenerDisconnected() {
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(this)
        publish(emptyMap())
    }

    /** Locked again, or the allowed apps changed: tidy the shade. */
    override fun onSharedPreferenceChanged(sp: android.content.SharedPreferences?, key: String?) {
        main.post { recount() }
    }
    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) = recount()
    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification?) = recount()

    private fun recount() {
        val active = try {
            activeNotifications
        } catch (e: Exception) {
            null
        } ?: return
        val dialer = try {
            getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        } catch (e: Exception) {
            null
        }
        val sms = try {
            android.provider.Telephony.Sms.getDefaultSmsPackage(this)
        } catch (e: Exception) {
            null
        }
        // Count bubbles are only for missed calls and texts.
        val countable = (Policy.PHONE + Policy.MESSAGES + listOfNotNull(dialer, sms)).toSet()
        val enforcing = Policy.isEnforcing(this)
        val map = HashMap<String, Int>()
        for (sbn in active) {
            if (!sbn.isClearable) continue // ongoing calls, music players, downloads
            // Food apps: never any notifications. Blocked apps: none while locked.
            if (Policy.isFoodApp(this, sbn.packageName) || (enforcing && shouldHide(sbn.packageName))) {
                try {
                    cancelNotification(sbn.key)
                } catch (e: Exception) { }
                continue
            }
            val n = sbn.notification
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
            if (n.category == Notification.CATEGORY_TRANSPORT) continue
            if (n.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true) continue
            var pkg = sbn.packageName
            if (pkg in CALL_PACKAGES && dialer != null) pkg = dialer
            if (pkg !in countable) continue
            map[pkg] = (map[pkg] ?: 0) + maxOf(1, n.number)
        }
        publish(map)
    }

    /**
     * Hide notifications only from blocked apps that have an icon, like a social or
     * game app. System notices (updates, battery, USB) have no icon and stay.
     */
    private fun shouldHide(pkg: String): Boolean =
        pkg != packageName && !Policy.isAllowed(this, pkg, null) && Policy.isLaunchable(this, pkg)
}
