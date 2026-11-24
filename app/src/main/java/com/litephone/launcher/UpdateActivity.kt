package com.litephone.launcher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.TextView

/** "Updates": check online or pick an APK file, then install. No passcode needed. */
class UpdateActivity : Activity() {

    private val PICK_APK = 3
    private lateinit var status: TextView
    private var candidate: Updater.Candidate? = null
    private var busy = false

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val c = Ui.column(this).apply {
            val p = Ui.dp(this@UpdateActivity, 24)
            setPadding(p, p, p, p)
        }
        c.addView(Ui.text(this, "Updates", 28f, bold = true))
        c.addView(Ui.text(this, "You have Lite Phone ${BuildConfig.VERSION_NAME}.", 16f, Ui.MUTED), Ui.lp(this, 8))

        if (!Updater.canInstall(this)) {
            c.addView(Ui.text(
                this,
                "Updates aren't allowed yet. The passcode holder turns this on once: " +
                    "🔒 Passcode → Protection checklist → Allow Lite Phone to update itself.",
                15f, Ui.WARN
            ), Ui.lp(this, 16))
        }

        status = Ui.text(this, "", 16f)
        c.addView(status, Ui.lp(this, 16))

        val found = candidate
        if (found != null) {
            status.text = "Version ${found.versionName} is ready to install."
            c.addView(Ui.button(this, "Install ${found.versionName}", Ui.ACCENT, Ui.BG) { install(found) }, Ui.lp(this, 12))
        }
        c.addView(Ui.button(this, "Check for updates", Ui.SURFACE, Ui.ACCENT) { checkOnline() }, Ui.lp(this, 12))
        c.addView(Ui.button(this, "Install from a file", Ui.SURFACE, Ui.FG) { pickFile() }, Ui.lp(this, 12))
        c.addView(Ui.text(
            this,
            "\"Install from a file\" takes a LitePhone.apk saved in Google Drive, OneDrive, email or Downloads. " +
                "Only genuine Lite Phone updates are accepted.\n\nIf Samsung's Auto Blocker is on, it may stop the install.",
            13f, Ui.MUTED
        ), Ui.lp(this, 12))
        c.addView(Ui.button(this, "Back", Ui.BG, Ui.MUTED) { finish() }, Ui.lp(this, 24))
        Ui.showScrolling(this, c)
    }

    private fun say(text: String, color: Int = Ui.FG) {
        status.text = text
        status.setTextColor(color)
    }

    private fun checkOnline() {
        if (busy) return
        busy = true
        say("Checking…", Ui.MUTED)
        Thread {
            val result = try {
                val file = Updater.download(this)
                val c = Updater.check(this, file)
                Result(c, if (c == null) "You're up to date." else null)
            } catch (e: Exception) {
                Result(null, e.message ?: "Couldn't check for updates.")
            }
            runOnUiThread { busy = false; show(result) }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun pickFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/vnd.android.package-archive")
        try {
            startActivityForResult(intent, PICK_APK)
        } catch (e: Exception) {
            say("Couldn't open your files.", Ui.DANGER)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri: Uri = data?.data ?: return
        if (requestCode != PICK_APK || resultCode != RESULT_OK) return
        Thread {
            val result = try {
                val c = Updater.check(this, Updater.copyFromUri(this, uri))
                Result(c, if (c == null) "That file isn't newer than what you have." else null)
            } catch (e: Exception) {
                Result(null, e.message ?: "Couldn't use that file.")
            }
            runOnUiThread { show(result) }
        }.start()
    }

    private class Result(val candidate: Updater.Candidate?, val message: String?)

    private fun show(r: Result) {
        candidate = r.candidate
        render()
        if (r.message != null) say(r.message, if (r.candidate == null && r.message != "You're up to date.") Ui.WARN else Ui.GOOD)
    }

    private fun install(c: Updater.Candidate) {
        if (!Updater.canInstall(this)) {
            say("Updates aren't allowed yet. See the note above.", Ui.WARN); return
        }
        say("Installing… Lite Phone will restart.", Ui.MUTED)
        Thread {
            try {
                Updater.install(this, c)
            } catch (e: Exception) {
                runOnUiThread { say("Install failed: ${e.message}", Ui.DANGER) }
            }
        }.start()
    }
}
