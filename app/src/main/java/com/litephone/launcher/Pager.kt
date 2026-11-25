package com.litephone.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import kotlin.math.abs

/**
 * Side-by-side full-width pages you swipe between. The page follows your finger
 * and snaps to the nearest page when you let go (a short flick is enough).
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class Pager(ctx: Context, private val onPageChanged: (Int) -> Unit) : HorizontalScrollView(ctx) {

    private val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    var page = 0
        private set
    private var downX = 0f
    private var startPage = 0

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        isFillViewport = true
        addView(row, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun addPage(v: View) {
        row.addView(v, LinearLayout.LayoutParams(width.coerceAtLeast(1), ViewGroup.LayoutParams.MATCH_PARENT))
    }

    val pageCount get() = row.childCount

    fun goTo(p: Int, animate: Boolean = true) {
        val target = p.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        if (animate) smoothScrollTo(target * width, 0) else scrollTo(target * width, 0)
        if (target != page) {
            page = target
            onPageChanged(page)
        }
    }

    // Every page is exactly as wide as the pager.
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        for (i in 0 until row.childCount) {
            row.getChildAt(i).layoutParams = LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        post { scrollTo(page * w, 0) }
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            downX = e.x
            startPage = page
        }
        return super.onInterceptTouchEvent(e)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                startPage = page
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                super.onTouchEvent(e)
                snap(e.x - downX)
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    // Snapping replaces the free-scrolling fling.
    override fun fling(velocityX: Int) {}

    private fun snap(dx: Float) {
        val w = width.coerceAtLeast(1)
        val target = when {
            abs(dx) > w * 0.15f -> if (dx < 0) startPage + 1 else startPage - 1
            else -> (scrollX + w / 2) / w
        }
        goTo(target)
    }
}
