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
            sp.edit()
                .putString(KEY_ALLOWED, defaults.joinToString(","))
                .putInt(KEY_DEFAULTS_VERSION, Policy.DEFAULTS_VERSION)
                .apply()
            return defaults
        }
        var list = saved.split(',').filter { it.isNotBlank() }
        // Apps added to the defaults in later versions get added once to existing installs.
        val legacy = if (sp.getBoolean(LEGACY_SETTINGS_ADDED, false)) 2 else 1
        val version = sp.getInt(KEY_DEFAULTS_VERSION, legacy)
        if (version < Policy.DEFAULTS_VERSION) {
            for ((v, pkgs) in Policy.ADDED_IN_VERSION) {
                if (v > version) list = list + pkgs.filter { it !in list }
            }
            sp.edit()
                .putString(KEY_ALLOWED, list.joinToString(","))
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
