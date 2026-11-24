package com.litephone.launcher

import android.app.Activity
import android.content.res.ColorStateList
import android.view.Gravity
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast

/**
 * Passcode holder picks which apps show on the home screen and are allowed to open.
 * Sections: Allowed, Suggested (school and everyday apps already installed), Everything else.
 */
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
        val installed = Policy.launchableApps(this).associateBy { it.pkg }
        val suggestions = Policy.SUGGESTED.associateBy { it.pkg }

        val allowedApps = allowed.mapNotNull { installed[it] }
        val suggestedApps = Policy.SUGGESTED
            .filter { it.pkg !in allowed }
            .mapNotNull { installed[it.pkg] }
        val otherApps = installed.values
            .filter { it.pkg !in allowed && it.pkg !in suggestions }
            .sortedBy { it.label.lowercase() }

        header(c, "Allowed (${allowedApps.size})")
        if (allowedApps.isEmpty()) c.addView(Ui.text(this, "None yet.", 15f, Ui.MUTED), Ui.lp(this, 4))
        allowedApps.forEach { row(c, it, true, Policy.RISKY[it.pkg]) }

        if (suggestedApps.isNotEmpty()) {
            header(c, "Suggested")
            val school = suggestedApps.filter { suggestions[it.pkg]?.group == "School" }
            if (school.size > 1) {
                c.addView(Ui.button(this, "Allow all ${school.size} school apps", Ui.SURFACE, Ui.ACCENT) {
                    school.forEach { Prefs.setAllowed(this, it.pkg, true) }
                    Toast.makeText(this, "School apps allowed", Toast.LENGTH_SHORT).show()
                    render()
                }, Ui.lp(this, 8))
            }
            suggestedApps.forEach { app ->
                val s = suggestions[app.pkg]
                row(c, app, false, s?.let { "${it.group}: ${it.why}" }, hintColor = Ui.ACCENT)
            }
        }

        header(c, "Everything else")
        otherApps.forEach { row(c, it, false, Policy.RISKY[it.pkg]) }

        c.addView(Ui.button(this, "Done", Ui.SURFACE) { finish() }, Ui.lp(this, 24))
        Ui.showScrolling(this, c)
    }

    private fun header(parent: LinearLayout, title: String) {
        parent.addView(Ui.text(this, title.uppercase(), 13f, Ui.MUTED, bold = true), Ui.lp(this, 28))
    }

    private fun row(
        parent: LinearLayout,
        app: Policy.AppEntry,
        checked: Boolean,
        hint: String?,
        hintColor: Int = Ui.WARN,
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = Ui.dp(this@AllowedAppsActivity, 6)
            setPadding(0, p, 0, p)
        }
        val size = Ui.dp(this, 36)
        row.addView(ImageView(this).apply { setImageDrawable(app.icon) }, LinearLayout.LayoutParams(size, size))

        val labels = Ui.column(this).apply {
            setPadding(Ui.dp(this@AllowedAppsActivity, 16), 0, 0, 0)
            addView(Ui.text(this@AllowedAppsActivity, app.label, 18f))
            if (hint != null) addView(Ui.text(this@AllowedAppsActivity, hint, 13f, hintColor))
        }
        row.addView(labels, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val box = CheckBox(this).apply {
            isChecked = checked
            buttonTintList = ColorStateList.valueOf(Ui.ACCENT)
            setOnCheckedChangeListener { _, on ->
                Prefs.setAllowed(this@AllowedAppsActivity, app.pkg, on)
            }
        }
        row.addView(box)
        row.setOnClickListener { box.toggle() }
        parent.addView(row, Ui.lp(this, 4))
    }
}
