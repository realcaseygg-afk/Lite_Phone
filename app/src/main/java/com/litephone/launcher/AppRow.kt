package com.litephone.launcher

import android.app.Activity
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** One tappable app line, styled by the Customize settings, with a count if it has notifications. */
object AppRow {
    private val grey = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })

    fun build(
        activity: Activity,
        app: Policy.AppEntry,
        compact: Boolean,
        onLongClick: (() -> Unit)? = null,
    ): View {
        val centered = Look.centered(activity)
        val iconStyle = Look.icons(activity)
        val labelSp = Look.labelSp(activity, compact)
        val pad = Ui.dp(activity, if (compact) 9 else 12)
        val size = (labelSp * 1.55f * activity.resources.displayMetrics.density).toInt()

        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (centered) Gravity.CENTER else Gravity.CENTER_VERTICAL
            setPadding(0, pad, 0, pad)
            isClickable = true
            setOnClickListener {
                try {
                    activity.startActivity(app.intent)
                } catch (e: Exception) {
                    Toast.makeText(activity, "Couldn't open ${app.label}", Toast.LENGTH_SHORT).show()
                }
            }
            if (onLongClick != null) setOnLongClickListener { onLongClick(); true }
        }
        if (iconStyle != "none") {
            row.addView(ImageView(activity).apply {
                setImageDrawable(app.icon)
                if (iconStyle == "grey") colorFilter = grey
            }, LinearLayout.LayoutParams(size, size))
        }
        val label = styledLabel(activity, app.label, labelSp).apply {
            val left = if (iconStyle == "none") 0 else Ui.dp(activity, 18)
            setPadding(left, 0, Ui.dp(activity, 8), 0)
        }
        row.addView(
            label,
            if (centered) LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            else LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        val count = BadgeService.counts[app.pkg] ?: 0
        if (count > 0) row.addView(Ui.countBadge(activity, count))
        return row
    }

    /** Label text in the chosen font, with a soft shadow when a wallpaper is behind it. */
    fun styledLabel(activity: Activity, text: String, sp: Float, color: Int = Ui.FG): TextView =
        Ui.text(activity, text, sp, color).apply {
            typeface = Look.typeface(activity)
            isSingleLine = true
            if (Look.background(activity) != Look.BG_BLACK) setShadowLayer(8f, 0f, 1f, 0xCC000000.toInt())
        }
}
