package com.litephone.launcher

import android.app.Activity
import android.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout

/** Choose the dock: your most-used apps, or four apps you pick. No passcode needed. */
class DockSettingsActivity : Activity() {

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val c = Ui.column(this).apply {
            val p = Ui.dp(this@DockSettingsActivity, 24)
            setPadding(p, p, p, p)
        }
        c.addView(Ui.text(this, "Dock", 28f, bold = true))
        c.addView(Ui.text(this, "The four apps at the bottom of the home screen, above Settings.", 15f, Ui.MUTED), Ui.lp(this, 8))

        // Mode
        val fixed = Dock.isFixed(this)
        val modes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((value, label) in listOf(false to "Most used", true to "Fixed")) {
            val selected = value == fixed
            modes.addView(
                Ui.button(this, label, if (selected) Ui.ACCENT else Ui.SURFACE, if (selected) Ui.BG else Ui.FG) {
                    Dock.setFixed(this, value); render()
                }.apply { textSize = 15f },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    rightMargin = Ui.dp(this@DockSettingsActivity, 8)
                }
            )
        }
        c.addView(modes, Ui.lp(this, 20))
        c.addView(Ui.text(
            this,
            if (fixed) "Tap a slot to choose its app. Leave a slot empty if you want fewer than four."
            else "Picked from what you use most over the last 14 days (needs usage tracking). Switch to Fixed to choose them yourself.",
            14f, Ui.MUTED
        ), Ui.lp(this, 10))

        // Slots
        if (fixed) {
            val slots = Dock.fixedSlots(this)
            slots.forEachIndexed { i, pkg ->
                val app = if (pkg.isEmpty()) null else Policy.entryFor(this, pkg)
                c.addView(slotRow(i, app), Ui.lp(this, if (i == 0) 16 else 8))
            }
        } else {
            c.addView(Ui.text(this, "RIGHT NOW", 13f, Ui.MUTED, bold = true), Ui.lp(this, 20))
            for (app in Dock.apps(this)) c.addView(slotRow(-1, app), Ui.lp(this, 8))
        }

        c.addView(Ui.button(this, "Done", Ui.SURFACE) { finish() }, Ui.lp(this, 28))
        Ui.showScrolling(this, c)
    }

    /** One dock slot. [index] -1 means read-only (Most used mode). */
    private fun slotRow(index: Int, app: Policy.AppEntry?): View {
        val p = Ui.dp(this, 12)
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(p, p, p, p)
            background = Ui.rounded(Ui.SURFACE, Ui.dp(this@DockSettingsActivity, 14).toFloat())
            if (index >= 0) {
                isClickable = true
                setOnClickListener { pickApp(index) }
            }
        }
        if (index >= 0) {
            row.addView(Ui.text(this, "${index + 1}", 16f, Ui.MUTED, bold = true),
                LinearLayout.LayoutParams(Ui.dp(this, 24), LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val size = Ui.dp(this, 34)
        if (app != null) {
            row.addView(ImageView(this).apply { setImageDrawable(app.icon) }, LinearLayout.LayoutParams(size, size).apply {
                rightMargin = Ui.dp(this@DockSettingsActivity, 12)
            })
        }
        row.addView(Ui.text(this, app?.label ?: "Empty", 17f, if (app != null) Ui.FG else Ui.MUTED),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (index >= 0) row.addView(Ui.text(this, "Change", 14f, Ui.ACCENT, bold = true))
        return row
    }

    private fun pickApp(index: Int) {
        val apps = Policy.allowedEntries(this).sortedBy { it.label.lowercase() }
        val labels = listOf("(Empty)") + apps.map { it.label }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Dock slot ${index + 1}")
            .setItems(labels.toTypedArray()) { _, which ->
                Dock.setSlot(this, index, if (which == 0) "" else apps[which - 1].pkg)
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
