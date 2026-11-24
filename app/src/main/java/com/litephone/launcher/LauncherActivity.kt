package com.litephone.launcher

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast

/** The home screen: clock, date and the short list of allowed apps. */
class LauncherActivity : Activity() {

    private lateinit var banner: TextView
    private lateinit var appList: LinearLayout
    private lateinit var scroll: ScrollView
    private val handler = Handler(Looper.getMainLooper())
    private val badgesChanged: () -> Unit = { refreshApps() }
    private val ticker = object : Runnable {
        override fun run() {
            updateBanner()
            handler.postDelayed(this, 10_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = Ui.dp(this, 28)

        val clock = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            textSize = 72f
            setTextColor(Ui.FG)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        val date = TextClock(this).apply {
            format12Hour = "EEEE, MMMM d"
            format24Hour = "EEEE, MMMM d"
            textSize = 18f
            setTextColor(Ui.MUTED)
        }
        banner = Ui.text(this, "", 15f, Ui.BG, bold = true).apply {
            val p = Ui.dp(this@LauncherActivity, 12)
            setPadding(p, p, p, p)
            visibility = View.GONE
        }
        appList = Ui.column(this)
        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(appList)
        }
        fun bottomLink(label: String, onClick: () -> Unit) =
            Ui.text(this, label, 14f, Ui.MUTED).apply {
                gravity = Gravity.CENTER
                val p = Ui.dp(this@LauncherActivity, 14)
                setPadding(p / 2, p, p / 2, p)
                setOnClickListener { onClick() }
            }
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            fun weight() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(bottomLink("Ask to unlock") {
                startActivity(Intent(this@LauncherActivity, RequestUnlockActivity::class.java))
            }, weight())
            addView(bottomLink("My use") {
                startActivity(Intent(this@LauncherActivity, UsageActivity::class.java))
            }, weight())
            addView(bottomLink("\uD83D\uDD12 Passcode") {
                PinActivity.openAdmin(this@LauncherActivity)
            }, weight())
        }

        val root = Ui.column(this).apply {
            setBackgroundColor(Ui.BG)
            addView(clock, Ui.lp(this@LauncherActivity, 24).apply { leftMargin = pad; rightMargin = pad })
            addView(date, Ui.lp(this@LauncherActivity).apply { leftMargin = pad; rightMargin = pad })
            addView(banner, Ui.lp(this@LauncherActivity, 16).apply { leftMargin = pad; rightMargin = pad })
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = Ui.dp(this@LauncherActivity, 24); leftMargin = pad; rightMargin = pad
            })
            addView(bottomBar, Ui.lp(this@LauncherActivity))
        }
        Ui.padForSystemBars(root)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        AdminSession.end() // back on the home screen = passcode-holder visit is over
        if (!Prefs.isArmed(this)) {
            startActivity(Intent(this, SetupActivity::class.java))
        }
        refreshApps()
        BadgeService.addListener(badgesChanged)
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        BadgeService.removeListener(badgesChanged)
        handler.removeCallbacks(ticker)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        scroll.smoothScrollTo(0, 0)
    }

    // The home screen has nowhere to go "back" to.
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
    }

    private fun refreshApps() {
        appList.removeAllViews()
        val apps = Policy.homeEntries(this)
        val more = Policy.moreEntries(this)
        if (apps.isEmpty() && more.isEmpty()) {
            appList.addView(Ui.text(this, "No apps yet. Ask the passcode holder to add some.", 18f, Ui.MUTED))
            return
        }
        // Slightly smaller rows once the list gets long, so more fit on screen.
        val compact = apps.size > 8
        val arrange = { startActivity(Intent(this, ArrangeActivity::class.java)) }
        for (app in apps) appList.addView(AppRow.build(this, app, compact, arrange))

        if (more.isNotEmpty()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val p = Ui.dp(this@LauncherActivity, 14)
                setPadding(0, p, 0, p)
                setOnClickListener { startActivity(Intent(this@LauncherActivity, MoreAppsActivity::class.java)) }
                setOnLongClickListener { arrange(); true }
            }
            row.addView(
                Ui.text(this, "More apps (${more.size})  \u203A", if (compact) 20f else 22f, Ui.MUTED),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            val waiting = more.sumOf { BadgeService.counts[it.pkg] ?: 0 }
            if (waiting > 0) row.addView(Ui.countBadge(this, waiting))
            appList.addView(row)
        }
    }

    private fun updateBanner() {
        val armed = Prefs.isArmed(this)
        val paused = Policy.pauseRemainingMs(this) > 0
        val (msg, color) = when {
            armed && !Policy.isGuardEnabled(this) ->
                "App blocker is OFF. Tap for the passcode holder." to Ui.DANGER
            armed && !Policy.isDefaultHome(this) ->
                "Lite Phone isn't the Home app. Tap for the passcode holder." to Ui.WARN
            armed && paused ->
                "All apps unlocked until ${Policy.pauseEndText(this)}. Tap to lock now." to Ui.WARN
            else -> null to 0
        }
        if (msg == null) {
            banner.visibility = View.GONE
            return
        }
        banner.text = msg
        banner.background = Ui.rounded(color, Ui.dp(this, 12).toFloat())
        banner.visibility = View.VISIBLE
        banner.setOnClickListener {
            if (paused && Policy.isGuardEnabled(this) && Policy.isDefaultHome(this)) {
                Policy.lockNow(this)
                updateBanner()
                Toast.makeText(this, "Locked", Toast.LENGTH_SHORT).show()
            } else {
                PinActivity.openAdmin(this)
            }
        }
    }
}
