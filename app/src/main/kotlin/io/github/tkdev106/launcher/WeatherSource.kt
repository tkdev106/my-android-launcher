package io.github.tkdev106.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
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

/**
 * 気象庁の予報を取ってきて覚えておく（docs/spec.md「内蔵パネル」）。
 * ホームを開いたときに refresh を呼び、前に取ってから1時間以上たっていれば裏の線で取り直す。常駐も定期取得もしない。
 */
class WeatherSource(private val context: Context) {
    private val cacheFile = File(context.filesDir, "weather.json")
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false

    fun cached(today: LocalDate): List<DayForecast>? = runCatching {
        val j = Json.parse(cacheFile.readText())
        val area = JmaArea(j["office"].string!!, j["area"].string!!, j["station"].string!!)
        JmaForecast.parse(j["forecast"].string!!, area, today)
    }.getOrNull()

    fun refresh(today: LocalDate, onUpdated: (List<DayForecast>) -> Unit) {
        val age = System.currentTimeMillis() - cacheFile.lastModified()
        if (cacheFile.exists() && age < REFRESH_MILLIS || running) return
        running = true
        Launcher.background.execute {
            try {
                val area = currentArea()
                val text = fetch("https://www.jma.go.jp/bosai/forecast/data/forecast/${area.office}.json")
                val days = JmaForecast.parse(text, area, today)
                val tmp = File(context.filesDir, "weather.json.tmp")
                tmp.writeText(Json.obj(
                    "office" to Json.Str(area.office), "area" to Json.Str(area.area),
                    "station" to Json.Str(area.station), "forecast" to Json.Str(text),
                ).stringify())
                tmp.renameTo(cacheFile)
                main.post { onUpdated(days) }
            } catch (_: Exception) {
                // 通信できないときは前の予報を出したままにし、次にホームを開いたときにまた試す
            } finally {
                running = false
            }
        }
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
        private const val REFRESH_MILLIS = 60 * 60 * 1000L
    }
}
