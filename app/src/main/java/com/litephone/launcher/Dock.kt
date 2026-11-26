package com.litephone.launcher

import android.content.Context

/**
 * The four apps in the home screen dock. Either your most-used allowed apps over
 * the last 14 days (from usage tracking, topped up with the first allowed apps
 * and recomputed at most once an hour), or four apps you pick yourself.
 */
object Dock {
    private const val K_APPS = "dock_apps"
    private const val K_AT = "dock_at"
    private const val SIZE = 4
    private const val REFRESH_MS = 60 * 60 * 1000L
    private const val K_MODE = "dock_mode"      // "used" | "fixed"
    private const val K_FIXED = "dock_fixed"    // 4 slots, "" = empty
    const val SLOTS = SIZE

    fun isFixed(ctx: Context) = Prefs.get(ctx).getString(K_MODE, "used") == "fixed"

    fun setFixed(ctx: Context, fixed: Boolean) {
        val sp = Prefs.get(ctx)
        // First switch to fixed: start from what the dock shows now.
        if (fixed && !sp.contains(K_FIXED)) {
            val now = cached(ctx).ifEmpty { Policy.allowedEntries(ctx).take(SIZE) }.map { it.pkg }
            sp.edit().putString(K_FIXED, (now + List(SIZE) { "" }).take(SIZE).joinToString(",")).apply()
        }
        sp.edit().putString(K_MODE, if (fixed) "fixed" else "used").apply()
    }

    /** The 4 fixed slots as package names ("" for an empty slot). */
    fun fixedSlots(ctx: Context): List<String> {
        val saved = (Prefs.get(ctx).getString(K_FIXED, null) ?: "").split(',')
        return (saved + List(SIZE) { "" }).take(SIZE)
    }

    fun setSlot(ctx: Context, index: Int, pkg: String) {
        val slots = fixedSlots(ctx).toMutableList()
        if (index !in slots.indices) return
        // An app can only be in one slot.
        for (i in slots.indices) if (slots[i] == pkg && pkg.isNotEmpty()) slots[i] = ""
        slots[index] = pkg
        Prefs.get(ctx).edit().putString(K_FIXED, slots.joinToString(",")).apply()
    }

    /** What the dock shows right now. */
    fun apps(ctx: Context): List<Policy.AppEntry> {
        if (isFixed(ctx)) {
            val allowed = Prefs.allowedList(ctx).toSet()
            return fixedSlots(ctx).filter { it.isNotEmpty() && it in allowed }.mapNotNull { Policy.entryFor(ctx, it) }
        }
        return cached(ctx).ifEmpty { Policy.allowedEntries(ctx).take(SIZE) }
    }

    /** Last computed dock (may be empty before the first run). */
    fun cached(ctx: Context): List<Policy.AppEntry> {
        val pkgs = (Prefs.get(ctx).getString(K_APPS, null) ?: "").split(',').filter { it.isNotBlank() }
        val allowed = Prefs.allowedList(ctx).toSet()
        return pkgs.filter { it in allowed }.mapNotNull { Policy.entryFor(ctx, it) }
    }

    fun isStale(ctx: Context) = !isFixed(ctx) &&
        (System.currentTimeMillis() - Prefs.get(ctx).getLong(K_AT, 0) > REFRESH_MS || cached(ctx).size < SIZE)

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
