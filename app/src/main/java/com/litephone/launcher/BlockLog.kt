package com.litephone.launcher

import android.content.Context
import java.io.File

/** Remembers when a blocked app was tried, for the "My use" screen. Kept 30 days. */
object BlockLog {
    private const val FILE = "blocked.log"
    private const val KEEP_MS = 30L * 24 * 60 * 60 * 1000
    private var lastPkg: String? = null
    private var lastAt = 0L

    fun record(ctx: Context, pkg: String) {
        val now = System.currentTimeMillis()
        if (pkg == lastPkg && now - lastAt < 10_000) return // one attempt, not every bounce
        lastPkg = pkg
        lastAt = now
        try {
            val f = File(ctx.filesDir, FILE)
            f.appendText("$now\t$pkg\n")
            if (f.length() > 64 * 1024) {
                val keep = read(ctx).filter { it.first > now - KEEP_MS }
                f.writeText(keep.joinToString("") { "${it.first}\t${it.second}\n" })
            }
        } catch (e: Exception) { }
    }

    /** (time, package) of each blocked attempt since [sinceMs]. */
    fun since(ctx: Context, sinceMs: Long): List<Pair<Long, String>> =
        read(ctx).filter { it.first >= sinceMs }

    private fun read(ctx: Context): List<Pair<Long, String>> = try {
        File(ctx.filesDir, FILE).takeIf { it.exists() }?.readLines()?.mapNotNull { line ->
            val parts = line.split('\t')
            val t = parts.getOrNull(0)?.toLongOrNull()
            val p = parts.getOrNull(1)
            if (t != null && !p.isNullOrBlank()) t to p else null
        } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}
