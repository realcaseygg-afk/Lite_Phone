package com.litephone.launcher

import android.app.Activity
import android.content.Intent

/** Lite Phone's settings page, from the bottom of the home screen. */
class OptionsActivity : Activity() {
    override fun onResume() {
        super.onResume()
        val c = Ui.column(this).apply {
            val p = Ui.dp(this@OptionsActivity, 24)
            setPadding(p, p, p, p)
        }
        c.addView(Ui.text(this, "Lite Phone settings", 28f, bold = true))

        fun section(title: String) =
            c.addView(Ui.text(this, title.uppercase(), 13f, Ui.MUTED, bold = true), Ui.lp(this, 28))
        fun item(label: String, top: Int = 10, action: () -> Unit) =
            c.addView(Ui.button(this, label, Ui.SURFACE) { action() }, Ui.lp(this, top))
        fun open(target: Class<*>) = startActivity(Intent(this, target))

        if (WebPass.enabled(this) && Prefs.isArmed(this)) {
            section("Web pass")
            if (WebPass.isActive(this)) {
                c.addView(Ui.text(this, (if (WebPass.includesStore(this)) "Browsers and the Play Store are open until " else "Browsers are open until ") + "${WebPass.endText(this)}.", 15f, Ui.ACCENT), Ui.lp(this, 4))
                item("Open the browser", top = 8) { WebPass.browserIntent(this)?.let { startActivity(it) } }
                item("End web pass now") { WebPass.end(this); recreate() }
            } else {
                val left = WebPass.leftToday(this)
                c.addView(Ui.text(
                    this,
                    "Open the web" + (if (WebPass.includesStore(this)) " and the Play Store" else "") +
                        " for ${WebPass.minutes(this)} minutes. $left of ${WebPass.perDay(this)} left today.",
                    15f, Ui.MUTED
                ), Ui.lp(this, 4))
                if (left > 0) item("\uD83C\uDF10 Start web pass", top = 8) { confirmWebPass() }
            }
        }

        section("You")
        item("My use", top = 8) { open(UsageActivity::class.java) }
        item("Customize look") { open(CustomizeActivity::class.java) }
        item("Dock apps") { open(DockSettingsActivity::class.java) }
        item(if (Agenda.canvasConnected(this)) "Canvas on the Agenda \u2713" else "Connect Canvas to the Agenda") {
            open(CanvasSetupActivity::class.java)
        }
        item("Arrange apps and categories") { open(ArrangeActivity::class.java) }
        item("Updates") { open(UpdateActivity::class.java) }

        section("Passcode holder")
        c.addView(Ui.text(this, "These ask for the passcode.", 14f, Ui.MUTED), Ui.lp(this, 4))
        item("Add or remove apps", top = 8) { PinActivity.openAllowedApps(this) }
        item("🔒 Passcode menu") { PinActivity.openAdmin(this) }

        c.addView(Ui.button(this, "Back", Ui.BG, Ui.MUTED) { finish() }, Ui.lp(this, 24))
        Ui.showScrolling(this, c)
    }

    private fun confirmWebPass() {
        val left = WebPass.leftToday(this)
        android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Start a web pass?")
            .setMessage(
                (if (WebPass.includesStore(this)) "Browsers and the Play Store" else "Browsers") +
                    " open for ${WebPass.minutes(this)} minutes, then close on their own. " +
                    "This uses 1 of your $left left today."
            )
            .setPositiveButton("Start") { _, _ ->
                if (WebPass.start(this)) {
                    android.widget.Toast.makeText(this, "Web pass until ${WebPass.endText(this)}", android.widget.Toast.LENGTH_SHORT).show()
                    WebPass.browserIntent(this)?.let { startActivity(it) }
                }
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
