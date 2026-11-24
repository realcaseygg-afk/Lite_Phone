package com.litephone.launcher

import android.app.Activity
import android.content.Intent

/** Home screen "Options": look, arrangement and updates. Nothing here needs the passcode. */
class OptionsActivity : Activity() {
    override fun onResume() {
        super.onResume()
        val c = Ui.column(this).apply {
            val p = Ui.dp(this@OptionsActivity, 24)
            setPadding(p, p, p, p)
        }
        c.addView(Ui.text(this, "Options", 28f, bold = true))
        fun item(label: String, target: Class<*>, top: Int = 12) =
            c.addView(Ui.button(this, label, Ui.SURFACE) { startActivity(Intent(this, target)) }, Ui.lp(this, top))
        item("Customize look", CustomizeActivity::class.java, top = 24)
        item("Arrange apps", ArrangeActivity::class.java)
        item("My use", UsageActivity::class.java)
        item("Updates", UpdateActivity::class.java)
        c.addView(Ui.button(this, "Back", Ui.BG, Ui.MUTED) { finish() }, Ui.lp(this, 24))
        Ui.showScrolling(this, c)
    }
}
