package com.litephone.launcher

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Small helpers so every screen is built the same simple way, in code. */
object Ui {
    const val BG = 0xFF000000.toInt()
    const val SURFACE = 0xFF1C1C1E.toInt()
    const val FG = 0xFFFFFFFF.toInt()
    const val MUTED = 0xFF9A9AA0.toInt()
    const val ACCENT = 0xFF7FB2FF.toInt()
    const val WARN = 0xFFFFB74D.toInt()
    const val DANGER = 0xFFFF6B6B.toInt()
    const val GOOD = 0xFF66D19E.toInt()

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun text(
        ctx: Context,
        s: CharSequence,
        sp: Float = 16f,
        color: Int = FG,
        bold: Boolean = false,
    ): TextView = TextView(ctx).apply {
        text = s
        textSize = sp
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(0f, 1.15f)
    }

    fun rounded(color: Int, radiusPx: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusPx
        }

    fun button(
        ctx: Context,
        label: String,
        bg: Int = SURFACE,
        fg: Int = FG,
        onClick: () -> Unit,
    ): TextView = text(ctx, label, 17f, fg, bold = true).apply {
        gravity = Gravity.CENTER
        val p = dp(ctx, 14)
        setPadding(p, p, p, p)
        background = RippleDrawable(
            ColorStateList.valueOf(0x33FFFFFF), rounded(bg, dp(ctx, 14).toFloat()), null
        )
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    fun column(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
    }

    fun lp(ctx: Context, topMarginDp: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(ctx, topMarginDp) }

    /** Shows [content] full screen, scrollable, padded clear of the status/nav bars. */
    fun showScrolling(activity: Activity, content: View) {
        val scroll = ScrollView(activity).apply {
            setBackgroundColor(BG)
            isFillViewport = true
            clipToPadding = false
            addView(content)
        }
        padForSystemBars(scroll)
        activity.setContentView(scroll)
    }

    fun padForSystemBars(v: View) {
        v.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val i = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                view.setPadding(i.left, i.top, i.right, i.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom
                )
            }
            insets
        }
        v.requestApplyInsets()
    }
}
