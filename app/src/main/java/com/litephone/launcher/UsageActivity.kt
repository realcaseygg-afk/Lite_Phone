package com.litephone.launcher

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** "My use": screen time, pickups and blocked attempts. Just for the phone's owner. */
class UsageActivity : Activity() {

    override fun onResume() {
        super.onResume()
        val loading = Ui.column(this).apply {
            val p = Ui.dp(this@UsageActivity, 24)
            setPadding(p, p, p, p)
            addView(Ui.text(this@UsageActivity, "My use", 28f, bold = true))
            addView(Ui.text(this@UsageActivity, "Loading…", 15f, Ui.MUTED), Ui.lp(this@UsageActivity, 8))
        }
        Ui.showScrolling(this, loading)
        if (!UsageReport.hasAccess(this)) {
            showNoAccess(); return
        }
        Thread {
            val data = load()
            runOnUiThread { if (!isFinishing) show(data) }
        }.start()
    }

    private class Data(
        val today: UsageReport.Result,
        val days: List<Pair<Long, UsageReport.Result>>,   // oldest first, includes today
        val blockedToday: Int,
        val blockedWeek: List<Pair<String, Int>>,
    )

    private fun startOfDay(daysAgo: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -daysAgo)
    }.timeInMillis

    private fun load(): Data {
        val now = System.currentTimeMillis()
        val days = (6 downTo 0).map { ago ->
            val begin = startOfDay(ago)
            val end = if (ago == 0) now else startOfDay(ago - 1)
            begin to UsageReport.compute(this, begin, end)
        }
        val blocked = BlockLog.since(this, startOfDay(6))
        val blockedToday = blocked.count { it.first >= startOfDay(0) }
        val byApp = blocked.groupingBy { it.second }.eachCount().toList().sortedByDescending { it.second }
        return Data(days.last().second, days, blockedToday, byApp)
    }

    private fun show(d: Data) {
        val c = Ui.column(this).apply {
            val p = Ui.dp(this@UsageActivity, 24)
            setPadding(p, p, p, p)
        }
        c.addView(Ui.text(this, "My use", 28f, bold = true))

        // Today summary
        c.addView(Ui.text(this, "TODAY", 13f, Ui.MUTED, bold = true), Ui.lp(this, 24))
        c.addView(Ui.text(this, Ui.formatDuration(d.today.total), 44f).apply {
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        }, Ui.lp(this, 2))
        val stats = mutableListOf<String>()
        if (d.today.pickups > 0) stats.add("${d.today.pickups} pickups")
        stats.add("${d.blockedToday} blocked ${if (d.blockedToday == 1) "attempt" else "attempts"}")
        c.addView(Ui.text(this, stats.joinToString("  ·  "), 15f, Ui.MUTED), Ui.lp(this, 2))
        appBars(c, d.today.perApp)

        // Week
        val week = d.days.sumOf { it.second.total }
        c.addView(Ui.text(this, "LAST 7 DAYS", 13f, Ui.MUTED, bold = true), Ui.lp(this, 32))
        c.addView(Ui.text(
            this, "${Ui.formatDuration(week)} total · ${Ui.formatDuration(week / 7)} a day on average", 16f
        ), Ui.lp(this, 4))
        val maxDay = d.days.maxOf { it.second.total }.coerceAtLeast(1)
        val dayName = SimpleDateFormat("EEE", Locale.getDefault())
        for ((start, r) in d.days) {
            c.addView(bar(dayName.format(start), Ui.formatDuration(r.total), r.total.toFloat() / maxDay), Ui.lp(this, 6))
        }
        val weekApps = HashMap<String, Long>()
        d.days.forEach { (_, r) -> r.perApp.forEach { (p, t) -> weekApps[p] = (weekApps[p] ?: 0L) + t } }
        c.addView(Ui.text(this, "Most used this week", 15f, Ui.MUTED), Ui.lp(this, 16))
        appBars(c, weekApps)

        // Blocked
        c.addView(Ui.text(this, "BLOCKED APPS TRIED (7 DAYS)", 13f, Ui.MUTED, bold = true), Ui.lp(this, 32))
        if (d.blockedWeek.isEmpty()) {
            c.addView(Ui.text(this, "None — nice.", 16f), Ui.lp(this, 4))
        } else {
            for ((pkg, n) in d.blockedWeek.take(10)) {
                c.addView(Ui.text(this, "${UsageReport.label(this, pkg)}  ·  $n ${if (n == 1) "time" else "times"}", 16f), Ui.lp(this, 4))
            }
        }

        c.addView(Ui.button(this, "Back", Ui.BG, Ui.MUTED) { finish() }, Ui.lp(this, 28))
        Ui.showScrolling(this, c)
    }

    private fun appBars(parent: LinearLayout, perApp: Map<String, Long>) {
        val top = perApp.filterValues { it >= 60_000 }.toList().sortedByDescending { it.second }.take(10)
        if (top.isEmpty()) {
            parent.addView(Ui.text(this, "Nothing yet.", 15f, Ui.MUTED), Ui.lp(this, 8))
            return
        }
        val max = top.first().second.toFloat()
        for ((pkg, t) in top) {
            parent.addView(bar(UsageReport.label(this, pkg), Ui.formatDuration(t), t / max), Ui.lp(this, 8))
        }
    }

    /** Label and time on one line, a thin bar under it. */
    private fun bar(label: String, value: String, fraction: Float): View {
        val col = Ui.column(this)
        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        line.addView(Ui.text(this, label, 16f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        line.addView(Ui.text(this, value, 16f, Ui.MUTED))
        col.addView(line)
        val track = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Ui.rounded(Ui.SURFACE, Ui.dp(this@UsageActivity, 3).toFloat())
        }
        val f = fraction.coerceIn(0.02f, 1f)
        track.addView(View(this).apply {
            background = Ui.rounded(Ui.ACCENT, Ui.dp(this@UsageActivity, 3).toFloat())
        }, LinearLayout.LayoutParams(0, Ui.dp(this, 6), f))
        track.addView(View(this), LinearLayout.LayoutParams(0, Ui.dp(this, 6), 1f - f))
        col.addView(track, Ui.lp(this, 4))
        return col
    }

    private fun showNoAccess() {
        val c = Ui.column(this).apply {
            val p = Ui.dp(this@UsageActivity, 24)
            setPadding(p, p, p, p)
        }
        c.addView(Ui.text(this, "My use", 28f, bold = true))
        c.addView(Ui.text(
            this,
            "Usage tracking isn't turned on yet. It needs a Settings page that's locked, so the passcode holder " +
                "turns it on once: Passcode → Protection checklist → Turn on usage tracking.",
            16f, Ui.MUTED
        ), Ui.lp(this, 12))
        c.addView(Ui.button(this, "Back", Ui.BG, Ui.MUTED) { finish() }, Ui.lp(this, 24))
        Ui.showScrolling(this, c)
    }
}
