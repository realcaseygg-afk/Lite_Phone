package com.litephone.launcher

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

/** Everything the passcode holder can do. Only reachable right after entering the passcode. */
class AdminActivity : Activity() {

    override fun onResume() {
        super.onResume()
        if (!AdminSession.valid()) {
            finish(); return
        }
        render()
    }

    private fun render() {
        val c = Ui.column(this).apply {
            val p = Ui.dp(this@AdminActivity, 24)
            setPadding(p, p, p, p)
        }
        c.addView(Ui.text(this, "Passcode holder", 28f, bold = true))

        val paused = Policy.pauseRemainingMs(this) > 0
        val (status, color) = when {
            !Policy.isGuardEnabled(this) ->
                "App blocker is OFF, so nothing is being blocked. Open the protection checklist to fix it." to Ui.DANGER
            !Policy.isDefaultHome(this) ->
                "Lite Phone isn't the Home app. Open the protection checklist to fix it." to Ui.WARN
            paused -> "All apps are unlocked until ${Policy.pauseEndText(this)}." to Ui.WARN
            else -> "Locked. Only allowed apps can open." to Ui.GOOD
        }
        c.addView(Ui.text(this, status, 16f, color), Ui.lp(this, 8))
        val updated = try {
            java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                .format(java.util.Date(packageManager.getPackageInfo(packageName, 0).lastUpdateTime))
        } catch (e: Exception) {
            "unknown"
        }
        c.addView(Ui.text(this, "Lite Phone ${BuildConfig.VERSION_NAME}, installed $updated", 13f, Ui.MUTED), Ui.lp(this, 6))

        fun add(label: String, bg: Int = Ui.SURFACE, fg: Int = Ui.FG, top: Int = 12, action: () -> Unit) =
            c.addView(Ui.button(this, label, bg, fg, action), Ui.lp(this, top))

        add("Unlock all apps for 15 minutes", top = 24) { unlockFor(15) }
        add("Unlock all apps for 1 hour") { unlockFor(60) }
        if (paused) add("Lock now", Ui.ACCENT, Ui.BG) {
            Policy.lockNow(this); render()
        }
        add("Choose allowed apps", top = 24) {
            startActivity(Intent(this, AllowedAppsActivity::class.java))
        }
        add("Protection checklist") {
            startActivity(Intent(this, SetupActivity::class.java))
        }
        add("Unlock all of Settings for 15 minutes") {
            Policy.pauseAtLeast(this, 15 * 60_000L)
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        // Web pass settings
        c.addView(Ui.text(this, "WEB PASS", 13f, Ui.MUTED, bold = true), Ui.lp(this, 28))
        c.addView(Ui.text(
            this,
            "Lets browsers open for a short time without the passcode, a few times a day.",
            14f, Ui.MUTED
        ), Ui.lp(this, 4))
        chips(c, listOf("on" to "On", "off" to "Off"), if (WebPass.enabled(this)) "on" else "off") {
            WebPass.setEnabled(this, it == "on")
        }
        if (WebPass.enabled(this)) {
            chips(c, listOf("15" to "15 min", "30" to "30 min", "60" to "1 hour"), WebPass.minutes(this).toString()) {
                WebPass.setMinutes(this, it.toInt())
            }
            chips(c, listOf("1" to "1 a day", "2" to "2 a day", "3" to "3 a day"), WebPass.perDay(this).toString()) {
                WebPass.setPerDay(this, it.toInt())
            }
        }

        add("Change passcode", top = 28) { PinActivity.create(this) }
        add("Turn off Lite Phone", Ui.SURFACE, Ui.DANGER, top = 24) { confirmTurnOff() }
        add("Done", Ui.BG, Ui.MUTED, top = 24) { finish() }

        Ui.showScrolling(this, c)
    }

    /** A row of options; the current one is filled in. */
    private fun chips(parent: android.widget.LinearLayout, options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit) {
        val line = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.HORIZONTAL }
        for ((value, label) in options) {
            val selected = value == current
            line.addView(
                Ui.button(this, label, if (selected) Ui.ACCENT else Ui.SURFACE, if (selected) Ui.BG else Ui.FG) {
                    onPick(value); render()
                }.apply { textSize = 15f },
                android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    rightMargin = Ui.dp(this@AdminActivity, 8)
                }
            )
        }
        parent.addView(line, Ui.lp(this, 8))
    }

    private fun unlockFor(minutes: Int) {
        Policy.pauseFor(this, minutes * 60_000L)
        Toast.makeText(this, "All apps unlocked until ${Policy.pauseEndText(this)}", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun confirmTurnOff() {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Turn off Lite Phone?")
            .setMessage(
                "Nothing will be blocked and uninstall protection is removed.\n\n" +
                    "On the next screen, pick your old Home app (One UI Home). " +
                    "After that you can uninstall Lite Phone like any other app."
            )
            .setPositiveButton("Turn off") { _, _ -> turnOff() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun turnOff() {
        val dpm = getSystemService(DevicePolicyManager::class.java)
        val admin = AdminReceiver.component(this)
        try {
            if (dpm != null && dpm.isAdminActive(admin)) dpm.removeActiveAdmin(admin)
        } catch (e: Exception) { }
        Prefs.setArmed(this, false)
        Policy.lockNow(this)
        AdminSession.end()
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }
}
