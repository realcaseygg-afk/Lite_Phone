package com.litephone.launcher

import android.app.Activity
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast

/** One tappable app line: grey icon, big label, and a count if it has notifications. */
object AppRow {
    private val grey = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })

    fun build(
        activity: Activity,
        app: Policy.AppEntry,
        compact: Boolean,
        onLongClick: (() -> Unit)? = null,
    ): View {
        val pad = Ui.dp(activity, if (compact) 9 else 12)
        val size = Ui.dp(activity, if (compact) 34 else 40)
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
        row.addView(ImageView(activity).apply {
            setImageDrawable(app.icon)
            colorFilter = grey
        }, LinearLayout.LayoutParams(size, size))
        row.addView(
            Ui.text(activity, app.label, if (compact) 22f else 26f).apply {
                setPadding(Ui.dp(activity, 20), 0, Ui.dp(activity, 8), 0)
                isSingleLine = true
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        val count = BadgeService.counts[app.pkg] ?: 0
        if (count > 0) row.addView(Ui.countBadge(activity, count))
        return row
    }
}
