package io.github.tkdev106.launcher.core

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 時計の表記。端末の言語に関係なく英字にする（spec「内蔵パネル」） */
object ClockText {
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val date = DateTimeFormatter.ofPattern("EEEE '·' d MMMM", Locale.ENGLISH)

    fun time(now: LocalDateTime): String = now.format(time)

    /** 例: `TUESDAY · 10 NOVEMBER` */
    fun date(now: LocalDateTime): String = now.format(date).uppercase(Locale.ENGLISH)
}
