package com.litephone.launcher

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout

/** Passcode holder picks which apps show on the home screen and are allowed to open. */
class AllowedAppsActivity : Activity() {

    override fun onResume() {
        super.onResume()
        if (!AdminSession.valid()) {
            finish(); return
        }
        render()
    }

    private fun render() {
        val c = Ui.column(this).apply {
            val p = Ui.dp(this@AllowedAppsActivity, 24)
            setPadding(p, p, p, p)
        }
        c.addView(Ui.text(this, "Allowed apps", 28f, bold = true))
        c.addView(
            Ui.text(
                this,
                "Checked apps appear on the home screen and can open. Everything else is blocked. " +
                    "Calls, emergency alerts, the keyboard, contacts and alarms always work.",
                15f, Ui.MUTED
            ),
            Ui.lp(this, 8)
        )

        val allowed = Prefs.allowedList(this)
        val apps = Policy.launchableApps(this).sortedWith(
            compareBy({ if (it.pkg in allowed) 0 else 1 },
                { allowed.indexOf(it.pkg) },
                { it.label.lowercase() })
        )

        for (app in apps) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val p = Ui.dp(this@AllowedAppsActivity, 6)
                setPadding(0, p, 0, p)
            }
            val size = Ui.dp(this, 36)
            row.addView(ImageView(this).apply { setImageDrawable(app.icon) },
                LinearLayout.LayoutParams(size, size))

            val labels = Ui.column(this).apply {
                setPadding(Ui.dp(this@AllowedAppsActivity, 16), 0, 0, 0)
                addView(Ui.text(this@AllowedAppsActivity, app.label, 18f))
                Policy.RISKY[app.pkg]?.let { addView(Ui.text(this@AllowedAppsActivity, it, 13f, Ui.WARN)) }
            }
            row.addView(labels, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            val box = CheckBox(this).apply {
                isChecked = app.pkg in allowed
                buttonTintList = android.content.res.ColorStateList.valueOf(Ui.ACCENT)
                setOnCheckedChangeListener { _, checked ->
                    Prefs.setAllowed(this@AllowedAppsActivity, app.pkg, checked)
                }
            }
            row.addView(box)
            row.setOnClickListener { box.toggle() }
            c.addView(row, Ui.lp(this, 4))
        }

        c.addView(Ui.button(this, "Done", Ui.SURFACE) { finish() }, Ui.lp(this, 24))
        Ui.showScrolling(this, c)
        window.decorView.setBackgroundColor(Color.BLACK)
    }
}
