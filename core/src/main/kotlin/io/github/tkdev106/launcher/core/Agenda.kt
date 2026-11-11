package io.github.tkdev106.launcher.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 端末のカレンダーの予定1件。時刻はエポックミリ秒。
 * 終日の予定は UTC の0時区切りで入っている（CalendarContract の決まり）ので、日付は UTC で読む。
 */
data class CalendarEvent(
    val id: Long,
    val title: String?,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    /** 予定が入っているカレンダーの既定の色（ARGB）。予定ごとの色ではなく、家族と自分のカレンダーを見分けるため */
    val calendarColor: Int? = null,
)

data class AgendaLine(val eventId: Long, val begin: Long, val end: Long, val whenText: String, val title: String, val color: Int? = null)

object Agenda {
    /** 今日から1か月先まで。ホームでは3行の高さに収め、残りはスクロールで見る */
    const val DAYS = 31L

    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val weekday = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
    private val date = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    /**
     * 今日から1か月分のうち、まだ終わっていない予定を始まる順に並べる。
     * 曜日だけで分かるのは1週間以内なので、それより先は日付で書く。
     */
    fun lines(events: List<CalendarEvent>, now: LocalDateTime, zone: ZoneId): List<AgendaLine> {
        val today = now.toLocalDate()
        val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
        val limitDate = today.plusDays(DAYS)
        return events
            .map { it to startDate(it, zone) }
            .filter { (e, date) -> endMillis(e, zone) > nowMillis && date < limitDate }
            .sortedWith(compareBy<Pair<CalendarEvent, LocalDate>> { it.second }.thenBy { !it.first.allDay }.thenBy { it.first.begin })
            .map { (e, d) ->
                val day = when {
                    d == today -> "TODAY"
                    d < today.plusDays(7) -> d.format(weekday)
                    else -> d.format(date)
                }.uppercase(Locale.ENGLISH)
                val whenText = if (e.allDay) day else "$day ${Instant.ofEpochMilli(e.begin).atZone(zone).format(time)}"
                AgendaLine(e.id, e.begin, e.end, whenText, e.title?.takeIf { it.isNotBlank() } ?: "No title", e.calendarColor)
            }
    }

    private fun startDate(e: CalendarEvent, zone: ZoneId): LocalDate =
        if (e.allDay) Instant.ofEpochMilli(e.begin).atZone(ZoneOffset.UTC).toLocalDate()
        else Instant.ofEpochMilli(e.begin).atZone(zone).toLocalDate()

    /** 終日の予定は、その日の終わり（端末の時間帯）まで続くものとして扱う */
    private fun endMillis(e: CalendarEvent, zone: ZoneId): Long =
        if (e.allDay) Instant.ofEpochMilli(e.end).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        else e.end
}
