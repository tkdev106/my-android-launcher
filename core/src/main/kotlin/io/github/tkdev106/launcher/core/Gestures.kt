package io.github.tkdev106.launcher.core

import kotlin.math.abs
import kotlin.math.hypot

data class Point(val x: Float, val y: Float)

/** 指1本の動き。押した位置と、離した（または今の）位置 */
data class PointerTrack(val start: Point, val end: Point) {
    val dx get() = end.x - start.x
    val dy get() = end.y - start.y
}

enum class Gesture { SWIPE_UP, SWIPE_DOWN, TWO_FINGER_UP, TWO_FINGER_DOWN, PINCH_OUT, PINCH_IN, DOUBLE_TAP }

enum class HomeAction { OPEN_DRAWER, OPEN_NOTIFICATIONS, OPEN_RECENTS, OPEN_QUICK_SETTINGS, LOCK_SCREEN, OPEN_EDIT_MODE }

/**
 * ホームの上での指の動き（離したところまで）を、docs/spec.md の「ジェスチャー」のどれかに振り分ける。
 * ダブルタップは時間が絡むので [DoubleTapDetector] で別に見る。
 * minDistance は「動かした」とみなす距離（ピクセル）。
 */
class GestureClassifier(private val minDistance: Float) {

    fun classify(tracks: List<PointerTrack>): Gesture? = when (tracks.size) {
        1 -> single(tracks[0])
        2 -> double(tracks[0], tracks[1])
        else -> null
    }

    private fun single(t: PointerTrack): Gesture? {
        if (!isVertical(t.dx, t.dy)) return null
        return if (t.dy < 0) Gesture.SWIPE_UP else Gesture.SWIPE_DOWN
    }

    private fun double(a: PointerTrack, b: PointerTrack): Gesture? {
        val before = distance(a.start, b.start)
        val after = distance(a.end, b.end)
        // 2本が同じ向きに動いていればスワイプ、離れていけばピンチアウト
        val sameDirection = a.dy * b.dy > 0
        val dx = (a.dx + b.dx) / 2
        val dy = (a.dy + b.dy) / 2
        if (sameDirection && isVertical(dx, dy) && abs(after - before) < abs(dy)) {
            return if (dy < 0) Gesture.TWO_FINGER_UP else Gesture.TWO_FINGER_DOWN
        }
        if (after - before >= minDistance) return Gesture.PINCH_OUT
        if (before - after >= minDistance) return Gesture.PINCH_IN
        return null
    }

    private fun isVertical(dx: Float, dy: Float) = abs(dy) >= minDistance && abs(dy) > abs(dx) * 1.5f

    private fun distance(p: Point, q: Point) = hypot(p.x - q.x, p.y - q.y)

    companion object {
        fun actionFor(gesture: Gesture): HomeAction = when (gesture) {
            Gesture.SWIPE_UP, Gesture.TWO_FINGER_DOWN -> HomeAction.OPEN_DRAWER
            Gesture.SWIPE_DOWN -> HomeAction.OPEN_NOTIFICATIONS
            Gesture.PINCH_OUT -> HomeAction.OPEN_RECENTS
            Gesture.PINCH_IN -> HomeAction.OPEN_QUICK_SETTINGS
            Gesture.DOUBLE_TAP -> HomeAction.LOCK_SCREEN
            Gesture.TWO_FINGER_UP -> HomeAction.OPEN_EDIT_MODE
        }
    }
}

/**
 * タップが2回続いたかを見る。1回目から timeoutMillis 以内に、slop 以内の場所をもう一度タップしたらダブルタップ。
 * 3回目は新しい1回目として数える（3連打で2回消灯させない）。
 */
class DoubleTapDetector(private val timeoutMillis: Long, private val slop: Float) {
    private var last: Pair<Long, Point>? = null

    fun onTap(timeMillis: Long, at: Point): Boolean {
        val prev = last
        val isDouble = prev != null && timeMillis - prev.first <= timeoutMillis &&
            hypot(at.x - prev.second.x, at.y - prev.second.y) <= slop
        last = if (isDouble) null else timeMillis to at
        return isDouble
    }

    /** タップでない操作（スワイプなど）が挟まったら数え直す */
    fun reset() {
        last = null
    }
}
