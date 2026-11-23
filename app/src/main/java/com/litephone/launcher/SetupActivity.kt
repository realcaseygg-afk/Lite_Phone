package com.litephone.launcher

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast

/**
 * First-time setup, and later the "Protection checklist" for the passcode holder.
 * Each step opens the right Android screen and shows a check mark once done.
 */
class SetupActivity : Activity() {

    private val REQUEST_HOME = 1

    /** After setup, this screen is only for the passcode holder. */
    private val passcodeHolderMode get() = Prefs.isArmed(this)

    override fun onResume() {
        super.onResume()
        if (passcodeHolderMode && !AdminSession.valid()) {
            finish(); return
        }
        render()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_HOME && !Policy.isDefaultHome(this)) openSettings(Intent(Settings.ACTION_HOME_SETTINGS))
    }

    // Leaving setup half-done just brings you back here.
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (passcodeHolderMode) super.onBackPressed()
    }

    private fun render() {
        val c = Ui.column(this).apply {
            val p = Ui.dp(this@SetupActivity, 24)
            setPadding(p, p, p, p)
        }
        if (passcodeHolderMode) {
            c.addView(Ui.text(this, "Protection checklist", 28f, bold = true))
            c.addView(
                Ui.text(this, "Everything should have a green check. Settings screens opened from here are unlocked for 5 minutes.", 15f, Ui.MUTED),
                Ui.lp(this, 8)
            )
        } else {
            c.addView(Ui.text(this, "Set up Lite Phone", 28f, bold = true))
            c.addView(
                Ui.text(
                    this,
                    "Hand the phone to the person who will keep the passcode. " +
                        "Once setup is finished, only the allowed apps will open, and only the passcode can change that.",
                    15f, Ui.MUTED
                ),
                Ui.lp(this, 8)
            )
        }

        var n = 1
        if (!passcodeHolderMode) {
            step(
                c, n++, "Create the passcode",
                "Choose ${Passcode.MIN_LENGTH} to ${Passcode.MAX_LENGTH} digits and keep it to yourself.",
                Passcode.isSet(this), "Create passcode"
            ) { PinActivity.create(this) }
        }

        step(
            c, n++, "Make Lite Phone the Home app",
            "When asked, choose Lite Phone as the default Home app.",
            Policy.isDefaultHome(this), "Choose Home app"
        ) { requestHomeRole() }

        val guardOn = Policy.isGuardEnabled(this)
        step(
            c, n++, "Turn on the app blocker",
            "On the Accessibility screen, tap Installed apps, then Lite Phone app blocker, and switch it on.\n\n" +
                "If the switch is greyed out or says \"Restricted setting\": tap \"Allow restricted setting\" below, " +
                "then tap ⋮ (top right) → Allow restricted settings, confirm, and come back here.",
            guardOn, "Open Accessibility"
        ) { openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        if (!guardOn && Build.VERSION.SDK_INT >= 33) {
            c.addView(Ui.button(this, "Allow restricted setting", Ui.SURFACE, Ui.ACCENT) {
                openSettings(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                )
            }, Ui.lp(this, 8))
        }

        step(
            c, n++, "Turn on uninstall protection",
            "Stops Lite Phone from being uninstalled without the passcode. Tap Activate on the next screen.",
            Policy.isUninstallProtected(this), "Turn on"
        ) { requestDeviceAdmin() }

        if (passcodeHolderMode) {
            c.addView(Ui.button(this, "Done", Ui.SURFACE) { finish() }, Ui.lp(this, 32))
        } else {
            val ready = Passcode.isSet(this) && Policy.isDefaultHome(this) && guardOn
            c.addView(Ui.text(
                this,
                if (ready) "All set. Locking hides every other app until the passcode is entered."
                else "Finish the steps above to lock the phone.",
                15f, Ui.MUTED
            ), Ui.lp(this, 32))
            c.addView(Ui.button(
                this, "Finish and lock",
                if (ready) Ui.ACCENT else Ui.SURFACE,
                if (ready) Ui.BG else Ui.MUTED
            ) {
                if (!ready) {
                    Toast.makeText(this, "Finish the steps above first", Toast.LENGTH_SHORT).show()
                } else {
                    Prefs.allowedList(this) // make sure the default apps are saved
                    Policy.lockNow(this)
                    Prefs.setArmed(this, true)
                    Toast.makeText(this, "Lite Phone is locked", Toast.LENGTH_LONG).show()
                    finish()
                }
            }, Ui.lp(this, 12))
        }

        Ui.showScrolling(this, c)
    }

    private fun step(
        parent: LinearLayout, number: Int, title: String, body: String,
        done: Boolean, actionLabel: String, action: () -> Unit,
    ) {
        val mark = if (done) "✓" else "$number."
        parent.addView(
            Ui.text(this, "$mark  $title", 20f, if (done) Ui.GOOD else Ui.FG, bold = true),
            Ui.lp(this, 28)
        )
        val bodyView = Ui.text(this, body, 15f, Ui.MUTED)
        parent.addView(bodyView, Ui.lp(this, 6))
        val button = Ui.button(this, if (done) "Done" else actionLabel, Ui.SURFACE, if (done) Ui.MUTED else Ui.ACCENT, action)
        parent.addView(button, Ui.lp(this, 10))
        if (done) {
            bodyView.visibility = View.GONE
            button.visibility = View.GONE
        }
    }

    /** In passcode-holder mode, briefly unlock so the Settings screen isn't blocked. */
    private fun allowSettingsBriefly() {
        if (passcodeHolderMode) Policy.pauseAtLeast(this, 5 * 60_000L)
    }

    private fun openSettings(intent: Intent) {
        allowSettingsBriefly()
        try {
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    @Suppress("DEPRECATION")
    private fun requestHomeRole() {
        allowSettingsBriefly()
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                try {
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQUEST_HOME)
                    return
                } catch (e: Exception) { }
            }
        }
        openSettings(Intent(Settings.ACTION_HOME_SETTINGS))
    }

    private fun requestDeviceAdmin() {
        allowSettingsBriefly()
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, AdminReceiver.component(this))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Stops Lite Phone from being uninstalled without the passcode."
            )
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open that screen", Toast.LENGTH_SHORT).show()
        }
    }
}
