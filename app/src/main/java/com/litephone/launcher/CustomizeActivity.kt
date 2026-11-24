package com.litephone.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast

/** Home screen look: background, accent colour, icons, text size, font, clock. No passcode needed. */
class CustomizeActivity : Activity() {

    private val PICK_PHOTO = 7

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val c = Ui.column(this).apply {
            val p = Ui.dp(this@CustomizeActivity, 24)
            setPadding(p, p, p, p)
        }
        c.addView(Ui.text(this, "Customize", 28f, bold = true))
        c.addView(Ui.text(this, "Changes show on the home screen right away.", 15f, Ui.MUTED), Ui.lp(this, 8))

        // Background
        val bg = Look.background(this)
        choice(c, "Background", listOf(Look.BG_BLACK to "Black", Look.BG_PHONE to "Phone wallpaper", Look.BG_PHOTO to "Photo"), bg) {
            if (it == Look.BG_PHOTO && !Look.wallpaperFile(this).exists()) pickPhoto()
            Look.setBackground(this, it)
        }
        if (bg == Look.BG_PHOTO) {
            c.addView(Ui.smallButton(this, if (Look.wallpaperFile(this).exists()) "Choose a different photo" else "Choose photo") {
                pickPhoto()
            }, Ui.lp(this, 8))
        }
        if (bg == Look.BG_PHONE) {
            c.addView(Ui.text(
                this,
                "Uses the wallpaper already set on your phone. Changing it needs Settings unlocked by the passcode holder; a Photo background doesn't.",
                13f, Ui.MUTED
            ), Ui.lp(this, 6))
        }
        if (bg != Look.BG_BLACK) {
            choice(c, "Darken wallpaper", listOf("0" to "None", "1" to "Light", "2" to "Medium", "3" to "Strong"),
                Look.dim(this).toString()) { Look.setDim(this, it.toInt()) }
        }

        // Accent colour
        c.addView(label("Accent colour"), Ui.lp(this, 24))
        val swatches = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val size = Ui.dp(this, 40)
        for (color in Look.ACCENTS) {
            val selected = color == Look.accent(this)
            swatches.addView(View(this).apply {
                background = Ui.rounded(color, size / 2f).apply {
                    if (selected) setStroke(Ui.dp(this@CustomizeActivity, 3), Ui.FG)
                }
                setOnClickListener { Look.setAccent(this@CustomizeActivity, color); render() }
            }, LinearLayout.LayoutParams(size, size).apply { rightMargin = Ui.dp(this@CustomizeActivity, 12) })
        }
        c.addView(swatches, Ui.lp(this, 8))

        // Apps
        choice(c, "App icons", listOf("grey" to "Grey", "color" to "Color", "none" to "None"), Look.icons(this)) {
            Look.setIcons(this, it)
        }
        choice(c, "App text size", listOf("small" to "Small", "medium" to "Medium", "large" to "Large"), Look.size(this)) {
            Look.setSize(this, it)
        }
        choice(c, "Line up", listOf("left" to "Left", "center" to "Center"),
            if (Look.centered(this)) "center" else "left") { Look.setAlign(this, it) }
        choice(c, "Font", listOf("default" to "Default", "light" to "Light", "serif" to "Serif",
            "mono" to "Mono", "condensed" to "Narrow"), Look.font(this)) { Look.setFont(this, it) }

        // Clock
        choice(c, "Clock", listOf("large" to "Large", "small" to "Small", "hidden" to "Hidden"), Look.clock(this)) {
            Look.setClock(this, it)
        }
        choice(c, "Date", listOf("on" to "Show", "off" to "Hide"), if (Look.showDate(this)) "on" else "off") {
            Look.setShowDate(this, it == "on")
        }

        c.addView(Ui.button(this, "Arrange apps", Ui.SURFACE, Ui.ACCENT) {
            startActivity(Intent(this, ArrangeActivity::class.java))
        }, Ui.lp(this, 32))
        c.addView(Ui.button(this, "Reset to default", Ui.SURFACE, Ui.MUTED) {
            AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Reset the look?")
                .setMessage("Background, colours, fonts and clock go back to the defaults. Your apps and their order stay.")
                .setPositiveButton("Reset") { _, _ -> Look.reset(this); render() }
                .setNegativeButton("Cancel", null)
                .show()
        }, Ui.lp(this, 12))
        c.addView(Ui.button(this, "Done", Ui.BG, Ui.MUTED) { finish() }, Ui.lp(this, 12))
        Ui.showScrolling(this, c)
    }

    private fun label(text: String) = Ui.text(this, text, 15f, Ui.MUTED, bold = true)

    /** A titled row of options; the current one is filled in. Wraps onto extra lines if needed. */
    private fun choice(
        parent: LinearLayout,
        title: String,
        options: List<Pair<String, String>>,
        current: String,
        onPick: (String) -> Unit,
    ) {
        parent.addView(label(title), Ui.lp(this, 24))
        options.chunked(3).forEach { chunk ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for ((value, text) in chunk) {
                val selected = value == current
                line.addView(
                    Ui.button(this, text, if (selected) Ui.ACCENT else Ui.SURFACE, if (selected) Ui.BG else Ui.FG) {
                        onPick(value); render()
                    }.apply { textSize = 15f },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        rightMargin = Ui.dp(this@CustomizeActivity, 8)
                    }
                )
            }
            repeat(3 - chunk.size) { line.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
            parent.addView(line, Ui.lp(this, 8))
        }
    }

    @Suppress("DEPRECATION")
    private fun pickPhoto() {
        val intent = if (Build.VERSION.SDK_INT >= 33) {
            Intent(MediaStore.ACTION_PICK_IMAGES)
        } else {
            Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
        }
        try {
            startActivityForResult(intent, PICK_PHOTO)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open photos", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != PICK_PHOTO || resultCode != RESULT_OK || uri == null) return
        Toast.makeText(this, "Setting wallpaper…", Toast.LENGTH_SHORT).show()
        Thread {
            val ok = Look.saveWallpaper(this, uri)
            runOnUiThread {
                if (ok) Look.setBackground(this, Look.BG_PHOTO)
                Toast.makeText(this, if (ok) "Wallpaper set" else "Couldn't use that photo", Toast.LENGTH_SHORT).show()
                render()
            }
        }.start()
    }
}
