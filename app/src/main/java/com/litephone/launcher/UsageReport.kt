package com.litephone.launcher

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process

/** Screen time per app, worked out from Android's own usage events. */
object UsageReport {
    // Event type numbers (constant across Android versions).
    private const val RESUMED = 1
    private const val PAUSED = 2
    private const val SCREEN_OFF = 16      // Android 9+
    private const val UNLOCKED = 18        // Android 9+ (keyguard hidden)

    private val IGNORED = setOf("android", "com.android.systemui")

    class Result(val perApp: Map<String, Long>, val pickups: Int) {
        val total: Long get() = perApp.values.sum()
    }

    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            ctx.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    fun compute(ctx: Context, begin: Long, end: Long): Result {
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return Result(emptyMap(), 0)
        val events = usm.queryEvents(begin, end) ?: return Result(emptyMap(), 0)
        val open = HashMap<String, Long>()   // "package/activity" -> resumed at
        val totals = HashMap<String, Long>()
        var pickups = 0
        val e = UsageEvents.Event()

        fun close(key: String, at: Long) {
            val start = open.remove(key) ?: return
            val pkg = key.substringBefore('/')
            if (at > start) totals[pkg] = (totals[pkg] ?: 0L) + (at - start)
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            val key = "$pkg/${e.className}"
            when (e.eventType) {
                RESUMED -> open[key] = e.timeStamp
                PAUSED -> close(key, e.timeStamp)
                SCREEN_OFF -> open.keys.toList().forEach { close(it, e.timeStamp) }
                UNLOCKED -> pickups++
            }
        }
        // Whatever is still open is on screen right now.
        open.keys.toList().forEach { close(it, end) }

        val own = ctx.packageName
        return Result(totals.filterKeys { it != own && it !in IGNORED && it != "" }, pickups)
    }

    fun label(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }
}
