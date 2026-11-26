package com.litephone.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Web pass: browsers open for a short time, a limited number of times a day,
 * without the passcode. The passcode holder can switch it off or change the limits.
 */
object WebPass {
    private const val K_ENABLED = "webpass_enabled"
    private const val K_MINUTES = "webpass_minutes"
    private const val K_PER_DAY = "webpass_per_day"
    private const val K_DAY = "webpass_day"
    private const val K_USED = "webpass_used"

    private val active = Deadline("webpass_until", "webpass_boot")

    fun enabled(ctx: Context) = Prefs.get(ctx).getBoolean(K_ENABLED, true)
    fun setEnabled(ctx: Context, on: Boolean) {
        Prefs.get(ctx).edit().putBoolean(K_ENABLED, on).apply()
        if (!on) end(ctx)
    }

    fun minutes(ctx: Context) = Prefs.get(ctx).getInt(K_MINUTES, 30)
    fun setMinutes(ctx: Context, m: Int) = Prefs.get(ctx).edit().putInt(K_MINUTES, m).apply()

    fun perDay(ctx: Context) = Prefs.get(ctx).getInt(K_PER_DAY, 2)
    fun setPerDay(ctx: Context, n: Int) = Prefs.get(ctx).edit().putInt(K_PER_DAY, n).apply()

    const val PLAY_STORE = "com.android.vending"

    /** Whether the Play Store also opens during a web pass (passcode holder's choice). */
    fun includesStore(ctx: Context) = Prefs.get(ctx).getBoolean("webpass_store", true)
    fun setIncludesStore(ctx: Context, on: Boolean) = Prefs.get(ctx).edit().putBoolean("webpass_store", on).apply()

    private fun today() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    fun usedToday(ctx: Context): Int {
        val sp = Prefs.get(ctx)
        return if (sp.getString(K_DAY, null) == today()) sp.getInt(K_USED, 0) else 0
    }

    fun leftToday(ctx: Context) = (perDay(ctx) - usedToday(ctx)).coerceAtLeast(0)

    fun remainingMs(ctx: Context): Long = if (enabled(ctx)) active.remainingMs(ctx) else 0L
    fun isActive(ctx: Context) = remainingMs(ctx) > 0

    fun endText(ctx: Context): String =
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(System.currentTimeMillis() + remainingMs(ctx)))

    /** Starts a pass if one is left today. Returns false if not. */
    fun start(ctx: Context): Boolean {
        if (!enabled(ctx) || isActive(ctx) || leftToday(ctx) <= 0) return false
        Prefs.get(ctx).edit().putString(K_DAY, today()).putInt(K_USED, usedToday(ctx) + 1).apply()
        active.set(ctx, minutes(ctx) * 60_000L)
        return true
    }

    /** End early; the unused time isn't given back. */
    fun end(ctx: Context) = active.clear(ctx)

    // ---- Which apps count as browsers ----------------------------------------

    private var browsersCache: Set<String> = emptySet()
    private var browsersAt = 0L

    /** Apps that open any web link, not just their own site. */
    fun browsers(ctx: Context): Set<String> {
        val now = SystemClock.elapsedRealtime()
        if (now - browsersAt < 60_000 && browsersCache.isNotEmpty()) return browsersCache
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.invalid/"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val found = ctx.packageManager.queryIntentActivities(probe, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .filter { it != "android" && it != ctx.packageName }
            .toSet()
        browsersCache = found
        browsersAt = now
        return found
    }

    /** The phone's default browser (or any browser), ready to open. */
    fun browserIntent(ctx: Context): Intent? {
        val pm = ctx.packageManager
        val def = pm.resolveActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.invalid/")), PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName?.takeIf { it != "android" }
        val pkg = def ?: browsers(ctx).firstOrNull() ?: return null
        return pm.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
