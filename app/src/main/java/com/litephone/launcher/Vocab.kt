package com.litephone.launcher

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * Word of the day, from your 毎日ノート vocabulary (read from your private
 * Japanese repo with the same read-only GitHub token as Updates). Falls back to a
 * small built-in list until that's available. Refreshed once a week.
 */
object Vocab {
    private const val SOURCE = "https://api.github.com/repos/realcaseygg-afk/Japanese/contents/dist/content.json"
    private const val FILE = "vocab.txt"
    private const val K_FETCHED = "vocab_fetched"
    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    const val NOTEBOOK_PKG = "com.mainichinote.app"

    class Word(val word: String, val reading: String, val meaning: String)

    // A few common N4–N3 words to start with.
    private val BUILT_IN = """
        経験|けいけん|experience
        準備|じゅんび|preparation
        約束|やくそく|promise; appointment
        習慣|しゅうかん|habit; custom
        将来|しょうらい|the future
        最近|さいきん|recently
        必ず|かならず|without fail
        途中|とちゅう|on the way
        意見|いけん|opinion
        理由|りゆう|reason
        結果|けっか|result
        関係|かんけい|relationship
        連絡|れんらく|getting in touch
        相談|そうだん|talking something over
        予定|よてい|plan; schedule
        返事|へんじ|reply
        大切|たいせつ|important; precious
        集中|しゅうちゅう|concentration
        努力|どりょく|effort
        目標|もくひょう|goal
    """.trimIndent()

    private fun parse(raw: String): List<Word> = raw.lines().mapNotNull { line ->
        val p = line.trim().split('|')
        if (p.size >= 3 && p[0].isNotBlank()) Word(p[0].trim(), p[1].trim(), p[2].trim()) else null
    }

    fun words(ctx: Context): List<Word> {
        val f = File(ctx.filesDir, FILE)
        val saved = if (f.exists()) parse(f.readText()) else emptyList()
        return saved.ifEmpty { parse(BUILT_IN) }
    }

    /** The same word all day, a new one each day. */
    fun today(ctx: Context): Word? {
        val list = words(ctx)
        if (list.isEmpty()) return null
        val day = LocalDate.now().toEpochDay()
        return list[((day * 7919) % list.size).toInt().let { if (it < 0) it + list.size else it }]
    }

    fun needsRefresh(ctx: Context) =
        System.currentTimeMillis() - Prefs.get(ctx).getLong(K_FETCHED, 0) > WEEK_MS

    /** Background thread. Pulls VOCAB_RAW from your Japanese app's content file. */
    fun refresh(ctx: Context): Boolean = try {
        val token = Updater.token(ctx)
        val conn = Updater.connect(SOURCE, "application/vnd.github.raw", token)
        val text = try { conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
        val raw = JSONObject(text).optString("VOCAB_RAW")
        val ok = parse(raw).size >= 10
        if (ok) File(ctx.filesDir, FILE).writeText(raw)
        Prefs.get(ctx).edit().putLong(K_FETCHED, System.currentTimeMillis()).apply()
        ok
    } catch (e: Exception) {
        // Try again in a day rather than a week.
        Prefs.get(ctx).edit().putLong(K_FETCHED, System.currentTimeMillis() - WEEK_MS + 24L * 60 * 60 * 1000).apply()
        false
    }
}
