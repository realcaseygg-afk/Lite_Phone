package com.litephone.launcher

import android.app.Activity
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * Reorder the home screen and choose which allowed apps sit on it.
 * No passcode needed: this never changes which apps are allowed.
 */
class ArrangeActivity : Activity() {

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val c = Ui.column(this).apply {
            val p = Ui.dp(this@ArrangeActivity, 24)
            setPadding(p, p, p, p)
        }
        c.addView(Ui.text(this, "Arrange home screen", 28f, bold = true))
        c.addView(Ui.text(
            this,
            "Use the arrows to change the order. Apps in More apps are still allowed, just one tap away.",
            15f, Ui.MUTED
        ), Ui.lp(this, 8))

        val home = Policy.homeEntries(this)
        c.addView(Ui.text(this, "HOME SCREEN", 13f, Ui.MUTED, bold = true), Ui.lp(this, 28))
        home.forEachIndexed { i, app ->
            val row = row(app)
            if (i > 0) row.addView(Ui.smallButton(this, "↑") {
                Prefs.swap(this, app.pkg, home[i - 1].pkg); render()
            }, buttonLp())
            if (i < home.size - 1) row.addView(Ui.smallButton(this, "↓") {
                Prefs.swap(this, app.pkg, home[i + 1].pkg); render()
            }, buttonLp())
            row.addView(Ui.smallButton(this, "To More", Ui.MUTED) {
                Prefs.setOnHome(this, app.pkg, false); render()
            }, buttonLp())
            c.addView(row, Ui.lp(this, 4))
        }

        val more = Policy.moreEntries(this)
        if (more.isNotEmpty()) {
            c.addView(Ui.text(this, "MORE APPS", 13f, Ui.MUTED, bold = true), Ui.lp(this, 28))
            for (app in more) {
                val row = row(app)
                row.addView(Ui.smallButton(this, "To Home") {
                    Prefs.setOnHome(this, app.pkg, true); render()
                }, buttonLp())
                c.addView(row, Ui.lp(this, 4))
            }
        }

        c.addView(Ui.button(this, "Customize look", Ui.SURFACE, Ui.ACCENT) {
            startActivity(android.content.Intent(this, CustomizeActivity::class.java))
        }, Ui.lp(this, 24))
        c.addView(Ui.button(this, "Done", Ui.SURFACE) { finish() }, Ui.lp(this, 12))
        Ui.showScrolling(this, c)
    }

    private fun row(app: Policy.AppEntry): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = Ui.dp(this@ArrangeActivity, 4)
            setPadding(0, p, 0, p)
        }
        val size = Ui.dp(this, 32)
        row.addView(ImageView(this).apply { setImageDrawable(app.icon) }, LinearLayout.LayoutParams(size, size))
        row.addView(
            Ui.text(this, app.label, 17f).apply {
                setPadding(Ui.dp(this@ArrangeActivity, 12), 0, Ui.dp(this@ArrangeActivity, 4), 0)
                isSingleLine = true
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        return row
    }

    private fun buttonLp() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { leftMargin = Ui.dp(this@ArrangeActivity, 6) }
}
