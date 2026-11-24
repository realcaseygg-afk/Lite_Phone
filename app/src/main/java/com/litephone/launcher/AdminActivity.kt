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
        add("Change passcode") { PinActivity.create(this) }
        add("Turn off Lite Phone", Ui.SURFACE, Ui.DANGER, top = 24) { confirmTurnOff() }
        add("Done", Ui.BG, Ui.MUTED, top = 24) { finish() }

        Ui.showScrolling(this, c)
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
