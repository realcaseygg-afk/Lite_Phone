package com.litephone.launcher

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings

/** All saved state lives in one private preferences file. */
object Prefs {
    private const val FILE = "lite_phone"
    private const val KEY_ARMED = "armed"
    private const val KEY_ALLOWED = "allowed"
    private const val KEY_DEFAULTS_VERSION = "defaults_version"
    private const val LEGACY_SETTINGS_ADDED = "settings_added_v2"
    private const val KEY_MORE = "more_apps"

    fun get(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** True once setup is finished and blocking is switched on. */
    fun isArmed(ctx: Context): Boolean = get(ctx).getBoolean(KEY_ARMED, false)

    fun setArmed(ctx: Context, armed: Boolean) {
        get(ctx).edit().putBoolean(KEY_ARMED, armed).apply()
    }

    /** Allowed apps, in the order they appear on the home screen. */
    fun allowedList(ctx: Context): List<String> {
        val sp = get(ctx)
        val saved = sp.getString(KEY_ALLOWED, null)
        if (saved == null) {
            val defaults = Policy.defaultAllowedPackages(ctx)
            val more = Policy.ADDED_IN_VERSION.filter { it.inMoreApps }.flatMap { it.packages }
            sp.edit()
                .putString(KEY_ALLOWED, defaults.joinToString(","))
                .putString(KEY_MORE, more.joinToString(","))
                .putInt(KEY_DEFAULTS_VERSION, Policy.DEFAULTS_VERSION)
                .apply()
            return defaults
        }
        var list = saved.split(',').filter { it.isNotBlank() }
        // Apps added to the defaults in later versions get added once to existing installs.
        val legacy = if (sp.getBoolean(LEGACY_SETTINGS_ADDED, false)) 2 else 1
        val version = sp.getInt(KEY_DEFAULTS_VERSION, legacy)
        if (version < Policy.DEFAULTS_VERSION) {
            val more = moreSet(ctx).toMutableSet()
            for (update in Policy.ADDED_IN_VERSION) {
                if (update.version <= version) continue
                val added = update.packages.filter { it !in list }
                list = list + added - update.removed.toSet()
                if (update.inMoreApps) more.addAll(added)
            }
            sp.edit()
                .putString(KEY_ALLOWED, list.joinToString(","))
                .putString(KEY_MORE, more.joinToString(","))
                .putInt(KEY_DEFAULTS_VERSION, Policy.DEFAULTS_VERSION)
                .apply()
        }
        return list
    }

    fun setAllowedList(ctx: Context, packages: List<String>) {
        get(ctx).edit().putString(KEY_ALLOWED, packages.distinct().joinToString(",")).apply()
    }

    fun setAllowed(ctx: Context, pkg: String, allowed: Boolean) {
        val list = allowedList(ctx).toMutableList()
        list.remove(pkg)
        if (allowed) list.add(pkg)
        setAllowedList(ctx, list)
    }

    /** Legacy (builds 4–9 had a "More apps" list); kept so older saved data still reads. */
    fun moreSet(ctx: Context): Set<String> =
        (get(ctx).getString(KEY_MORE, null) ?: "").split(',').filter { it.isNotBlank() }.toSet()


    // ---- Home screen categories ----

    private const val KEY_CATEGORY_ORDER = "category_order"
    private const val CATEGORY_PREFIX = "category:"

    private const val KEY_CUSTOM_CATEGORIES = "custom_categories"

    /** Categories the user made with "New category". */
    fun customCategories(ctx: Context): List<String> =
        (get(ctx).getString(KEY_CUSTOM_CATEGORIES, null) ?: "").split(',').filter { it.isNotBlank() }

    /** Adds a category and returns its cleaned-up name (or null if blank). */
    fun addCustomCategory(ctx: Context, name: String): String? {
        val clean = name.replace(',', ' ').trim().take(30)
        if (clean.isEmpty()) return null
        if (clean in allCategoryNames(ctx)) return clean
        get(ctx).edit().putString(KEY_CUSTOM_CATEGORIES, (customCategories(ctx) + clean).joinToString(",")).apply()
        return clean
    }

    /** Built-in categories plus the user's own, with Other last. */
    fun allCategoryNames(ctx: Context): List<String> =
        (Policy.CATEGORY_NAMES.filter { it != Policy.OTHER } + customCategories(ctx) + Policy.OTHER).distinct()

    /** Category the user moved this app into, if any. */
    fun categoryOverride(ctx: Context, pkg: String): String? =
        get(ctx).getString(CATEGORY_PREFIX + pkg, null)?.takeIf { it in allCategoryNames(ctx) }

    fun setCategory(ctx: Context, pkg: String, category: String) {
        get(ctx).edit().putString(CATEGORY_PREFIX + pkg, category).apply()
    }

    /** All category names in the order shown on the home screen. */
    fun categoryOrder(ctx: Context): List<String> {
        val all = allCategoryNames(ctx)
        val saved = (get(ctx).getString(KEY_CATEGORY_ORDER, null) ?: "").split(',').filter { it in all }
        return (saved + all).distinct()
    }

    /**
     * Saves a new order for the categories shown on screen. Hidden (empty)
     * categories keep their places.
     */
    fun setVisibleCategoryOrder(ctx: Context, visible: List<String>) {
        val order = fillSlots(categoryOrder(ctx), visible)
        get(ctx).edit().putString(KEY_CATEGORY_ORDER, order.joinToString(",")).apply()
    }

    /** Saves a new order for some apps (one category's), keeping everyone else in place. */
    fun reorderApps(ctx: Context, newOrder: List<String>) {
        setAllowedList(ctx, fillSlots(allowedList(ctx), newOrder))
    }

    /** Puts [newOrder] into the positions its items already occupy in [list]. */
    private fun fillSlots(list: List<String>, newOrder: List<String>): List<String> {
        val members = newOrder.toSet()
        val out = list.toMutableList()
        val slots = out.indices.filter { out[it] in members }
        if (slots.size != newOrder.size) return list
        slots.forEachIndexed { k, i -> out[i] = newOrder[k] }
        return out
    }

}

/**
 * A deadline that survives app restarts but not reboots, and can't be stretched
 * by changing the phone's clock (it uses time-since-boot, not wall time).
 */
class Deadline(private val untilKey: String, private val bootKey: String) {
    fun remainingMs(ctx: Context): Long {
        val sp = Prefs.get(ctx)
        if (sp.getInt(bootKey, Int.MIN_VALUE) != bootCount(ctx)) return 0
        return (sp.getLong(untilKey, 0) - SystemClock.elapsedRealtime()).coerceAtLeast(0)
    }

    fun set(ctx: Context, durationMs: Long) {
        Prefs.get(ctx).edit()
            .putInt(bootKey, bootCount(ctx))
            .putLong(untilKey, SystemClock.elapsedRealtime() + durationMs)
            .apply()
    }

    fun clear(ctx: Context) {
        Prefs.get(ctx).edit().putLong(untilKey, 0).apply()
    }

    fun isSameBoot(ctx: Context): Boolean =
        Prefs.get(ctx).getInt(bootKey, Int.MIN_VALUE) == bootCount(ctx)

    companion object {
        fun bootCount(ctx: Context): Int =
            Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT, -1)
    }
}

/** In-memory "passcode was just entered" window for the passcode-holder screens. */
object AdminSession {
    private const val LENGTH_MS = 10 * 60 * 1000L
    private var until = 0L

    fun grant() {
        until = SystemClock.elapsedRealtime() + LENGTH_MS
    }

    fun valid(): Boolean = SystemClock.elapsedRealtime() < until

    fun end() {
        until = 0L
    }
}
