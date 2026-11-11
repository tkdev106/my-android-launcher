package io.github.tkdev106.launcher

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.tkdev106.launcher.Ui.dp
import io.github.tkdev106.launcher.core.DoubleTapDetector
import io.github.tkdev106.launcher.core.Gesture
import io.github.tkdev106.launcher.core.GestureClassifier
import io.github.tkdev106.launcher.core.Point
import io.github.tkdev106.launcher.core.PointerTrack
import kotlin.math.abs

/**
 * ホームの一番外側。指の動きを集めて core の GestureClassifier に渡す。
 * 縦に動いたか2本目の指が触れた時点で子（アイコン・予定など）から操作を取り上げ、タップの誤爆を防ぐ。
 */
class GestureLayer(context: Context, private val onGesture: (Gesture) -> Unit) : FrameLayout(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val classifier = GestureClassifier(minDistance = dp(56).toFloat())
    private val doubleTap = DoubleTapDetector(ViewConfiguration.getDoubleTapTimeout().toLong(), dp(40).toFloat())
    private val starts = HashMap<Int, Point>()
    private val ends = HashMap<Int, Point>()
    private var maxPointers = 0
    private var downTime = 0L
    private var downOnEmpty = false
    // 予定一覧のように中が縦にスクロールする所で始まった操作は、ジェスチャーにせずその中のスクロールに任せる
    private var downOnScroll = false

    var active = true

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (active) record(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!active || downOnScroll && ev.pointerCount == 1) return false
        return when (ev.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> true
            MotionEvent.ACTION_MOVE -> {
                val s = starts[ev.getPointerId(0)] ?: return false
                val dx = abs(ev.getX(0) - s.x)
                val dy = abs(ev.getY(0) - s.y)
                dy > slop && dy > dx
            }
            else -> false
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean = active

    private fun record(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                starts.clear(); ends.clear()
                maxPointers = 1
                downTime = ev.eventTime
                downOnEmpty = !hitsInteractive(this, ev.x, ev.y)
                downOnScroll = hitsScrollable(this, ev.x, ev.y)
                put(ev, 0, starts); put(ev, 0, ends)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                put(ev, ev.actionIndex, starts); put(ev, ev.actionIndex, ends)
                maxPointers = maxOf(maxPointers, ev.pointerCount)
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until ev.pointerCount) put(ev, i, ends)
            MotionEvent.ACTION_POINTER_UP -> put(ev, ev.actionIndex, ends)
            MotionEvent.ACTION_UP -> {
                put(ev, 0, ends)
                finish(ev)
            }
            MotionEvent.ACTION_CANCEL -> {
                starts.clear(); ends.clear()
                doubleTap.reset()
            }
        }
    }

    private fun finish(ev: MotionEvent) {
        if (downOnScroll && maxPointers == 1) return
        val tracks = starts.keys.mapNotNull { id -> ends[id]?.let { PointerTrack(starts.getValue(id), it) } }
        val gesture = classifier.classify(tracks)
        if (gesture != null) {
            doubleTap.reset()
            onGesture(gesture)
            return
        }
        // 何も無い所の短いタップだけをダブルタップとして数える（アイコンを2回押して画面が消えないように）
        val t = tracks.singleOrNull()
        val isTap = t != null && maxPointers == 1 && abs(t.dx) < slop && abs(t.dy) < slop &&
            ev.eventTime - downTime < ViewConfiguration.getLongPressTimeout()
        if (isTap && downOnEmpty) {
            if (doubleTap.onTap(ev.eventTime, Point(ev.x, ev.y))) onGesture(Gesture.DOUBLE_TAP)
        } else {
            doubleTap.reset()
        }
    }

    private fun put(ev: MotionEvent, index: Int, into: HashMap<Int, Point>) {
        into[ev.getPointerId(index)] = Point(ev.getX(index), ev.getY(index))
    }

    private fun hitsScrollable(group: ViewGroup, x: Float, y: Float): Boolean {
        for (i in group.childCount - 1 downTo 0) {
            val c = group.getChildAt(i)
            if (c.visibility != View.VISIBLE) continue
            val lx = x - c.left + group.scrollX
            val ly = y - c.top + group.scrollY
            if (lx < 0 || ly < 0 || lx >= c.width || ly >= c.height) continue
            if (c.canScrollVertically(1) || c.canScrollVertically(-1)) return true
            if (c is ViewGroup && hitsScrollable(c, lx, ly)) return true
        }
        return false
    }

    private fun hitsInteractive(group: ViewGroup, x: Float, y: Float): Boolean {
        for (i in group.childCount - 1 downTo 0) {
            val c = group.getChildAt(i)
            if (c.visibility != View.VISIBLE) continue
            val lx = x - c.left + group.scrollX
            val ly = y - c.top + group.scrollY
            if (lx < 0 || ly < 0 || lx >= c.width || ly >= c.height) continue
            if (c.isClickable || c.isLongClickable) return true
            if (c is ViewGroup && hitsInteractive(c, lx, ly)) return true
        }
        return false
    }
}
