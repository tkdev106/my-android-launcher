package io.github.tkdev106.launcher.core

import kotlin.random.Random

/**
 * 壁紙の入れ替え（docs/spec.md「壁紙」）。ホームを開いたときに、前に替えてから1時間たっていれば次の画像にする。
 * 裏で時刻ちょうどに替えないのは、常駐するものを増やさないため。
 */
object WallpaperRotation {
    const val INTERVAL_MILLIS = 60 * 60 * 1000L

    /** 一度も替えていないか、前に替えてから1時間たったら替える。時計が戻ったとき（前に替えた時刻が未来）も替える */
    fun isDue(lastChangedMillis: Long?, nowMillis: Long): Boolean =
        lastChangedMillis == null || nowMillis - lastChangedMillis >= INTERVAL_MILLIS || nowMillis < lastChangedMillis

    /** フォルダの画像から無作為に1つ選ぶ。2枚以上あれば今の画像は避ける。画像が無ければ null */
    fun pick(names: List<String>, current: String?, random: Random = Random.Default): String? {
        if (names.isEmpty()) return null
        val others = names.filter { it != current }
        return (others.ifEmpty { names }).random(random)
    }
}
