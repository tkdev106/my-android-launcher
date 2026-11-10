package io.github.tkdev106.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import io.github.tkdev106.launcher.core.Amedas
import io.github.tkdev106.launcher.core.DayForecast
import io.github.tkdev106.launcher.core.JmaArea
import io.github.tkdev106.launcher.core.JmaAreas
import io.github.tkdev106.launcher.core.JmaForecast
import io.github.tkdev106.launcher.core.Json
import io.github.tkdev106.launcher.core.get
import io.github.tkdev106.launcher.core.string
import io.github.tkdev106.launcher.core.stringify
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 予報3日分と今の気温。今の気温が取れていなければ null */
data class WeatherState(val days: List<DayForecast>, val current: Int?)

/**
 * 気象庁の予報とアメダスの今の気温を取ってきて覚えておく（docs/spec.md「内蔵パネル」）。
 * ホームを開いたときに refresh を呼び、予報は1時間、今の気温は10分たっていれば裏の線で取り直す。常駐も定期取得もしない。
 */
class WeatherSource(private val context: Context) {
    private val cacheFile = File(context.filesDir, "weather.json")
    private val observedFile = File(context.filesDir, "observed.txt")
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false

    private fun cachedArea(j: Json) = JmaArea(j["office"].string!!, j["area"].string!!, j["station"].string!!)

    fun cached(today: LocalDate): WeatherState? = runCatching {
        val j = Json.parse(cacheFile.readText())
        WeatherState(JmaForecast.parse(j["forecast"].string!!, cachedArea(j), today), observed())
    }.getOrNull()

    /** 今の気温は古すぎると誤解を招くので、1時間より前の観測は出さない */
    private fun observed(): Int? {
        if (!observedFile.exists() || System.currentTimeMillis() - observedFile.lastModified() > FORECAST_MILLIS) return null
        return runCatching { observedFile.readText().trim().toInt() }.getOrNull()
    }

    fun refresh(today: LocalDate, onUpdated: (WeatherState) -> Unit) {
        val now = System.currentTimeMillis()
        val forecastStale = !cacheFile.exists() || now - cacheFile.lastModified() >= FORECAST_MILLIS
        val observedStale = !observedFile.exists() || now - observedFile.lastModified() >= OBSERVED_MILLIS
        if (!forecastStale && !observedStale || running) return
        running = true
        Launcher.background.execute {
            try {
                if (forecastStale) {
                    val area = currentArea()
                    val text = fetch("https://www.jma.go.jp/bosai/forecast/data/forecast/${area.office}.json")
                    JmaForecast.parse(text, area, today)
                    writeAtomically(cacheFile, Json.obj(
                        "office" to Json.Str(area.office), "area" to Json.Str(area.area),
                        "station" to Json.Str(area.station), "forecast" to Json.Str(text),
                    ).stringify())
                }
                // 今の気温が取れなくても予報は出す。失敗しても空で書いておき、ホームを開くたびに通信しないよう10分待つ
                val temp = runCatching {
                    val station = cachedArea(Json.parse(cacheFile.readText())).station
                    Amedas.latestTemp(fetch(Amedas.pointUrl(station, fetch(Amedas.LATEST_TIME_URL))))
                }.getOrNull()
                writeAtomically(observedFile, temp?.toString() ?: "")
                cached(today)?.let { main.post { onUpdated(it) } }
            } catch (_: Exception) {
                // 通信できないときは前の予報を出したままにし、次にホームを開いたときにまた試す
            } finally {
                running = false
            }
        }
    }

    private fun writeAtomically(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        tmp.renameTo(file)
    }

    /** Yahoo!天気のアプリがあればそれを、無ければ Web の Yahoo!天気を開く */
    fun openYahoo() {
        val intent = context.packageManager.getLaunchIntentForPackage(YAHOO_WEATHER)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://weather.yahoo.co.jp/weather/"))
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** 端末が最後に取った大まかな位置から市区町村を引き、予報区域に当てる。取れなければ東京地方 */
    private fun currentArea(): JmaArea {
        val location = lastLocation() ?: return JmaAreas.TOKYO
        val table = context.assets.open("jma-areas.csv").bufferedReader().use { it.readText() }
        val latch = CountDownLatch(1)
        var names: List<String?> = emptyList()
        var admin: String? = null
        Geocoder(context, Locale.JAPAN).getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<android.location.Address>) {
                addresses.firstOrNull()?.let { a ->
                    names = listOf(a.locality, a.subAdminArea, a.subLocality)
                    admin = a.adminArea
                }
                latch.countDown()
            }

            override fun onError(errorMessage: String?) = latch.countDown()
        })
        latch.await(10, TimeUnit.SECONDS)
        return JmaAreas.find(table, names, admin)
    }

    // 許可は先頭で確かめている。lint は runCatching の中まで追えないので止める
    @SuppressLint("MissingPermission")
    private fun lastLocation(): Location? {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val lm = context.getSystemService(LocationManager::class.java)
        return listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    private fun fetch(url: String): String {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            if (conn.responseCode != 200) throw java.io.IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val FORECAST_MILLIS = 60 * 60 * 1000L
        private const val OBSERVED_MILLIS = 10 * 60 * 1000L
        const val YAHOO_WEATHER = "jp.co.yahoo.android.weather.type1"
    }
}
