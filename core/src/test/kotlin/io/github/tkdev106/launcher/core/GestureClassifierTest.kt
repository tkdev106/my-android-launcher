package io.github.tkdev106.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureClassifierTest {
    private val classifier = GestureClassifier(minDistance = 100f)

    private fun t(x0: Float, y0: Float, x1: Float, y1: Float) = PointerTrack(Point(x0, y0), Point(x1, y1))

    @Test
    fun `1本指で上へ動かすと上スワイプ`() {
        assertEquals(Gesture.SWIPE_UP, classifier.classify(listOf(t(500f, 1500f, 520f, 1200f))))
    }

    @Test
    fun `1本指で下へ動かすと下スワイプ`() {
        assertEquals(Gesture.SWIPE_DOWN, classifier.classify(listOf(t(500f, 500f, 480f, 800f))))
    }

    @Test
    fun `動きが短いか横向きならどれにも当たらない`() {
        assertEquals(null, classifier.classify(listOf(t(500f, 500f, 500f, 450f))))
        assertEquals(null, classifier.classify(listOf(t(100f, 500f, 600f, 650f))))
    }

    @Test
    fun `2本指で同じ向きに上下へ動かすと2本指スワイプ`() {
        assertEquals(Gesture.TWO_FINGER_UP, classifier.classify(listOf(t(400f, 1500f, 400f, 1200f), t(700f, 1500f, 700f, 1210f))))
        assertEquals(Gesture.TWO_FINGER_DOWN, classifier.classify(listOf(t(400f, 500f, 400f, 800f), t(700f, 500f, 700f, 790f))))
    }

    @Test
    fun `2本指の間を広げるとピンチアウト、狭めるとピンチイン`() {
        assertEquals(Gesture.PINCH_OUT, classifier.classify(listOf(t(450f, 1000f, 250f, 900f), t(650f, 1000f, 850f, 1100f))))
        assertEquals(Gesture.PINCH_IN, classifier.classify(listOf(t(250f, 900f, 450f, 1000f), t(850f, 1100f, 650f, 1000f))))
    }

    @Test
    fun `3本指はどれにも当たらない`() {
        val up = t(500f, 1500f, 500f, 1000f)
        assertEquals(null, classifier.classify(listOf(up, up, up)))
    }

    @Test
    fun `ジェスチャーごとの動作は spec の表のとおり`() {
        val expected = mapOf(
            Gesture.SWIPE_UP to HomeAction.OPEN_DRAWER,
            Gesture.TWO_FINGER_DOWN to HomeAction.OPEN_DRAWER,
            Gesture.SWIPE_DOWN to HomeAction.OPEN_NOTIFICATIONS,
            Gesture.PINCH_OUT to HomeAction.OPEN_RECENTS,
            Gesture.TWO_FINGER_UP to HomeAction.OPEN_EDIT_MODE,
            Gesture.DOUBLE_TAP to HomeAction.LOCK_SCREEN,
            Gesture.PINCH_IN to HomeAction.OPEN_QUICK_SETTINGS,
        )
        assertEquals(expected, Gesture.entries.associateWith { GestureClassifier.actionFor(it) })
    }

    @Test
    fun `近い場所を続けて2回タップするとダブルタップ`() {
        val d = DoubleTapDetector(timeoutMillis = 300, slop = 50f)
        assertFalse(d.onTap(1000, Point(100f, 100f)))
        assertTrue(d.onTap(1200, Point(120f, 110f)))
    }

    @Test
    fun `間が空くか離れた場所ならダブルタップにならない`() {
        val d = DoubleTapDetector(timeoutMillis = 300, slop = 50f)
        assertFalse(d.onTap(1000, Point(100f, 100f)))
        assertFalse(d.onTap(1400, Point(100f, 100f)))
        assertFalse(d.onTap(1500, Point(400f, 100f)))
    }

    @Test
    fun `3回続けてタップしても2回目でしかダブルタップにならない`() {
        val d = DoubleTapDetector(timeoutMillis = 300, slop = 50f)
        val results = listOf(1000L, 1100L, 1200L).map { d.onTap(it, Point(0f, 0f)) }
        assertEquals(listOf(false, true, false), results)
    }

    @Test
    fun `間にスワイプが挟まると数え直す`() {
        val d = DoubleTapDetector(timeoutMillis = 300, slop = 50f)
        d.onTap(1000, Point(0f, 0f))
        d.reset()
        assertFalse(d.onTap(1100, Point(0f, 0f)))
    }
}
