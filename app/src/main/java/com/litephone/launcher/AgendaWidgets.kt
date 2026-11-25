package com.litephone.launcher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

/**
 * App widgets (like Canvas's To Do widget, or a calendar widget) shown on the
 * Agenda page. Only widgets from allowed apps can be added.
 */
object AgendaWidgets {
    const val HOST_ID = 1024
    private const val K_IDS = "agenda_widgets"
    private const val K_HEIGHT = "agenda_widget_h:"
    val HEIGHTS_DP = listOf(180, 280, 380, 480)

    fun ids(ctx: Context): List<Int> =
        (Prefs.get(ctx).getString(K_IDS, null) ?: "").split(',').mapNotNull { it.toIntOrNull() }

    fun add(ctx: Context, id: Int) {
        Prefs.get(ctx).edit().putString(K_IDS, (ids(ctx) + id).distinct().joinToString(",")).apply()
    }

    fun remove(ctx: Context, id: Int) {
        Prefs.get(ctx).edit()
            .putString(K_IDS, (ids(ctx) - id).joinToString(","))
            .remove(K_HEIGHT + id)
            .apply()
    }

    fun heightDp(ctx: Context, id: Int): Int = Prefs.get(ctx).getInt(K_HEIGHT + id, 280)

    fun resize(ctx: Context, id: Int, bigger: Boolean) {
        val i = HEIGHTS_DP.indexOf(heightDp(ctx, id)).coerceAtLeast(0)
        val next = HEIGHTS_DP[(if (bigger) i + 1 else i - 1).coerceIn(0, HEIGHTS_DP.size - 1)]
        Prefs.get(ctx).edit().putInt(K_HEIGHT + id, next).apply()
    }

    /** Widgets offered for adding: only from apps that are allowed to open. */
    fun available(ctx: Context): List<AppWidgetProviderInfo> {
        val mgr = AppWidgetManager.getInstance(ctx)
        val allowed = Prefs.allowedList(ctx).toSet()
        return mgr.installedProviders
            .filter { it.provider.packageName in allowed }
            .sortedWith(compareBy({ it.provider.packageName != Agenda.CANVAS_PKG }, { label(ctx, it).lowercase() }))
    }

    /** "Canvas: To Do" style name for the picker. */
    fun label(ctx: Context, info: AppWidgetProviderInfo): String {
        val pm = ctx.packageManager
        val app = try {
            pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
        } catch (e: Exception) {
            info.provider.packageName
        }
        val widget = info.loadLabel(pm) ?: ""
        return if (widget.isBlank() || widget == app) app else "$app: $widget"
    }

    fun hasCanvasWidget(ctx: Context): Boolean {
        val mgr = AppWidgetManager.getInstance(ctx)
        return ids(ctx).any { mgr.getAppWidgetInfo(it)?.provider?.packageName == Agenda.CANVAS_PKG }
    }
}
