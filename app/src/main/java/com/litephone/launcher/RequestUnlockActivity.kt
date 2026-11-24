package com.litephone.launcher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.Toast

/** "Ask to unlock": text the passcode holder, then type in the one-time code they send back. */
class RequestUnlockActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = Prefs.holderName(this).ifBlank { "the passcode holder" }
        val minutes = UnlockCodes.minutes(this)

        val c = Ui.column(this).apply {
            val p = Ui.dp(this@RequestUnlockActivity, 24)
            setPadding(p, p, p, p)
        }
        c.addView(Ui.text(this, "Ask to unlock", 28f, bold = true))
        c.addView(Ui.text(
            this,
            "Text $name to ask for $minutes minutes with all apps unlocked. " +
                "If they agree, they'll text back a one-time code to enter below.",
            15f, Ui.MUTED
        ), Ui.lp(this, 8))

        val reason = Ui.input(
            this, "What do you need it for? (optional)",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        ).apply { isSingleLine = false; minLines = 2 }
        c.addView(reason, Ui.lp(this, 24))
        c.addView(Ui.button(this, "Text $name", Ui.ACCENT, Ui.BG) {
            sendRequest(reason.text.toString(), minutes)
        }, Ui.lp(this, 12))

        c.addView(Ui.text(this, "Got a code?", 20f, bold = true), Ui.lp(this, 36))
        val code = Ui.input(this, "8-digit code", InputType.TYPE_CLASS_NUMBER)
        c.addView(code, Ui.lp(this, 10))
        val message = Ui.text(this, "", 15f, Ui.MUTED)
        c.addView(message, Ui.lp(this, 8))
        c.addView(Ui.button(this, "Unlock") {
            val wait = Passcode.lockoutRemainingMs(this)
            if (wait > 0) {
                message.setTextColor(Ui.DANGER)
                message.text = "Too many wrong tries. Try again in ${Ui.formatDuration(wait).replace("<1 min", "a minute")}."
                return@button
            }
            val ok = UnlockCodes.use(this, code.text.toString())
            Passcode.recordAttempt(this, ok)
            if (ok) {
                Policy.pauseFor(this, Policy.pauseRemainingMs(this) + minutes * 60_000L)
                Toast.makeText(this, "All apps unlocked until ${Policy.pauseEndText(this)}", Toast.LENGTH_LONG).show()
                finish()
            } else {
                code.setText("")
                message.setTextColor(Ui.DANGER)
                message.text = "That code didn't work. Each code only works once."
            }
        }, Ui.lp(this, 8))

        if (UnlockCodes.remaining(this) == 0) {
            c.addView(Ui.text(
                this,
                "No codes are set up yet. The passcode holder can make some under Passcode → Texting unlock codes.",
                14f, Ui.WARN
            ), Ui.lp(this, 16))
        }
        c.addView(Ui.button(this, "Cancel", Ui.BG, Ui.MUTED) { finish() }, Ui.lp(this, 24))
        Ui.showScrolling(this, c)
    }

    private fun sendRequest(reason: String, minutes: Int) {
        val phone = Prefs.holderPhone(this)
        val body = buildString {
            append("Lite Phone: can you send me an unlock code for $minutes minutes?")
            if (reason.isNotBlank()) append(" It's for: ${reason.trim()}")
        }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(phone)))
            .putExtra("sms_body", body)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open Messages", Toast.LENGTH_SHORT).show()
        }
    }
}
