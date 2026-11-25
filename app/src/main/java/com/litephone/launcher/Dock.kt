package com.litephone.launcher

import android.content.Context

/**
 * The four apps in the home screen dock: your most-used allowed apps over the
 * last 14 days (from usage tracking), topped up with the first allowed apps
 * when there isn't enough history. Recomputed at most once an hour.
 */
object Dock {
    private const val K_APPS = "dock_apps"
    private const val K_AT = "dock_at"
    private const val SIZE = 4
    private const val REFRESH_MS = 60 * 60 * 1000L

    /** Last computed dock (may be empty before the first run). */
    fun cached(ctx: Context): List<Policy.AppEntry> {
        val pkgs = (Prefs.get(ctx).getString(K_APPS, null) ?: "").split(',').filter { it.isNotBlank() }
        val allowed = Prefs.allowedList(ctx).toSet()
        return pkgs.filter { it in allowed }.mapNotNull { Policy.entryFor(ctx, it) }
    }

    fun isStale(ctx: Context) =
        System.currentTimeMillis() - Prefs.get(ctx).getLong(K_AT, 0) > REFRESH_MS || cached(ctx).size < SIZE

    /** Heavy-ish (reads usage history): call off the main thread. */
    fun compute(ctx: Context): List<Policy.AppEntry> {
        val allowed = Policy.allowedEntries(ctx)
        val byPkg = allowed.associateBy { it.pkg }
        val ranked = if (UsageReport.hasAccess(ctx)) {
            val now = System.currentTimeMillis()
            UsageReport.compute(ctx, now - 14L * 24 * 60 * 60 * 1000, now).perApp
                .filterKeys { it in byPkg }
                .entries.sortedByDescending { it.value }
                .map { it.key }
        } else emptyList()
        val picks = (ranked + allowed.map { it.pkg }).distinct().take(SIZE)
        Prefs.get(ctx).edit()
            .putString(K_APPS, picks.joinToString(","))
            .putLong(K_AT, System.currentTimeMillis())
            .apply()
        return picks.mapNotNull { byPkg[it] }
    }
}
