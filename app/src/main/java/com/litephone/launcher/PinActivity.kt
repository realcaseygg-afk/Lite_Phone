package com.litephone.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast

/** Number pad for creating or entering the passcode. */
class PinActivity : Activity() {

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val MODE_CREATE = "create"
        private const val MODE_VERIFY = "verify"
        private const val EXTRA_NEXT = "next"
        private const val NEXT_APPS = "apps"

        /** Ask for the passcode, then open the passcode-holder screen. */
        fun openAdmin(ctx: Context) {
            ctx.startActivity(Intent(ctx, PinActivity::class.java).putExtra(EXTRA_MODE, MODE_VERIFY))
        }

        /** Ask for the passcode, then go straight to choosing allowed apps. */
        fun openAllowedApps(ctx: Context) {
            ctx.startActivity(
                Intent(ctx, PinActivity::class.java).putExtra(EXTRA_MODE, MODE_VERIFY).putExtra(EXTRA_NEXT, NEXT_APPS)
            )
        }

        /** Create a new passcode (first setup, or "Change passcode"). */
        fun create(ctx: Context) {
            ctx.startActivity(Intent(ctx, PinActivity::class.java).putExtra(EXTRA_MODE, MODE_CREATE))
        }
    }

    private val entry = StringBuilder()
    private var firstEntry: String? = null
    private lateinit var title: TextView
    private lateinit var dots: TextView
    private lateinit var message: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val lockoutTicker = object : Runnable {
        override fun run() {
            if (showLockout()) handler.postDelayed(this, 1000)
        }
    }

    private val creating get() = intent.getStringExtra(EXTRA_MODE) == MODE_CREATE
    private var lockoutShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) // no screenshots of this screen

        // Changing an existing passcode needs the current one first.
        if (creating && Passcode.isSet(this) && !AdminSession.valid()) {
            finish(); return
        }
        if (!creating && !Passcode.isSet(this)) {
            finish(); return
        }

        title = Ui.text(this, if (creating) "Choose a passcode" else "Enter passcode", 26f, bold = true)
            .apply { gravity = Gravity.CENTER }
        message = Ui.text(
            this,
            if (creating) "${Passcode.MIN_LENGTH} to ${Passcode.MAX_LENGTH} digits. Only the passcode holder should see this."
            else "Only the passcode holder can unlock apps.",
            15f, Ui.MUTED
        ).apply { gravity = Gravity.CENTER }
        dots = Ui.text(this, " ", 30f).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.3f
            minHeight = Ui.dp(this@PinActivity, 48)
        }

        val pad = GridLayout(this).apply {
            columnCount = 3
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK")
            for (k in keys) addView(key(k))
        }

        val root = Ui.column(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            val p = Ui.dp(this@PinActivity, 24)
            setPadding(p, p * 2, p, p)
            addView(title, Ui.lp(this@PinActivity))
            addView(message, Ui.lp(this@PinActivity, 8))
            addView(dots, Ui.lp(this@PinActivity, 24))
            addView(pad, Ui.lp(this@PinActivity, 16).apply {
                width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                gravity = Gravity.CENTER_HORIZONTAL
            })
            addView(Ui.button(this@PinActivity, "Cancel", Ui.BG, Ui.MUTED) { finish() },
                Ui.lp(this@PinActivity, 16))
        }
        Ui.showScrolling(this, root)
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(lockoutTicker)
        if (!creating) handler.post(lockoutTicker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(lockoutTicker)
    }

    private fun key(label: String): TextView {
        val size = Ui.dp(this, 76)
        val m = Ui.dp(this, 8)
        val bg = if (label == "OK") Ui.ACCENT else Ui.SURFACE
        val fg = if (label == "OK") Ui.BG else Ui.FG
        return Ui.button(this, label, bg, fg) { onKey(label) }.apply {
            textSize = 26f
            setPadding(0, 0, 0, 0)
            background = Ui.rounded(bg, size / 2f).let {
                android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x33FFFFFF), it, null
                )
            }
            layoutParams = GridLayout.LayoutParams().apply {
                width = size; height = size
                setMargins(m, m, m, m)
            }
        }
    }

    private fun onKey(k: String) {
        if (!creating && Passcode.lockoutRemainingMs(this) > 0) return
        when (k) {
            "⌫" -> if (entry.isNotEmpty()) entry.deleteCharAt(entry.length - 1)
            "OK" -> { submit(); return }
            else -> if (entry.length < Passcode.MAX_LENGTH) entry.append(k)
        }
        dots.text = if (entry.isEmpty()) " " else "●".repeat(entry.length)
    }

    private fun clearEntry() {
        entry.setLength(0)
        dots.text = " "
    }

    private fun submit() {
        val pin = entry.toString()
        if (creating) {
            if (pin.length < Passcode.MIN_LENGTH) {
                say("Use at least ${Passcode.MIN_LENGTH} digits.", Ui.WARN); return
            }
            val first = firstEntry
            if (first == null) {
                firstEntry = pin
                clearEntry()
                title.text = "Enter it again"
                say("To make sure it's right.", Ui.MUTED)
            } else if (first == pin) {
                Passcode.set(this, pin)
                Toast.makeText(this, "Passcode saved", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                firstEntry = null
                clearEntry()
                title.text = "Choose a passcode"
                say("Those didn't match. Start again.", Ui.DANGER)
            }
            return
        }

        if (pin.isEmpty()) return
        if (Passcode.check(this, pin)) {
            AdminSession.grant()
            val next = if (intent.getStringExtra(EXTRA_NEXT) == NEXT_APPS) AllowedAppsActivity::class.java
            else AdminActivity::class.java
            startActivity(Intent(this, next))
            finish()
        } else {
            clearEntry()
            say("Wrong passcode.", Ui.DANGER)
            handler.removeCallbacks(lockoutTicker)
            handler.post(lockoutTicker)
        }
    }

    /** Shows the wait message while locked out; returns true if still locked out. */
    private fun showLockout(): Boolean {
        val ms = Passcode.lockoutRemainingMs(this)
        if (ms <= 0) {
            if (lockoutShown) say("You can try again now.", Ui.MUTED)
            lockoutShown = false
            return false
        }
        lockoutShown = true
        val s = (ms + 999) / 1000
        val time = if (s >= 60) "${(s + 59) / 60} min" else "$s s"
        say("Too many wrong tries. Try again in $time.", Ui.DANGER)
        return true
    }

    private fun say(text: String, color: Int) {
        message.text = text
        message.setTextColor(color)
    }
}
