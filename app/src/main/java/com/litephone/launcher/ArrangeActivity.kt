package com.litephone.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * Reorder the home screen categories, reorder apps inside each one, and move
 * apps between categories. No passcode needed: this never changes which apps are allowed.
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
        c.addView(Ui.text(this, "Arrange", 28f, bold = true))
        c.addView(Ui.text(
            this,
            "Use the arrows to change the order. \"Move\" puts an app in a different category.",
            15f, Ui.MUTED
        ), Ui.lp(this, 8))

        val categories = Policy.categories(this)
        val names = categories.map { it.name }

        // Category order
        c.addView(Ui.text(this, "CATEGORY ORDER", 13f, Ui.MUTED, bold = true), Ui.lp(this, 28))
        categories.forEachIndexed { i, cat ->
            val row = row(cat.name, null, 18f)
            if (i > 0) row.addView(Ui.smallButton(this, "↑") {
                Prefs.moveCategory(this, cat.name, up = true, visible = names); render()
            }, buttonLp())
            if (i < categories.size - 1) row.addView(Ui.smallButton(this, "↓") {
                Prefs.moveCategory(this, cat.name, up = false, visible = names); render()
            }, buttonLp())
            c.addView(row, Ui.lp(this, 4))
        }

        // Apps inside each category
        for (cat in categories) {
            c.addView(Ui.text(this, cat.name.uppercase(), 13f, Ui.MUTED, bold = true), Ui.lp(this, 28))
            cat.apps.forEachIndexed { i, app ->
                val row = row(app.label, app, 17f)
                if (i > 0) row.addView(Ui.smallButton(this, "↑") {
                    Prefs.swap(this, app.pkg, cat.apps[i - 1].pkg); render()
                }, buttonLp())
                if (i < cat.apps.size - 1) row.addView(Ui.smallButton(this, "↓") {
                    Prefs.swap(this, app.pkg, cat.apps[i + 1].pkg); render()
                }, buttonLp())
                row.addView(Ui.smallButton(this, "Move", Ui.MUTED) { chooseCategory(app, cat.name) }, buttonLp())
                c.addView(row, Ui.lp(this, 4))
            }
        }

        c.addView(Ui.button(this, "Customize look", Ui.SURFACE, Ui.ACCENT) {
            startActivity(Intent(this, CustomizeActivity::class.java))
        }, Ui.lp(this, 28))
        c.addView(Ui.button(this, "Done", Ui.SURFACE) { finish() }, Ui.lp(this, 12))
        Ui.showScrolling(this, c)
    }

    private fun chooseCategory(app: Policy.AppEntry, current: String) {
        val options = Policy.CATEGORY_NAMES.toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Move ${app.label} to")
            .setSingleChoiceItems(options, options.indexOf(current)) { dialog, which ->
                Prefs.setCategory(this, app.pkg, options[which])
                dialog.dismiss()
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun row(label: String, app: Policy.AppEntry?, sp: Float): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = Ui.dp(this@ArrangeActivity, 4)
            setPadding(0, p, 0, p)
        }
        if (app != null) {
            val size = Ui.dp(this, 32)
            row.addView(ImageView(this).apply { setImageDrawable(app.icon) }, LinearLayout.LayoutParams(size, size))
        }
        row.addView(
            Ui.text(this, label, sp).apply {
                setPadding(if (app != null) Ui.dp(this@ArrangeActivity, 12) else 0, 0, Ui.dp(this@ArrangeActivity, 4), 0)
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
