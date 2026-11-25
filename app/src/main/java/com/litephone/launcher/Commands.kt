package com.litephone.launcher

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract

/**
 * The command bar: the home search box also understands
 *   call mom · text sarah running late · timer 10 · alarm 7:30
 *   due / agenda · weather · ?question (or "ask …") for Claude
 */
object Commands {
    const val CLAUDE_PKG = "com.anthropic.claude"

    /** One suggestion row. [run] does it; [needsContacts] means contacts access must be granted first. */
    class Suggestion(val icon: String, val label: String, val needsContacts: Boolean = false, val run: (Context) -> Unit)

    class Hooks(val openAgenda: () -> Unit, val weather: () -> Unit)

    fun hasContacts(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun canCall(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    fun suggestions(ctx: Context, input: String, hooks: Hooks): List<Suggestion> {
        val text = input.trim()
        if (text.isEmpty()) return emptyList()
        val lower = text.lowercase()
        val out = ArrayList<Suggestion>()

        // ?question or "ask …" → Claude
        val question = when {
            text.startsWith("?") -> text.drop(1).trim()
            lower.startsWith("ask ") -> text.drop(4).trim()
            else -> null
        }
        if (question != null) {
            out += Suggestion("✳️", "Ask Claude: ${question.ifBlank { "…" }}") { askClaude(it, question) }
            return out
        }

        // timer 10 / timer 10m / timer 90s / timer 1h
        Regex("""^timer\s+(\d+)\s*(s|sec|secs|m|min|mins|h|hr|hrs)?$""").find(lower)?.let { m ->
            val n = m.groupValues[1].toInt()
            val unit = m.groupValues[2]
            val seconds = when {
                unit.startsWith("s") -> n
                unit.startsWith("h") -> n * 3600
                else -> n * 60
            }
            val label = when {
                unit.startsWith("s") -> "$n sec"
                unit.startsWith("h") -> "$n h"
                else -> "$n min"
            }
            out += Suggestion("⏱️", "Timer $label") { setTimer(it, seconds, label) }
        }

        // alarm 7:30 / alarm 7am / alarm 19:45
        Regex("""^alarm\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?$""").find(lower)?.let { m ->
            var hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
            when (m.groupValues[3]) {
                "pm" -> if (hour < 12) hour += 12
                "am" -> if (hour == 12) hour = 0
            }
            if (hour in 0..23 && minute in 0..59) {
                val shown = String.format("%d:%02d %s", if (hour % 12 == 0) 12 else hour % 12, minute, if (hour < 12) "AM" else "PM")
                out += Suggestion("⏰", "Alarm $shown") { setAlarm(it, hour, minute, shown) }
            }
        }

        if (lower in setOf("due", "agenda", "calendar", "today")) {
            out += Suggestion("📅", "Open the Agenda") { hooks.openAgenda() }
        }
        if (lower == "weather") out += Suggestion("⛅", "Refresh weather") { hooks.weather() }

        // call <name or number> / text <name> [message]
        Regex("""^(call|text|txt|sms)\s+(.+)$""").find(text.lowercase())?.let { m ->
            val isCall = m.groupValues[1] == "call"
            val rest = text.substring(m.groups[2]!!.range.first)
            val digits = rest.filter { it.isDigit() || it == '+' }
            if (digits.length >= 3 && rest.none { it.isLetter() }) {
                out += if (isCall) Suggestion("📞", "Call $rest") { call(it, digits) }
                else Suggestion("💬", "Text $rest") { sms(it, digits, "") }
            } else if (!hasContacts(ctx)) {
                out += Suggestion(if (isCall) "📞" else "💬",
                    "${if (isCall) "Call" else "Text"} “$rest” (tap to allow contacts)", needsContacts = true) { }
            } else {
                // For texts, the longest matching name wins and the rest is the message.
                val words = rest.split(Regex("\\s+"))
                var shown = 0
                for (n in words.size downTo 1) {
                    val name = words.take(n).joinToString(" ")
                    val message = words.drop(n).joinToString(" ")
                    for ((who, number, type) in lookup(ctx, name)) {
                        if (shown >= 3) break
                        shown++
                        out += if (isCall) Suggestion("📞", "Call $who ($type)") { call(it, number) }
                        else Suggestion("💬", "Text $who" + if (message.isNotBlank()) ": $message" else "") { sms(it, number, message) }
                    }
                    if (shown > 0 || isCall) break
                }
                if (shown == 0) out += Suggestion("🔎", "No contact matches “$rest”") { }
            }
        }
        return out
    }

    /** Up to three phone numbers for contacts whose name matches. */
    private fun lookup(ctx: Context, name: String): List<Triple<String, String, String>> {
        val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(name))
        val cols = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
        )
        val out = ArrayList<Triple<String, String, String>>()
        try {
            ctx.contentResolver.query(uri, cols, null, null, null)?.use { c ->
                val seen = HashSet<String>()
                while (c.moveToNext() && out.size < 3) {
                    val who = c.getString(0) ?: continue
                    val number = c.getString(1) ?: continue
                    if (!seen.add(number.filter { it.isDigit() })) continue
                    val type = ContactsContract.CommonDataKinds.Phone.getTypeLabel(ctx.resources, c.getInt(2), c.getString(3)).toString().lowercase()
                    out += Triple(who, number, type)
                }
            }
        } catch (e: Exception) { }
        return out
    }

    private fun start(ctx: Context, i: Intent): Boolean = try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: Exception) {
        false
    }

    private fun toast(ctx: Context, msg: String) =
        android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()

    private fun call(ctx: Context, number: String) {
        val uri = Uri.parse("tel:" + Uri.encode(number))
        if (!(canCall(ctx) && start(ctx, Intent(Intent.ACTION_CALL, uri)))) start(ctx, Intent(Intent.ACTION_DIAL, uri))
    }

    private fun sms(ctx: Context, number: String, message: String) {
        start(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))).putExtra("sms_body", message))
    }

    private fun setTimer(ctx: Context, seconds: Int, label: String) {
        val ok = start(ctx, Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
        toast(ctx, if (ok) "Timer set: $label" else "Couldn't set a timer")
    }

    private fun setAlarm(ctx: Context, hour: Int, minute: Int, shown: String) {
        val ok = start(ctx, Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
        toast(ctx, if (ok) "Alarm set for $shown" else "Couldn't set an alarm")
    }

    /** Shares the question to the Claude app; if that isn't possible, copies it and opens Claude. */
    private fun askClaude(ctx: Context, question: String) {
        if (question.isBlank()) {
            ctx.packageManager.getLaunchIntentForPackage(CLAUDE_PKG)?.let { start(ctx, it) }
            return
        }
        val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, question).setPackage(CLAUDE_PKG)
        if (start(ctx, share)) return
        ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("question", question))
        val open = ctx.packageManager.getLaunchIntentForPackage(CLAUDE_PKG)
        if (open != null && start(ctx, open)) toast(ctx, "Question copied. Paste it into Claude.")
        else toast(ctx, "Claude isn't installed")
    }
}
