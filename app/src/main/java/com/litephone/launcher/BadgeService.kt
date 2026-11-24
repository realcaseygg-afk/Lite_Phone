package com.litephone.launcher

import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.telecom.TelecomManager

/**
 * Counts each app's notifications so the home screen can show missed calls,
 * unread texts and so on. Nothing is stored or sent anywhere.
 */
class BadgeService : NotificationListenerService() {

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

    override fun onListenerConnected() = recount()
    override fun onListenerDisconnected() = publish(emptyMap())
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
        val map = HashMap<String, Int>()
        for (sbn in active) {
            if (!sbn.isClearable) continue // ongoing calls, music players, downloads
            val n = sbn.notification
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
            if (n.category == Notification.CATEGORY_TRANSPORT) continue
            if (n.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true) continue
            var pkg = sbn.packageName
            if (pkg in CALL_PACKAGES && dialer != null) pkg = dialer
            map[pkg] = (map[pkg] ?: 0) + maxOf(1, n.number)
        }
        publish(map)
    }
}
