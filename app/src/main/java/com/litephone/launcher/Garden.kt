package com.litephone.launcher

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId

/**
 * The little garden under the clock. Each day under your screen-time goal grows
 * a flower; a day over it wilts. Today is a seedling until the day is done.
 */
object Garden {
    private const val PREFIX = "garden:"
    private val FLOWERS = listOf("🌷", "🌻", "🌼", "🌸", "🌺", "🪻", "🌹")
    private const val WILTED = "🥀"
    private const val SEEDLING = "🌱"
    private const val FALLEN = "🍂"

    class State(val plants: String, val streak: Int, val todayMinutes: Long, val goal: Int)

    /** Background thread: reads screen time for the last 7 days (past days are cached). */
    fun compute(ctx: Context): State? {
        if (!UsageReport.hasAccess(ctx)) return null
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val goal = Look.gardenGoal(ctx)
        val sp = Prefs.get(ctx)
        fun minutesFor(day: LocalDate): Long {
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = if (day == today) System.currentTimeMillis() else day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            return UsageReport.compute(ctx, start, end).total / 60_000
        }
        val past = (6 downTo 1).map { ago ->
            val day = today.minusDays(ago.toLong())
            val key = PREFIX + day
            val cached = sp.getLong(key, -1)
            day to if (cached >= 0) cached else minutesFor(day).also { sp.edit().putLong(key, it).apply() }
        }
        val todayMin = minutesFor(today)
        val plants = buildString {
            for ((day, m) in past) append(if (m <= goal) FLOWERS[(day.toEpochDay() % FLOWERS.size).toInt()] else WILTED)
            append(if (todayMin <= goal) SEEDLING else FALLEN)
        }
        var streak = 0
        for ((_, m) in past.reversed()) { if (m <= goal) streak++ else break }
        if (todayMin <= goal) streak++ // today counts while you're under the goal
        // Clean up old days so the cache stays small.
        sp.all.keys.filter { it.startsWith(PREFIX) && it.removePrefix(PREFIX) < today.minusDays(14).toString() }
            .forEach { sp.edit().remove(it).apply() }
        return State(plants, streak, todayMin, goal)
    }
}
