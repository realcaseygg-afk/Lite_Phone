package com.litephone.launcher

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Toast

/** Passcode holder sets their contact info and makes the one-time codes they text back. */
class UnlockCodesActivity : Activity() {

    private var freshCodes: List<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) // no screenshots of the codes
    }

    override fun onResume() {
        super.onResume()
        if (!AdminSession.valid()) {
            finish(); return
        }
        render()
    }

    private fun render() {
        val c = Ui.column(this).apply {
            val p = Ui.dp(this@UnlockCodesActivity, 24)
            setPadding(p, p, p, p)
        }
        c.addView(Ui.text(this, "Texting unlock codes", 28f, bold = true))
        c.addView(Ui.text(
            this,
            "\"Ask to unlock\" on the home screen sends you a text. To say yes, reply with one of your codes. " +
                "Each code works once.",
            15f, Ui.MUTED
        ), Ui.lp(this, 8))

        // Contact
        c.addView(Ui.text(this, "Your name", 15f, Ui.MUTED), Ui.lp(this, 24))
        val name = Ui.input(this, "e.g. Sarah", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
            .apply { setText(Prefs.holderName(this@UnlockCodesActivity)) }
        c.addView(name, Ui.lp(this, 6))
        c.addView(Ui.text(this, "Your mobile number (requests are texted here)", 15f, Ui.MUTED), Ui.lp(this, 12))
        val phone = Ui.input(this, "Phone number", InputType.TYPE_CLASS_PHONE)
            .apply { setText(Prefs.holderPhone(this@UnlockCodesActivity)) }
        c.addView(phone, Ui.lp(this, 6))
        c.addView(Ui.smallButton(this, "Save name and number") {
            Prefs.setHolderContact(this, name.text.toString(), phone.text.toString())
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }, Ui.lp(this, 8))

        // Duration
        c.addView(Ui.text(this, "Each code unlocks all apps for", 15f, Ui.MUTED), Ui.lp(this, 24))
        val durations = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((minutes, label) in listOf(15 to "15 min", 30 to "30 min", 60 to "1 hour")) {
            val selected = UnlockCodes.minutes(this) == minutes
            durations.addView(
                Ui.button(this, label, if (selected) Ui.ACCENT else Ui.SURFACE, if (selected) Ui.BG else Ui.FG) {
                    UnlockCodes.setMinutes(this, minutes); render()
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    rightMargin = Ui.dp(this@UnlockCodesActivity, 8)
                }
            )
        }
        c.addView(durations, Ui.lp(this, 8))

        // Codes
        val left = UnlockCodes.remaining(this)
        c.addView(Ui.text(this, "$left codes left", 20f, if (left > 2) Ui.FG else Ui.WARN, bold = true), Ui.lp(this, 28))
        val codes = freshCodes
        if (codes != null) {
            c.addView(Ui.text(
                this,
                "Write these down or take a photo with YOUR phone now. They won't be shown again.",
                15f, Ui.WARN
            ), Ui.lp(this, 8))
            val box = Ui.column(this).apply {
                background = Ui.rounded(Ui.SURFACE, Ui.dp(this@UnlockCodesActivity, 14).toFloat())
                val p = Ui.dp(this@UnlockCodesActivity, 16)
                setPadding(p, p, p, p)
            }
            for (code in codes) {
                box.addView(Ui.text(this, code.chunked(4).joinToString(" "), 24f).apply {
                    typeface = Typeface.MONOSPACE
                    gravity = Gravity.CENTER
                    setPadding(0, Ui.dp(this@UnlockCodesActivity, 4), 0, Ui.dp(this@UnlockCodesActivity, 4))
                })
            }
            c.addView(box, Ui.lp(this, 10))
        }
        c.addView(Ui.button(this, "Make 10 new codes", Ui.SURFACE, Ui.ACCENT) {
            if (left > 0) {
                AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("Make new codes?")
                    .setMessage("Your $left unused codes will stop working.")
                    .setPositiveButton("Make new codes") { _, _ -> makeCodes() }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                makeCodes()
            }
        }, Ui.lp(this, 12))

        c.addView(Ui.button(this, "Done", Ui.BG, Ui.MUTED) { finish() }, Ui.lp(this, 24))
        Ui.showScrolling(this, c)
    }

    private fun makeCodes() {
        freshCodes = UnlockCodes.generate(this)
        render()
    }
}
