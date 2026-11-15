package io.github.tkdev106.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WallpaperRotationTest {
    private val hour = WallpaperRotation.INTERVAL_MILLIS

    @Test
    fun `一度も替えていなければ替える`() {
        assertTrue(WallpaperRotation.isDue(null, 1_000L))
    }

    @Test
    fun `前に替えてから1時間たっていなければ替えない`() {
        assertFalse(WallpaperRotation.isDue(10 * hour, 11 * hour - 1))
    }

    @Test
    fun `前に替えてからちょうど1時間で替える`() {
        assertTrue(WallpaperRotation.isDue(10 * hour, 11 * hour))
    }

    @Test
    fun `時計が戻って前に替えた時刻が未来なら替える`() {
        assertTrue(WallpaperRotation.isDue(10 * hour, 9 * hour))
    }

    @Test
    fun `画像が無ければ選ばない`() {
        assertNull(WallpaperRotation.pick(emptyList(), null))
    }

    @Test
    fun `画像が2枚以上あれば今の画像とは別のものを選ぶ`() {
        val names = listOf("a.jpg", "b.jpg", "c.jpg")
        repeat(50) { seed ->
            assertNotEquals("b.jpg", WallpaperRotation.pick(names, "b.jpg", Random(seed)))
        }
    }

    @Test
    fun `画像が1枚だけならその画像を選ぶ`() {
        assertEquals("a.jpg", WallpaperRotation.pick(listOf("a.jpg"), "a.jpg"))
    }

    @Test
    fun `選ぶのはフォルダの中の画像のどれか`() {
        val names = listOf("a.jpg", "b.jpg", "c.jpg")
        val picked = (0 until 100).map { WallpaperRotation.pick(names, null, Random(it)) }.toSet()
        assertEquals(names.toSet(), picked)
    }
}
