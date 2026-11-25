package com.litephone.launcher

import android.content.Context
import java.text.DateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.util.Date

/**
 * The morning card (5 AM to noon) and evening card (6 PM to midnight) on the home
 * screen: weather, first thing today or tomorrow, what's due, and screen time.
 */
object DayCard {
    private const val K_DISMISSED = "daycard_dismissed"

    class Card(val key: String, val title: String, val lines: List<String>)

    private fun periodKey(): String? {
        val h = LocalTime.now().hour
        val p = when (h) { in 5..11 -> "am"; in 18..23 -> "pm"; else -> return null }
        return "${LocalDate.now()}-$p"
    }

    fun dismiss(ctx: Context, key: String) = Prefs.get(ctx).edit().putString(K_DISMISSED, key).apply()

    /** Background thread. Null when there's no card right now. */
    fun build(ctx: Context): Card? {
        if (!Look.dayCards(ctx)) return null
        val key = periodKey() ?: return null
        if (Prefs.get(ctx).getString(K_DISMISSED, null) == key) return null
        val today = LocalDate.now()
        val agenda = try { Agenda.load(ctx).items } catch (e: Exception) { emptyList() }
        val timeFmt = DateFormat.getTimeInstance(DateFormat.SHORT)
        fun describe(i: Agenda.Item) = (if (i.allDay) Look.t(ctx, "All day", "終日") else timeFmt.format(Date(i.time))) + " · " + i.title
        val lines = ArrayList<String>()

        return if (key.endsWith("am")) {
            Weather.cached(ctx)?.let { w ->
                val (icon, words) = Weather.describe(w.code, w.isDay)
                lines += "$icon ${w.temp}° · $words · H ${w.hi}° L ${w.lo}°"
            }
            val now = System.currentTimeMillis()
            agenda.firstOrNull { it.day == today && !it.isCanvas && (it.allDay || it.time >= now) }?.let {
                lines += Look.t(ctx, "First up: ", "最初: ") + describe(it)
            }
            val due = agenda.filter { it.day == today && it.isCanvas }
            if (due.isNotEmpty()) lines += Look.t(ctx, "Due today: ", "今日の締め切り: ") + due.joinToString(", ") { it.title }
            if (lines.isEmpty()) lines += Look.t(ctx, "Nothing scheduled. Have a good one.", "予定なし。いい一日を。")
            Card(key, Look.t(ctx, "Good morning", "おはようございます"), lines)
        } else {
            if (UsageReport.hasAccess(ctx)) {
                val start = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                val r = UsageReport.compute(ctx, start, System.currentTimeMillis())
                val pickups = if (r.pickups > 0) " · ${r.pickups} " + Look.t(ctx, "pickups", "回") else ""
                lines += Look.t(ctx, "Screen time today: ", "今日の使用時間: ") + Ui.formatDuration(r.total) + pickups
            }
            val tomorrow = today.plusDays(1)
            agenda.firstOrNull { it.day == tomorrow && !it.isCanvas }?.let {
                lines += Look.t(ctx, "Tomorrow: ", "明日: ") + describe(it)
            }
            val due = agenda.filter { it.day == tomorrow && it.isCanvas }
            if (due.isNotEmpty()) lines += Look.t(ctx, "Due tomorrow: ", "明日の締め切り: ") + due.joinToString(", ") { it.title }
            if (lines.isEmpty()) lines += Look.t(ctx, "Nothing on tomorrow.", "明日の予定はありません。")
            Card(key, Look.t(ctx, "Tonight", "今夜"), lines)
        }
    }
}
