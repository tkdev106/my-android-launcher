package io.github.tkdev106.launcher

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.OverScroller
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 6列×7行のマスを、横にページをめくって見せる（docs/spec.md「アプリ一覧」）。
 * ViewPager を使うと AndroidX が要るので、横のスクロールとページ合わせだけを自前で持つ。
 * 子は追加した順に、ページ → 行 → 列 の順でマスに入る。
 * 最後のページの先へめくると最初のページに、最初のページの手前へめくると最後のページに戻る。
 */
class PagedGrid(context: Context, private val columns: Int, private val rows: Int) : ViewGroup(context) {
    private val scroller = OverScroller(context)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFling = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private var velocity: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var dragging = false

    var page = 0
        private set
    var onPageChanged: (Int) -> Unit = {}

    var onSwipeDown: () -> Unit = {}

    val pageCount get() = maxOf(1, (childCount + columns * rows - 1) / (columns * rows))

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val cw = (w - paddingLeft - paddingRight) / columns
        val ch = (h - paddingTop - paddingBottom) / rows
        for (i in 0 until childCount) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY))
        }
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val cw = (w - paddingLeft - paddingRight) / columns
        val ch = (b - t - paddingTop - paddingBottom) / rows
        val perPage = columns * rows
        for (i in 0 until childCount) {
            val p = i / perPage
            val cell = i % perPage
            val x = p * w + paddingLeft + (cell % columns) * cw
            val y = paddingTop + (cell / columns) * ch
            getChildAt(i).layout(x, y, x + cw, y + ch)
        }
        scrollTo(page * w, 0)
    }

    fun showPage(p: Int, animate: Boolean) {
        val target = p.coerceIn(0, pageCount - 1)
        if (target != page) {
            page = target
            onPageChanged(page)
        }
        if (animate) {
            scroller.startScroll(scrollX, 0, page * width - scrollX, 0, 260)
            postInvalidateOnAnimation()
        } else {
            scroller.forceFinished(true)
            scrollTo(page * width, 0)
        }
    }

    /** 反対の端へは全ページを流さず、隣のページから入ってくるように見せる */
    private fun wrapTo(p: Int) {
        val w = width
        // 端の外へ引いた分だけ隣から入ってくる形にする。引いていない（速く払っただけの）ときは隣のページの位置から
        val from = if (p == 0) -w + (scrollX - (pageCount - 1) * w).coerceIn(0, w) else pageCount * w - (-scrollX).coerceIn(0, w)
        page = p
        onPageChanged(page)
        scroller.forceFinished(true)
        scrollTo(from, 0)
        scroller.startScroll(from, 0, p * w - from, 0, 260)
        postInvalidateOnAnimation()
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.currX, 0)
            postInvalidateOnAnimation()
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; lastX = ev.x; dragging = false
                if (!scroller.isFinished) {
                    scroller.forceFinished(true)
                    dragging = true
                }
                track(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                track(ev)
                val dx = abs(ev.x - downX)
                val dy = ev.y - downY
                if (dx > slop && dx > abs(dy)) {
                    dragging = true
                    lastX = ev.x
                } else if (dy > slop * 3 && dy > dx * 2) {
                    return true
                }
            }
        }
        return dragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        track(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; lastX = ev.x
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.y - downY
                if (!dragging && dy > abs(ev.x - downX) * 2) return true
                dragging = true
                // 端の先にも少しだけ引っ張れるようにして、ぐるりと回る操作を受ける
                val over = if (pageCount > 1) width / 3 else 0
                val max = (pageCount - 1) * width
                scrollTo((scrollX + (lastX - ev.x)).roundToInt().coerceIn(-over, max + over), 0)
                lastX = ev.x
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val vt = velocity
                vt?.computeCurrentVelocity(1000)
                val vx = vt?.xVelocity ?: 0f
                val dy = ev.y - downY
                if (!dragging && dy > slop * 3 && ev.actionMasked == MotionEvent.ACTION_UP) {
                    onSwipeDown()
                } else {
                    val w = width.coerceAtLeast(1)
                    val last = pageCount - 1
                    val target = when {
                        vx < -minFling * 4 -> page + 1
                        vx > minFling * 4 -> page - 1
                        scrollX < -w / 6 -> -1
                        scrollX > last * w + w / 6 -> last + 1
                        else -> Math.floorDiv(scrollX + w / 2, w)
                    }
                    when {
                        last == 0 -> showPage(0, animate = true)
                        target > last -> wrapTo(0)
                        target < 0 -> wrapTo(last)
                        else -> showPage(target, animate = true)
                    }
                }
                dragging = false
                velocity?.recycle()
                velocity = null
            }
        }
        return true
    }

    private fun track(ev: MotionEvent) {
        val vt = velocity ?: VelocityTracker.obtain().also { velocity = it }
        vt.addMovement(ev)
    }

    fun setItems(views: List<View>) {
        removeAllViews()
        views.forEach { addView(it) }
        if (page >= pageCount) showPage(pageCount - 1, animate = false)
        onPageChanged(page)
    }
}
