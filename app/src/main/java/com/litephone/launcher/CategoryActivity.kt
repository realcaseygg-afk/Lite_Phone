package com.litephone.launcher

import android.app.Activity
import android.content.Intent

/** The apps in one home screen category. */
class CategoryActivity : Activity() {

    companion object {
        const val EXTRA_NAME = "category"
    }

    private val badgesChanged: () -> Unit = { render() }
    private val name get() = intent.getStringExtra(EXTRA_NAME) ?: Policy.OTHER

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
        val apps = Policy.appsIn(this, name)
        val c = Ui.column(this).apply {
            val p = Ui.dp(this@CategoryActivity, 24)
            setPadding(p, p, p, p)
        }
        c.addView(Ui.text(this, name, 28f, bold = true).apply { typeface = Look.typeface(this@CategoryActivity) })
        val arrange = { startActivity(Intent(this, ArrangeActivity::class.java)) }
        if (apps.isEmpty()) c.addView(Ui.text(this, "Nothing here.", 16f, Ui.MUTED), Ui.lp(this, 12))
        for (app in apps) c.addView(AppRow.build(this, app, compact = false, onLongClick = arrange), Ui.lp(this))
        c.addView(Ui.button(this, "Back", Ui.BG, Ui.MUTED) { finish() }, Ui.lp(this, 24))
        Ui.showScrolling(this, c)
    }
}
