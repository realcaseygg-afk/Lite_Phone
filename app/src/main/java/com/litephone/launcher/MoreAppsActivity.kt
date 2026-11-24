package com.litephone.launcher

import android.app.Activity
import android.content.Intent

/** Allowed apps that aren't on the home screen, alphabetical. */
class MoreAppsActivity : Activity() {
    private val badgesChanged: () -> Unit = { render() }

    override fun onResume() {
        super.onResume()
        render()
        BadgeService.addListener(badgesChanged)
    }

    override fun onPause() {
        super.onPause()
        BadgeService.removeListener(badgesChanged)
    }

    private fun render() {
        val c = Ui.column(this).apply {
            val p = Ui.dp(this@MoreAppsActivity, 24)
            setPadding(p, p, p, p)
        }
        c.addView(Ui.text(this, "More apps", 28f, bold = true))
        val arrange = { startActivity(Intent(this, ArrangeActivity::class.java)) }
        val apps = Policy.moreEntries(this)
        if (apps.isEmpty()) c.addView(Ui.text(this, "Nothing here.", 16f, Ui.MUTED), Ui.lp(this, 12))
        // Grouped into sections: Files and documents, Everyday, Banking and money, Food, Shopping, Other.
        val order = Policy.CATEGORIES.map { it.first } + "Other"
        val groups = apps.groupBy { Policy.categoryOf(it) }
        for (name in order) {
            val group = groups[name] ?: continue
            c.addView(Ui.text(this, name.uppercase(), 13f, Ui.MUTED, bold = true), Ui.lp(this, 24))
            for (app in group) c.addView(AppRow.build(this, app, compact = true, onLongClick = arrange), Ui.lp(this))
        }
        c.addView(Ui.button(this, "Arrange home screen", Ui.SURFACE, Ui.ACCENT) { arrange() }, Ui.lp(this, 24))
        c.addView(Ui.button(this, "Back", Ui.BG, Ui.MUTED) { finish() }, Ui.lp(this, 8))
        Ui.showScrolling(this, c)
    }
}
