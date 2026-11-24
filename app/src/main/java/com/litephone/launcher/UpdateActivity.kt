package com.litephone.launcher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** "Updates": checks Lite Phone and your own apps, or installs from a file. No passcode needed. */
class UpdateActivity : Activity() {

    private val PICK_APK = 3

    /** One line per app after a check. */
    private class Row(val name: String, val status: String, val candidate: Updater.Candidate?, val ok: Boolean)

    private var rows: List<Row> = emptyList()
    private var checking = false
    private lateinit var note: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) // the token field
    }

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
        c.addView(Ui.text(this, "Lite Phone ${BuildConfig.VERSION_NAME} and your own apps.", 16f, Ui.MUTED), Ui.lp(this, 8))

        if (!Updater.canInstall(this)) {
            c.addView(Ui.text(
                this,
                "Updates aren't allowed yet. The passcode holder turns this on once: " +
                    "🔒 Passcode menu → Protection checklist → Allow Lite Phone to update itself.",
                15f, Ui.WARN
            ), Ui.lp(this, 16))
        }

        c.addView(Ui.button(this, if (checking) "Checking…" else "Check for updates", Ui.ACCENT, Ui.BG) { checkAll() }, Ui.lp(this, 20))
        note = Ui.text(this, "", 14f, Ui.MUTED)
        c.addView(note, Ui.lp(this, 6))

        for (r in rows) c.addView(rowView(r), Ui.lp(this, 10))
        if (rows.any { it.candidate != null } && rows.any { it.candidate?.pkg == packageName }) {
            c.addView(Ui.text(this, "Tip: install your other apps first. Lite Phone restarts when it updates.", 13f, Ui.MUTED), Ui.lp(this, 6))
        }

        c.addView(Ui.button(this, "Install from a file", Ui.SURFACE, Ui.FG) { pickFile() }, Ui.lp(this, 20))
        c.addView(Ui.text(
            this,
            "Takes an APK saved in Drive, OneDrive, email or Downloads. It must be an update to an app " +
                "you already have, signed with the same key.",
            13f, Ui.MUTED
        ), Ui.lp(this, 6))

        tokenSection(c)

        c.addView(Ui.button(this, "Back", Ui.BG, Ui.MUTED) { finish() }, Ui.lp(this, 24))
        Ui.showScrolling(this, c)
    }

    private fun rowView(r: Row): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val texts = Ui.column(this).apply {
            addView(Ui.text(this@UpdateActivity, r.name, 17f, bold = true))
            addView(Ui.text(this@UpdateActivity, r.status, 14f, if (r.ok) Ui.MUTED else Ui.WARN))
        }
        row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val cand = r.candidate
        if (cand != null) {
            row.addView(Ui.smallButton(this, if (cand.isNew) "Install" else "Update") { install(cand) })
        }
        return row
    }

    private fun tokenSection(c: LinearLayout) {
        c.addView(Ui.text(this, "GITHUB ACCESS FOR PRIVATE REPOS", 13f, Ui.MUTED, bold = true), Ui.lp(this, 32))
        val has = Updater.token(this) != null
        c.addView(Ui.text(
            this,
            if (has) "✓ A token is saved. It's only sent to GitHub."
            else "Needed only for apps in private repos. On GitHub: Settings → Developer settings → " +
                "Fine-grained tokens → Generate. Pick your app repos, give Contents \"Read-only\", and nothing else.",
            14f, if (has) Ui.GOOD else Ui.MUTED
        ), Ui.lp(this, 6))
        val field = Ui.input(this, "Paste token (github_pat_…)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        c.addView(field, Ui.lp(this, 8))
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Ui.smallButton(this, "Save token") {
            val t = field.text.toString().trim()
            if (t.isEmpty()) {
                Toast.makeText(this, "Paste a token first", Toast.LENGTH_SHORT).show()
            } else {
                Updater.setToken(this, t)
                Toast.makeText(this, "Token saved", Toast.LENGTH_SHORT).show()
                render()
            }
        })
        if (has) {
            buttons.addView(Ui.smallButton(this, "Remove token", Ui.MUTED) {
                Updater.setToken(this, null); render()
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = Ui.dp(this@UpdateActivity, 8)
            })
        }
        c.addView(buttons, Ui.lp(this, 8))
    }

    private fun checkAll() {
        if (checking) return
        checking = true
        rows = emptyList()
        render()
        note.text = "Checking Lite Phone and your apps…"
        Thread {
            val out = ArrayList<Row>()
            // Your other apps
            try {
                for (src in Updater.fetchAppList(this)) {
                    out.add(try {
                        val cand = Updater.check(this, Updater.downloadApp(this, src), src.pkg)
                        when {
                            cand == null -> Row(src.name, "Up to date", null, true)
                            cand.isNew -> Row(src.name, "Not installed. Version ${cand.versionName} available.", cand, true)
                            else -> Row(src.name, "Update ready: version ${cand.versionName}", cand, true)
                        }
                    } catch (e: Exception) {
                        Row(src.name, e.message ?: "Couldn't check", null, false)
                    })
                }
            } catch (e: Exception) {
                out.add(Row("Your apps", "Couldn't read the app list (my-apps.json): ${e.message}", null, false))
            }
            // Lite Phone last, since installing it restarts this screen
            out.add(try {
                val cand = Updater.check(this, Updater.downloadLitePhone(this), packageName)
                if (cand == null) Row("Lite Phone", "Up to date (${BuildConfig.VERSION_NAME})", null, true)
                else Row("Lite Phone", "Update ready: ${cand.versionName}", cand, true)
            } catch (e: Exception) {
                Row("Lite Phone", e.message ?: "Couldn't check", null, false)
            })
            runOnUiThread {
                checking = false
                rows = out
                render()
                val n = out.count { it.candidate != null }
                note.text = if (n == 0) "Everything is up to date." else "$n ready to install."
            }
        }.start()
    }

    private fun install(c: Updater.Candidate) {
        if (!Updater.canInstall(this)) {
            Toast.makeText(this, "Updates aren't allowed yet. See the note at the top.", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "Installing ${c.label}…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                Updater.install(this, c)
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Install failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
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
            Toast.makeText(this, "Couldn't open your files", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri: Uri = data?.data ?: return
        if (requestCode != PICK_APK || resultCode != RESULT_OK) return
        Thread {
            val row = try {
                val cand = Updater.check(this, Updater.copyFromUri(this, uri), null)
                if (cand == null) Row("That file", "It isn't newer than what you have.", null, true)
                else Row(cand.label, "Update ready: version ${cand.versionName}", cand, true)
            } catch (e: Exception) {
                Row("That file", e.message ?: "Couldn't use that file.", null, false)
            }
            runOnUiThread { rows = listOf(row); render() }
        }.start()
    }
}
