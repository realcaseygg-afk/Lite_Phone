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

        section("You")
        item("My use", top = 8) { open(UsageActivity::class.java) }
        item("Customize look") { open(CustomizeActivity::class.java) }
        item("Arrange apps and categories") { open(ArrangeActivity::class.java) }
        item("Updates") { open(UpdateActivity::class.java) }

        section("Passcode holder")
        c.addView(Ui.text(this, "These ask for the passcode.", 14f, Ui.MUTED), Ui.lp(this, 4))
        item("Add or remove apps", top = 8) { PinActivity.openAllowedApps(this) }
        item("🔒 Passcode menu") { PinActivity.openAdmin(this) }

        c.addView(Ui.button(this, "Back", Ui.BG, Ui.MUTED) { finish() }, Ui.lp(this, 24))
        Ui.showScrolling(this, c)
    }
}
