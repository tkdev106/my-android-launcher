package io.github.tkdev106.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class ClockTextTest {
    @Test
    fun `時刻は24時間制の時と分`() {
        assertEquals("09:05", ClockText.time(LocalDateTime.of(2026, 11, 10, 9, 5)))
    }

    @Test
    fun `日付は英字の大文字で曜日と日と月`() {
        assertEquals("TUESDAY · 10 NOVEMBER", ClockText.date(LocalDateTime.of(2026, 11, 10, 22, 10)))
    }
}

class AgendaTest {
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val now = LocalDateTime.of(2026, 11, 10, 12, 0)

    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, d, h, m).atZone(tokyo).toInstant().toEpochMilli()
    private fun utcDay(d: Int) = LocalDate.of(2026, 11, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private fun timed(id: Long, title: String?, d: Int, h: Int) = CalendarEvent(id, title, at(d, h), at(d, h + 1), false)
    private fun allDay(id: Long, title: String, d: Int) = CalendarEvent(id, title, utcDay(d), utcDay(d + 1), true)

    @Test
    fun `今日の終日の予定は TODAY、ほかの日は曜日と時刻で出す`() {
        val lines = Agenda.lines(listOf(timed(2, "羊文学", 11, 18), allDay(1, "テレ東解約", 10), timed(3, "会議", 12, 19)), now, tokyo)
        assertEquals(listOf("TODAY", "WED 18:00", "THU 19:00"), lines.map { it.whenText })
        assertEquals(listOf("テレ東解約", "羊文学", "会議"), lines.map { it.title })
    }

    @Test
    fun `今日の時刻つきの予定は TODAY と時刻`() {
        assertEquals("TODAY 15:00", Agenda.lines(listOf(timed(1, "a", 10, 15)), now, tokyo)[0].whenText)
    }

    @Test
    fun `終わった予定と3日より先の予定は出さない`() {
        val lines = Agenda.lines(listOf(timed(1, "済んだ", 10, 9), timed(2, "先", 13, 10), timed(3, "残る", 12, 22)), now, tokyo)
        assertEquals(listOf("残る"), lines.map { it.title })
    }

    @Test
    fun `3件までに絞る`() {
        val lines = Agenda.lines((1..5).map { timed(it.toLong(), "e$it", 11, it + 8) }, now, tokyo)
        assertEquals(listOf("e1", "e2", "e3"), lines.map { it.title })
    }

    @Test
    fun `題名が無い予定は No title`() {
        assertEquals("No title", Agenda.lines(listOf(timed(1, " ", 11, 10)), now, tokyo)[0].title)
    }
}

class WeatherTest {
    private val table = File("../app/src/main/assets/jma-areas.csv").readText()
    private val forecast = javaClass.getResource("/jma/tokyo-2025-04-03-17.json")!!.readText()

    @Test
    fun `渋谷区は東京地方`() {
        assertEquals(JmaArea("130000", "130010", "44132"), JmaAreas.find(table, listOf("渋谷区"), "東京都"))
    }

    @Test
    fun `苗場（湯沢町）は新潟県中越`() {
        assertEquals("150020", JmaAreas.find(table, listOf("湯沢町", "南魚沼郡"), "新潟県").area)
    }

    @Test
    fun `同じ名前の市は都道府県で見分ける`() {
        assertEquals("340000", JmaAreas.find(table, listOf("府中市"), "広島県").office)
        assertEquals("130000", JmaAreas.find(table, listOf("府中市"), "東京都").office)
    }

    @Test
    fun `市区町村の名前で当たらなければ次の候補（郡など）で探す`() {
        assertEquals("150020", JmaAreas.find(table, listOf("存在しない", "湯沢町"), "新潟県").area)
    }

    @Test
    fun `市区町村が分からなければ東京地方`() {
        assertEquals(JmaAreas.TOKYO, JmaAreas.find(table, listOf(null), null))
        assertEquals(JmaAreas.TOKYO, JmaAreas.find(table, listOf("どこでもない町"), "東京都"))
    }

    @Test
    fun `今日から3日分の天気・気温・降水確率をまとめる`() {
        val days = JmaForecast.parse(forecast, JmaAreas.TOKYO, LocalDate.of(2025, 4, 3))
        assertEquals(listOf(LocalDate.of(2025, 4, 3), LocalDate.of(2025, 4, 4), LocalDate.of(2025, 4, 5)), days.map { it.date })
        assertEquals(listOf(200, 101, 201), days.map { it.weatherCode })
        // 明日は詳しい予報の最低6度・最高17度、明後日は週間予報の値
        assertEquals(DayForecast(LocalDate.of(2025, 4, 4), 101, 6, 17, days[1].pop), days[1])
        assertEquals(7, days[2].min)
        assertEquals(18, days[2].max)
    }

    @Test
    fun `天気コードの百の位で晴れ・くもり・雨・雪に分ける`() {
        assertEquals(
            listOf(WeatherKind.SUNNY, WeatherKind.CLOUDY, WeatherKind.RAIN, WeatherKind.SNOW, null, null),
            listOf(101, 200, 313, 400, 999, null).map { JmaForecast.kind(it) },
        )
    }
}
