package com.litephone.launcher

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.Toast

/** Connects Canvas so assignments show on the Agenda page. No passcode needed. */
class CanvasSetupActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) // the token field
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val c = Ui.column(this).apply {
            val p = Ui.dp(this@CanvasSetupActivity, 24)
            setPadding(p, p, p, p)
        }
        c.addView(Ui.text(this, "Canvas on the Agenda", 28f, bold = true))
        val domain = Agenda.canvasDomain(this)
        if (Agenda.canvasConnected(this)) {
            c.addView(Ui.text(this, "✓ Connected to $domain", 16f, Ui.GOOD), Ui.lp(this, 10))
            c.addView(Ui.button(this, "Disconnect", Ui.SURFACE, Ui.DANGER) {
                Agenda.disconnectCanvas(this); render()
            }, Ui.lp(this, 16))
        }
        c.addView(Ui.text(
            this,
            "Shows assignments due in the next 7 days that you haven't submitted yet.\n\n" +
                "The token is made on the Canvas website, not in the app. Start a \uD83C\uDF10 Web pass (Settings) first.\n\n" +
                "1. In the browser, go to your school's Canvas site and sign in.\n" +
                "2. Open \u2630 \u2192 Account \u2192 Settings.\n" +
                "3. Under Approved Integrations, tap \"+ New Access Token\". Name it Lite Phone and generate it.\n" +
                "4. Copy the token (it's only shown once) and paste it below with the address from the browser bar.\n\n" +
                "The token is kept on this phone and only sent to your school's Canvas.",
            15f, Ui.MUTED
        ), Ui.lp(this, 16))

        val address = Ui.input(this, "School address, e.g. myschool.instructure.com", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
            .apply { setText(domain ?: "") }
        c.addView(address, Ui.lp(this, 16))
        val token = Ui.input(this, "Access token", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        c.addView(token, Ui.lp(this, 10))
        c.addView(Ui.button(this, "Save and connect", Ui.ACCENT, Ui.BG) {
            val a = address.text.toString()
            val t = token.text.toString()
            if (a.isBlank() || t.isBlank()) {
                Toast.makeText(this, "Enter both the address and the token", Toast.LENGTH_SHORT).show()
            } else {
                Agenda.setCanvas(this, a, t)
                Toast.makeText(this, "Canvas connected. Swipe left on the home screen to see your Agenda.", Toast.LENGTH_LONG).show()
                finish()
            }
        }, Ui.lp(this, 14))
        c.addView(Ui.button(this, "Back", Ui.BG, Ui.MUTED) { finish() }, Ui.lp(this, 12))
        Ui.showScrolling(this, c)
    }
}
