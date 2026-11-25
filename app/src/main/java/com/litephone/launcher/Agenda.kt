package com.litephone.launcher

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The Agenda page: the next 7 days of calendar events (read from the phone's
 * calendars) merged with Canvas assignments that are due and not yet submitted.
 */
object Agenda {
    private const val K_CANVAS_DOMAIN = "canvas_domain"
    private const val K_CANVAS_TOKEN = "canvas_token"
    private const val DAYS = 7
    private const val CANVAS_STALE_MS = 15 * 60 * 1000L
    const val CANVAS_PKG = "com.instructure.candroid"

    class Item(
        val day: LocalDate,
        val time: Long,              // for sorting and display; 0 for all-day
        val allDay: Boolean,
        val title: String,
        val subtitle: String,
        val isCanvas: Boolean,
        val color: Int,
        val open: Intent?,
    )

    // ---- Canvas connection ----

    fun canvasDomain(ctx: Context): String? = Prefs.get(ctx).getString(K_CANVAS_DOMAIN, null)
    fun canvasConnected(ctx: Context) = canvasDomain(ctx) != null && Prefs.get(ctx).getString(K_CANVAS_TOKEN, null) != null

    /** Saves the school address (e.g. myschool.instructure.com) and access token. */
    fun setCanvas(ctx: Context, domainInput: String, token: String) {
        val domain = domainInput.trim()
            .removePrefix("https://").removePrefix("http://")
            .substringBefore('/').lowercase()
        Prefs.get(ctx).edit().putString(K_CANVAS_DOMAIN, domain).putString(K_CANVAS_TOKEN, token.trim()).apply()
        canvasCache = null
    }

    fun disconnectCanvas(ctx: Context) {
        Prefs.get(ctx).edit().remove(K_CANVAS_DOMAIN).remove(K_CANVAS_TOKEN).apply()
        canvasCache = null
    }

    fun hasCalendarPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    // ---- Loading ----

    class Result(val items: List<Item>, val canvasError: String?)

    @Volatile private var canvasCache: Pair<Long, List<Item>>? = null

    /** Runs on a background thread. Calendar is read fresh; Canvas is cached for 15 minutes. */
    fun load(ctx: Context, forceCanvas: Boolean = false): Result {
        val items = ArrayList<Item>()
        if (hasCalendarPermission(ctx)) items += calendarItems(ctx)
        var canvasError: String? = null
        if (canvasConnected(ctx)) {
            val cached = canvasCache
            if (!forceCanvas && cached != null && System.currentTimeMillis() - cached.first < CANVAS_STALE_MS) {
                items += cached.second
            } else {
                try {
                    val fresh = canvasItems(ctx)
                    canvasCache = System.currentTimeMillis() to fresh
                    items += fresh
                } catch (e: Exception) {
                    canvasError = e.message ?: "Couldn't reach Canvas"
                    cached?.let { items += it.second }
                }
            }
        }
        items.sortWith(compareBy<Item>({ it.day }, { !it.allDay }, { it.time }))
        return Result(items, canvasError)
    }

    private fun calendarItems(ctx: Context): List<Item> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(DAYS.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start)
            ContentUris.appendId(it, end)
        }.build()
        val cols = arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE, CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR,
        )
        val out = ArrayList<Item>()
        try {
            ctx.contentResolver.query(
                uri, cols, "${CalendarContract.Instances.VISIBLE}=1", null, "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val begin = c.getLong(1)
                    val finish = c.getLong(2)
                    val allDay = c.getInt(5) == 1
                    if (!allDay && finish < now) continue // already over
                    // All-day events are stored as UTC midnight.
                    val day = if (allDay) Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
                    else Instant.ofEpochMilli(begin).atZone(zone).toLocalDate()
                    if (day.isBefore(today)) continue
                    val open = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id))
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, finish)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    out += Item(
                        day = day, time = if (allDay) 0 else begin, allDay = allDay,
                        title = c.getString(3)?.takeIf { it.isNotBlank() } ?: "(No title)",
                        subtitle = c.getString(4) ?: "",
                        isCanvas = false,
                        color = (c.getInt(6).takeIf { it != 0 } ?: 0xFF7FB2FF.toInt()) or 0xFF000000.toInt(),
                        open = open,
                    )
                }
            }
        } catch (e: Exception) { }
        return out
    }

    /** Canvas planner items due in the next 7 days that aren't submitted or marked done. */
    private fun canvasItems(ctx: Context): List<Item> {
        val sp = Prefs.get(ctx)
        val domain = sp.getString(K_CANVAS_DOMAIN, null) ?: return emptyList()
        val token = sp.getString(K_CANVAS_TOKEN, null) ?: return emptyList()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant()
        val end = today.plusDays(DAYS.toLong()).atStartOfDay(zone).toInstant()
        val url = "https://$domain/api/v1/planner/items?per_page=100&start_date=$start&end_date=$end"
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        c.setRequestProperty("Authorization", "Bearer $token")
        c.setRequestProperty("Accept", "application/json")
        val body = try {
            when (c.responseCode) {
                in 200..299 -> c.inputStream.bufferedReader().use { it.readText() }
                401 -> throw Exception("Canvas rejected the token. Make a new one and reconnect.")
                else -> throw Exception("Canvas error ${c.responseCode}")
            }
        } finally {
            c.disconnect()
        }
        val arr = JSONArray(body)
        val out = ArrayList<Item>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val type = o.optString("plannable_type")
            if (type !in setOf("assignment", "quiz", "discussion_topic", "sub_assignment")) continue
            val subs = o.opt("submissions")
            if (subs is org.json.JSONObject && (subs.optBoolean("submitted") || subs.optBoolean("excused"))) continue
            if (o.optJSONObject("planner_override")?.optBoolean("marked_complete") == true) continue
            val date = o.optString("plannable_date").takeIf { it.isNotBlank() } ?: continue
            val due = try { Instant.parse(date) } catch (e: Exception) { continue }
            val p = o.optJSONObject("plannable")
            val title = p?.optString("title")?.takeIf { it.isNotBlank() }
                ?: p?.optString("name")?.takeIf { it.isNotBlank() } ?: "Assignment"
            val course = o.optString("context_name")
            val path = o.optString("html_url")
            val open = Intent(Intent.ACTION_VIEW, Uri.parse("https://$domain$path"))
                .setPackage(CANVAS_PKG)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            out += Item(
                day = due.atZone(zone).toLocalDate(),
                time = due.toEpochMilli(),
                allDay = false,
                title = title,
                subtitle = listOf(course, "Canvas").filter { it.isNotBlank() }.joinToString(" · "),
                isCanvas = true,
                color = 0xFFFF8A65.toInt(),
                open = open,
            )
        }
        return out
    }
}
