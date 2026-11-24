package com.litephone.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * A vertical list whose rows can be dragged by a handle to reorder them.
 * Rows move out of the way as you drag, and the surrounding ScrollView scrolls
 * when you drag near its top or bottom edge. [onReordered] gets the row keys in
 * their new order when you let go.
 */
class ReorderList(ctx: Context, private val onReordered: (List<String>) -> Unit) : LinearLayout(ctx) {

    private var dragged: View? = null
    private var draggedBackground: Drawable? = null
    private var lastRawY = 0f
    private val handler = Handler(Looper.getMainLooper())
    private val edge = Ui.dp(ctx, 72)
    private val maxStep = Ui.dp(ctx, 14)

    init {
        orientation = VERTICAL
    }

    /** Adds a row identified by [key]. Touching [handle] starts dragging it. */
    @SuppressLint("ClickableViewAccessibility")
    fun addRow(key: String, row: View, handle: View) {
        row.tag = key
        handle.setOnTouchListener { _, e -> onHandleTouch(row, e) }
        addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    fun keys(): List<String> = (0 until childCount).mapNotNull { getChildAt(it).tag as? String }

    private fun scroller(): ScrollView? {
        var p = parent
        while (p != null) {
            if (p is ScrollView) return p
            p = p.parent
        }
        return null
    }

    private fun onHandleTouch(row: View, e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragged = row
                lastRawY = e.rawY
                draggedBackground = row.background
                row.background = Ui.rounded(Ui.SURFACE, Ui.dp(context, 12).toFloat())
                row.elevation = Ui.dp(context, 8).toFloat()
                row.scaleX = 1.02f
                row.scaleY = 1.02f
                // Keep the ScrollView from stealing the gesture.
                parent?.requestDisallowInterceptTouchEvent(true)
                handler.post(autoScroll)
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragged !== row) return true
                row.translationY += e.rawY - lastRawY
                lastRawY = e.rawY
                swapIfNeeded(row)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragged === row) finish(row)
            }
        }
        return true
    }

    /** Moves a neighbour past the dragged row once the row's middle crosses the neighbour's. */
    private fun swapIfNeeded(row: View) {
        while (true) {
            val i = indexOfChild(row)
            val center = row.top + row.translationY + row.height / 2f
            val prev = if (i > 0) getChildAt(i - 1) else null
            val next = if (i < childCount - 1) getChildAt(i + 1) else null
            when {
                prev != null && center < prev.top + prev.translationY + prev.height / 2f -> {
                    // prev goes below the row; the row's slot moves up by prev's height.
                    removeView(prev)
                    addView(prev, i)
                    row.translationY += prev.height
                    // Shift both now so the next check sees the new positions before relayout.
                    row.offsetTopAndBottom(-prev.height)
                    prev.offsetTopAndBottom(row.height)
                    slideIn(prev, -row.height.toFloat())
                }
                next != null && center > next.top + next.translationY + next.height / 2f -> {
                    removeView(next)
                    addView(next, i)
                    row.translationY -= next.height
                    row.offsetTopAndBottom(next.height)
                    next.offsetTopAndBottom(-row.height)
                    slideIn(next, row.height.toFloat())
                }
                else -> return
            }
        }
    }

    private fun slideIn(v: View, from: Float) {
        v.translationY = from
        v.animate().translationY(0f).setDuration(150).start()
    }

    private val autoScroll = object : Runnable {
        override fun run() {
            val row = dragged ?: return
            val sv = scroller()
            if (sv != null) {
                val loc = IntArray(2)
                sv.getLocationOnScreen(loc)
                val top = loc[1]
                val bottom = top + sv.height
                val step = when {
                    lastRawY < top + edge -> -(maxStep * (1f - (lastRawY - top) / edge).coerceIn(0f, 1f)).toInt()
                    lastRawY > bottom - edge -> (maxStep * (1f - (bottom - lastRawY) / edge).coerceIn(0f, 1f)).toInt()
                    else -> 0
                }
                if (step != 0) {
                    val before = sv.scrollY
                    sv.scrollBy(0, step)
                    val moved = sv.scrollY - before
                    if (moved != 0) {
                        row.translationY += moved
                        swapIfNeeded(row)
                    }
                }
            }
            handler.postDelayed(this, 16)
        }
    }

    private fun finish(row: View) {
        handler.removeCallbacks(autoScroll)
        dragged = null
        parent?.requestDisallowInterceptTouchEvent(false)
        row.animate().translationY(0f).scaleX(1f).scaleY(1f).setDuration(150).withEndAction {
            row.elevation = 0f
            row.background = draggedBackground
        }.start()
        requestLayout()
        onReordered(keys())
    }
}
