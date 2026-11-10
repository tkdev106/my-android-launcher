package io.github.tkdev106.launcher

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import io.github.tkdev106.launcher.core.Agenda
import io.github.tkdev106.launcher.core.AgendaLine
import io.github.tkdev106.launcher.core.CalendarEvent
import java.time.LocalDateTime
import java.time.ZoneId

/** 端末のカレンダー（Google カレンダーの同期分）から予定を読む。許可が無ければ空 */
class CalendarSource(private val context: Context) {

    fun lines(now: LocalDateTime, zone: ZoneId): List<AgendaLine> {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return emptyList()
        // 終日の予定は UTC 区切りなので、前後に1日ずつ広く取ってから core で絞る
        val from = now.toLocalDate().minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = now.toLocalDate().plusDays(Agenda.DAYS + 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )
        val events = runCatching {
            context.contentResolver.query(uri, projection, "${CalendarContract.Instances.VISIBLE} = 1", null, null)?.use { c ->
                buildList {
                    while (c.moveToNext()) add(CalendarEvent(c.getLong(0), c.getString(1), c.getLong(2), c.getLong(3), c.getInt(4) == 1))
                }
            }
        }.getOrNull() ?: emptyList()
        return Agenda.lines(events, now, zone)
    }

    /** その予定を Google カレンダーで開く。入っていなければ端末の既定のカレンダーで開く */
    fun open(line: AgendaLine) {
        val intent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, line.eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, line.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, line.end)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val google = Intent(intent).setPackage(GOOGLE_CALENDAR)
        runCatching { context.startActivity(google) }.recoverCatching { context.startActivity(intent) }
    }

    companion object {
        const val GOOGLE_CALENDAR = "com.google.android.calendar"
    }
}
