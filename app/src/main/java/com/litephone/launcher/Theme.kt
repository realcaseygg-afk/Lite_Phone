package com.litephone.launcher

import android.content.Context
import android.os.SystemClock
import java.time.LocalTime

/**
 * Dark or "paper" (warm cream, serif) look. In Auto, paper runs from sunrise to
 * sunset where you are (from the weather), or 7 AM to 7 PM before that's known.
 */
object Theme {
    @Volatile var app: Context? = null
    private var checkedAt = 0L
    private var cached = false

    /** True while the paper (light) look is in use. Re-checked every 30 seconds. */
    val paper: Boolean
        get() {
            val now = SystemClock.elapsedRealtime()
            if (now - checkedAt > 30_000 || checkedAt == 0L) {
                cached = compute()
                checkedAt = now
            }
            return cached
        }

    /** Call after changing the theme setting so it applies straight away. */
    fun invalidate() {
        checkedAt = 0L
    }

    private fun compute(): Boolean {
        val ctx = app ?: return false
        return when (Look.theme(ctx)) {
            "paper" -> true
            "auto" -> isDaytime(ctx)
            else -> false
        }
    }

    fun isDaytime(ctx: Context): Boolean {
        val (rise, set) = Weather.sunTimes(ctx) ?: (7 * 60 to 19 * 60)
        val now = LocalTime.now().let { it.hour * 60 + it.minute }
        return now in rise until set
    }

    /** A darker version of a colour, so light accents stay readable on paper. */
    fun darken(color: Int, f: Float = 0.55f): Int {
        val r = ((color shr 16 and 0xFF) * f).toInt()
        val g = ((color shr 8 and 0xFF) * f).toInt()
        val b = ((color and 0xFF) * f).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
