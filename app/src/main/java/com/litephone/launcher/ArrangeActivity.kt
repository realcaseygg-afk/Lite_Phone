package com.litephone.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Arrange the home screen by dragging.
 *  - Without a category: reorder the categories; tap one to arrange its apps.
 *  - With a category: reorder its apps, or Move an app to another (or a new) category.
 * No passcode needed: this never changes which apps are allowed.
 */
class ArrangeActivity : Activity() {

    companion object {
        private const val EXTRA_CATEGORY = "category"

        fun openCategories(ctx: Context) = ctx.startActivity(Intent(ctx, ArrangeActivity::class.java))

        fun openCategory(ctx: Context, name: String) =
            ctx.startActivity(Intent(ctx, ArrangeActivity::class.java).putExtra(EXTRA_CATEGORY, name))
    }

    private val category get() = intent.getStringExtra(EXTRA_CATEGORY)

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val c = Ui.column(this).apply {
            val p = Ui.dp(this@ArrangeActivity, 24)
            setPadding(p, p, p, p)
        }
        val cat = category
        if (cat == null) renderCategories(c) else renderApps(c, cat)
        c.addView(Ui.button(this, "Done", Ui.SURFACE) { finish() }, Ui.lp(this, 28))
        Ui.showScrolling(this, c)
    }

    // ---- Categories ----

    private fun renderCategories(c: LinearLayout) {
        c.addView(Ui.text(this, "Arrange", 28f, bold = true))
        c.addView(Ui.text(
            this,
            "Drag ≡ to change the order of your categories. Tap a category to arrange the apps in it.",
            15f, Ui.MUTED
        ), Ui.lp(this, 8))

        val list = ReorderList(this) { order -> Prefs.setVisibleCategoryOrder(this, order) }
        for (cat in Policy.categories(this)) {
            val (row, handle) = buildRow(cat.name, "${cat.apps.size} ${if (cat.apps.size == 1) "app" else "apps"}", null)
            row.setOnClickListener { openCategory(this, cat.name) }
            row.addView(chevron())
            list.addRow(cat.name, row, handle)
        }
        c.addView(list, Ui.lp(this, 16))

        c.addView(Ui.button(this, "Customize look", Ui.SURFACE, Ui.ACCENT) {
            startActivity(Intent(this, CustomizeActivity::class.java))
        }, Ui.lp(this, 24))
    }

    // ---- Apps in one category ----

    private fun renderApps(c: LinearLayout, cat: String) {
        c.addView(Ui.text(this, cat, 28f, bold = true))
        c.addView(Ui.text(
            this,
            "Drag ≡ to change the order. Tap Move to put an app in a different category.",
            15f, Ui.MUTED
        ), Ui.lp(this, 8))

        val apps = Policy.appsIn(this, cat)
        if (apps.isEmpty()) {
            c.addView(Ui.text(this, "No apps here now.", 16f, Ui.MUTED), Ui.lp(this, 16))
        }
        val list = ReorderList(this) { order -> Prefs.reorderApps(this, order) }
        for (app in apps) {
            val (row, handle) = buildRow(app.label, null, app)
            row.addView(Ui.smallButton(this, "Move") { chooseCategory(app, cat) { list.removeView(row) } })
            list.addRow(app.pkg, row, handle)
        }
        c.addView(list, Ui.lp(this, 16))

        c.addView(Ui.button(this, "All categories", Ui.SURFACE, Ui.ACCENT) {
            openCategories(this); finish()
        }, Ui.lp(this, 24))
    }

    /** Category picker, with "New category…" at the end. */
    private fun chooseCategory(app: Policy.AppEntry, current: String, onMoved: () -> Unit) {
        val names = Prefs.allCategoryNames(this)
        val options = (names + "New category…").toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Move ${app.label} to")
            .setSingleChoiceItems(options, names.indexOf(current)) { dialog, which ->
                dialog.dismiss()
                if (which == names.size) {
                    askNewCategory { name -> moveTo(app, name, current, onMoved) }
                } else {
                    moveTo(app, names[which], current, onMoved)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun moveTo(app: Policy.AppEntry, target: String, current: String, onMoved: () -> Unit) {
        if (target == current) return
        Prefs.setCategory(this, app.pkg, target)
        Toast.makeText(this, "${app.label} moved to $target", Toast.LENGTH_SHORT).show()
        onMoved()
    }

    private fun askNewCategory(then: (String) -> Unit) {
        val field = Ui.input(this, "Category name", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val box = LinearLayout(this).apply {
            val p = Ui.dp(this@ArrangeActivity, 20)
            setPadding(p, p / 2, p, 0)
            addView(field, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("New category")
            .setView(box)
            .setPositiveButton("Create") { _, _ ->
                val name = Prefs.addCustomCategory(this, field.text.toString())
                if (name != null) then(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---- Row building ----

    /** A row with a drag handle on the left. Returns the row and its handle. */
    private fun buildRow(title: String, subtitle: String?, app: Policy.AppEntry?): Pair<LinearLayout, View> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = Ui.dp(this@ArrangeActivity, 6)
            setPadding(0, p, Ui.dp(this@ArrangeActivity, 4), p)
            isClickable = true
        }
        val handle = TextView(this).apply {
            text = "≡"
            textSize = 28f
            setTextColor(Ui.MUTED)
            gravity = Gravity.CENTER
            contentDescription = "Drag to reorder"
        }
        val size = Ui.dp(this, 52)
        row.addView(handle, LinearLayout.LayoutParams(size, size))
        if (app != null) {
            val icon = Ui.dp(this, 34)
            row.addView(ImageView(this).apply { setImageDrawable(app.icon) }, LinearLayout.LayoutParams(icon, icon).apply {
                rightMargin = Ui.dp(this@ArrangeActivity, 12)
            })
        }
        val texts = Ui.column(this).apply {
            addView(Ui.text(this@ArrangeActivity, title, 18f).apply { isSingleLine = true })
            if (subtitle != null) addView(Ui.text(this@ArrangeActivity, subtitle, 13f, Ui.MUTED))
        }
        row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return row to handle
    }

    private fun chevron() = Ui.text(this, "›", 24f, Ui.MUTED).apply {
        setPadding(Ui.dp(this@ArrangeActivity, 8), 0, Ui.dp(this@ArrangeActivity, 8), 0)
    }
}
