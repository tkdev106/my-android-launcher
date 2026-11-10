package io.github.tkdev106.launcher.core

import java.time.LocalDate
import java.time.OffsetDateTime

/** 気象庁の予報を取りに行く単位。office は府県予報区、area は一次細分区域、station は気温の観測点 */
data class JmaArea(val office: String, val area: String, val station: String)

object JmaAreas {
    val TOKYO = JmaArea("130000", "130010", "44132")

    /**
     * 市区町村名と都道府県名から予報区域を引く。table は jma-areas.csv の中身
     * （`市区町村名,府県予報区名,府県予報区,一次細分区域,観測点`）。
     * names は住所から取れた市区町村の候補（市区町村・郡・区の順）で、先に当たったものを使う。
     * 同じ名前の市区町村が複数あれば、府県予報区名が都道府県名と合うものを選ぶ。見つからなければ東京地方。
     */
    fun find(table: String, names: List<String?>, adminArea: String?): JmaArea {
        val rows = table.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split(',') }
            .filter { it.size >= 5 }
            .toList()
        val wanted = names.mapNotNull { it?.trim() }.filter { it.isNotEmpty() }
        val candidates = wanted.firstNotNullOfOrNull { name -> rows.filter { it[0] == name }.ifEmpty { null } } ?: emptyList()
        val chosen = candidates.firstOrNull { adminArea != null && it[1] == adminArea }
            ?: candidates.firstOrNull { adminArea != null && (it[1].contains(adminArea) || adminArea.contains(it[1])) }
            ?: candidates.firstOrNull()
            ?: return TOKYO
        return JmaArea(chosen[2], chosen[3], chosen[4])
    }
}

/** 1日分の予報。値が無いところは null */
data class DayForecast(val date: LocalDate, val weatherCode: Int?, val min: Int?, val max: Int?, val pop: Int?)

enum class WeatherKind(val label: String) { SUNNY("Sunny"), CLOUDY("Cloudy"), RAIN("Rain"), SNOW("Snow") }

object JmaForecast {
    const val DAYS = 3

    /** 気象庁の天気コードの百の位で4つに分ける（100番台は晴れ、200 くもり、300 雨、400 雪） */
    fun kind(code: Int?): WeatherKind? = when (code?.div(100)) {
        1 -> WeatherKind.SUNNY
        2 -> WeatherKind.CLOUDY
        3 -> WeatherKind.RAIN
        4 -> WeatherKind.SNOW
        else -> null
    }

    /**
     * `forecast/{office}.json` を読み、today から3日分にまとめる。
     * 今日・明日は詳しい予報（1つ目）を優先し、無い値は週間予報（2つ目）で埋める。
     */
    fun parse(text: String, area: JmaArea, today: LocalDate): List<DayForecast> {
        val root = Json.parse(text).list
        val short = root.getOrNull(0)?.get("timeSeries").list
        val weekly = root.getOrNull(1)?.get("timeSeries").list

        val codes = HashMap<LocalDate, Int>()
        val pops = HashMap<LocalDate, Int>()
        val mins = HashMap<LocalDate, Int>()
        val maxs = HashMap<LocalDate, Int>()

        // 週間予報を先に入れ、詳しい予報で上書きする
        weekly.getOrNull(0)?.let { ts ->
            val a = pickArea(ts, area.area)
            fill(ts, a, "weatherCodes", codes)
            fill(ts, a, "pops", pops)
        }
        weekly.getOrNull(1)?.let { ts ->
            val a = pickArea(ts, area.station)
            fill(ts, a, "tempsMin", mins)
            fill(ts, a, "tempsMax", maxs)
        }
        short.getOrNull(0)?.let { ts -> fill(ts, pickArea(ts, area.area), "weatherCodes", codes) }
        short.getOrNull(1)?.let { ts ->
            // 6時間ごとの降水確率は、その日のいちばん高い値を使う
            val a = pickArea(ts, area.area)
            val shortPops = HashMap<LocalDate, Int>()
            times(ts).zip(a?.get("pops").list.map { it.string?.toIntOrNull() }).forEach { (t, v) ->
                if (v != null) shortPops.merge(t.toLocalDate(), v, ::maxOf)
            }
            pops.putAll(shortPops)
        }
        short.getOrNull(2)?.let { ts ->
            // 0時の値は朝の最低気温、9時の値は日中の最高気温
            val a = pickArea(ts, area.station)
            times(ts).zip(a?.get("temps").list.map { it.string?.toIntOrNull() }).forEach { (t, v) ->
                if (v == null) return@forEach
                if (t.hour < 9) mins[t.toLocalDate()] = v else maxs[t.toLocalDate()] = v
            }
        }

        return (0 until DAYS).map { i ->
            val d = today.plusDays(i.toLong())
            DayForecast(d, codes[d], mins[d], maxs[d], pops[d])
        }
    }

    private fun times(ts: Json): List<OffsetDateTime> = ts["timeDefines"].list.mapNotNull { j ->
        j.string?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
    }

    private fun pickArea(ts: Json, code: String): Json? {
        val areas = ts["areas"].list
        return areas.firstOrNull { it["area"]["code"].string == code } ?: areas.firstOrNull()
    }

    private fun fill(ts: Json, area: Json?, key: String, into: MutableMap<LocalDate, Int>) {
        times(ts).zip(area?.get(key).list).forEach { (t, v) ->
            v.string?.toIntOrNull()?.let { into[t.toLocalDate()] = it }
        }
    }
}
