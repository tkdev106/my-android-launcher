package io.github.tkdev106.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import io.github.tkdev106.launcher.Ui.dp
import io.github.tkdev106.launcher.core.AgendaLine
import io.github.tkdev106.launcher.core.ClockText
import io.github.tkdev106.launcher.core.DayForecast
import io.github.tkdev106.launcher.core.JmaForecast
import io.github.tkdev106.launcher.core.WeatherKind
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/** 自分で毎分の放送を受けず、ホームの1つの受け口から update してもらう（受け口を増やさないため） */
class ClockPanel(context: Context) : LinearLayout(context) {
    private val time = Ui.text(context, 110f, Ui.serif(context)).apply { letterSpacing = -0.02f }
    private val date = Ui.text(context, 11f, Ui.mono(context), Ui.TEXT, 0.2f)

    init {
        orientation = VERTICAL
        addView(time)
        addView(date, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
    }

    fun update(now: LocalDateTime) {
        time.text = ClockText.time(now)
        date.text = ClockText.date(now)
    }
}

fun weatherIcon(kind: WeatherKind?): Int = when (kind) {
    WeatherKind.SUNNY -> R.drawable.ic_weather_sunny
    WeatherKind.RAIN -> R.drawable.ic_weather_rain
    WeatherKind.SNOW -> R.drawable.ic_weather_snow
    else -> R.drawable.ic_weather_cloudy
}

class WeatherPanel(context: Context, onOpen: () -> Unit) : LinearLayout(context) {
    private val todayIcon = ImageView(context)
    private val todayTemp = Ui.text(context, 50f, Ui.serif(context))
    private val todayLabel = Ui.text(context, 14f, Ui.sans(context))
    private val todaySub = Ui.text(context, 11f, Ui.mono(context), Ui.TEXT_DIM)
    private val rows = LinearLayout(context).apply { orientation = VERTICAL }

    init {
        orientation = VERTICAL
        visibility = GONE
        val today = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(todayIcon, LayoutParams(dp(36), dp(36)))
            addView(todayTemp, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = dp(14) })
            addView(LinearLayout(context).apply {
                orientation = VERTICAL
                addView(todayLabel)
                addView(todaySub, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(3) })
            }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = dp(14) })
        }
        addView(today)
        addView(rows, LayoutParams(dp(262), LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        todayIcon.setColorFilter(Ui.TEXT)
        isClickable = true
        contentDescription = "Yahoo!天気"
        setOnClickListener { onOpen() }
    }

    /** 大きく出すのは今の気温。観測がまだ取れていなければ今日の最高気温で代える */
    fun update(state: WeatherState?) {
        val days = state?.days
        if (days.isNullOrEmpty()) {
            visibility = GONE
            return
        }
        visibility = VISIBLE
        val t = days[0]
        val kind = JmaForecast.kind(t.weatherCode)
        todayIcon.setImageResource(weatherIcon(kind))
        todayTemp.text = (state.current ?: t.max ?: t.min)?.let { "$it°" } ?: "--°"
        todayLabel.text = kind?.label ?: "--"
        fun deg(v: Int?) = v?.let { "$it°" } ?: "--"
        todaySub.text = "H ${deg(t.max)} · L ${deg(t.min)} · RAIN ${t.pop ?: "--"}%"

        rows.removeAllViews()
        val rest = days.drop(1)
        val lo = rest.mapNotNull { it.min }.minOrNull() ?: 0
        val hi = rest.mapNotNull { it.max }.maxOrNull() ?: 1
        rest.forEach { d -> rows.addView(row(d, lo, hi), LayoutParams(LayoutParams.MATCH_PARENT, dp(24))) }
    }

    private fun row(d: DayForecast, lo: Int, hi: Int): View = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val day = Ui.text(context, 11f, Ui.mono(context), Ui.TEXT_DIM, 0.12f).apply {
            text = d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(Locale.ENGLISH)
        }
        addView(day, LayoutParams(dp(34), LayoutParams.WRAP_CONTENT))
        addView(ImageView(context).apply {
            setImageResource(weatherIcon(JmaForecast.kind(d.weatherCode)))
            setColorFilter(Ui.TEXT)
        }, LayoutParams(dp(18), dp(18)).apply { marginStart = dp(8) })
        addView(Ui.text(context, 11f, Ui.mono(context), Ui.TEXT_DIM).apply {
            text = d.min?.let { "$it°" } ?: "--"
            gravity = Gravity.END
        }, LayoutParams(dp(24), LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
        addView(RangeBar(context, d.min, d.max, lo, hi), LayoutParams(0, dp(2), 1f).apply { marginStart = dp(8) })
        addView(Ui.text(context, 11f, Ui.mono(context)).apply { text = d.max?.let { "$it°" } ?: "--" },
            LayoutParams(dp(28), LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
    }

    /** 最低〜最高の線。並べた日の中の最も低い値と最も高い値を両端にする */
    private class RangeBar(context: Context, val min: Int?, val max: Int?, val lo: Int, val hi: Int) : View(context) {
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.TRACK }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.TEXT }

        override fun onDraw(canvas: Canvas) {
            val r = height / 2f
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, track)
            if (min == null || max == null || hi <= lo) return
            val span = (hi - lo).toFloat()
            val l = (min - lo) / span * width
            val rr = (max - lo) / span * width
            canvas.drawRoundRect(l, 0f, rr.coerceAtLeast(l + height), height.toFloat(), r, r, fill)
        }
    }
}

/**
 * 予定一覧。許可が無いときと予定が無いときも1行出す（何も出ないと、壊れているのか予定が無いのか見分けられないため）。
 */
class AgendaPanel(
    context: Context,
    private val onOpen: (AgendaLine) -> Unit,
    private val onAllow: () -> Unit,
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
    }

    fun update(lines: List<AgendaLine>, permitted: Boolean) {
        removeAllViews()
        if (!permitted) {
            addView(note("CALENDAR", "タップしてカレンダーを許可").apply { setOnClickListener { onAllow() } }, LayoutParams(dp(300), dp(28)))
            return
        }
        if (lines.isEmpty()) {
            addView(note("NEXT 3 DAYS", "No events"), LayoutParams(dp(300), dp(28)))
            return
        }
        lines.forEach { line ->
            addView(row(line.whenText, line.title).apply {
                isClickable = true
                contentDescription = "${line.whenText} ${line.title}"
                setOnClickListener { onOpen(line) }
            }, LayoutParams(dp(300), dp(28)))
        }
    }

    private fun note(head: String, body: String) = row(head, body, Ui.TEXT_DIM)

    private fun row(head: String, body: String, bodyColor: Int = Ui.TEXT) = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(Ui.text(context, 10f, Ui.mono(context), Ui.TEXT_DIM).apply { text = head }, LayoutParams(dp(76), LayoutParams.WRAP_CONTENT))
        addView(Ui.text(context, 13f, Ui.sans(context), bodyColor).apply {
            text = body
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) })
    }
}

data class NowPlayingState(val art: Bitmap?, val title: String?, val artist: String?, val playing: Boolean)

/**
 * 再生中の曲。ジャケットを大きめに、右に曲名・アーティスト名と再生/一時停止ボタン。
 * 何も再生していないときは Apple Music を開くボタンにする（docs/spec.md「内蔵パネル」）。
 * 通知へのアクセスが無いと曲を読めないので、そのときは許可の画面を開くボタンにする。
 */
class NowPlayingPanel(
    context: Context,
    private val onOpen: (View) -> Unit,
    private val onToggle: () -> Unit,
    private val onAllow: () -> Unit,
) : LinearLayout(context) {
    private var hasAccess = true
    private val art = ImageView(context)
    private val title = Ui.text(context, 17f, Ui.sans(context)).apply { maxLines = 3; ellipsize = TextUtils.TruncateAt.END }
    private val artist = Ui.text(context, 13f, Ui.sans(context), Ui.TEXT_DIM).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
    private val toggle = ImageView(context)
    private val placeholder = IconView(context)
    private val artFrame = FrameLayout(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.BOTTOM
        art.scaleType = ImageView.ScaleType.CENTER_CROP
        artFrame.apply {
            isClickable = true
            setOnClickListener { onOpen(it) }
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, dp(4).toFloat())
            }
            clipToOutline = true
            elevation = dp(18).toFloat()
            addView(art, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        placeholder.contentDescription = "Apple Music"
        placeholder.setOnClickListener { if (hasAccess) onOpen(it) else onAllow() }
        placeholder.set(Launcher.icons(context).glyph(R.drawable.ic_music, dp(56), Ui.CIRCLE))

        toggle.apply {
            background = Ui.pill(context, Ui.TEXT, 26f)
            setColorFilter(Ui.INK)
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            setOnClickListener { onToggle() }
            elevation = dp(6).toFloat()
        }
        val side = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(title)
            addView(artist, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
            addView(toggle, LayoutParams(dp(52), dp(52)).apply { topMargin = dp(14) })
        }
        addView(artFrame, LayoutParams(dp(196), dp(196)))
        addView(placeholder, LayoutParams(dp(56), dp(56)))
        addView(side, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(16) })
        update(null, true)
    }

    fun update(state: NowPlayingState?, hasAccess: Boolean) {
        this.hasAccess = hasAccess
        val playing = state != null
        artFrame.visibility = if (playing) VISIBLE else GONE
        placeholder.visibility = if (playing) GONE else VISIBLE
        placeholder.contentDescription = if (hasAccess) "Apple Music" else "通知へのアクセスを許可"
        // 許可が無いときは、ボタンの横に何のためのボタンかを書く
        (getChildAt(2) as View).visibility = if (playing || !hasAccess) VISIBLE else GONE
        toggle.visibility = if (playing) VISIBLE else GONE
        if (state == null) {
            title.text = if (hasAccess) "" else "Now Playing"
            artist.text = if (hasAccess) "" else "タップして通知へのアクセスを許可"
            return
        }
        art.setImageBitmap(state.art)
        art.setBackgroundColor(if (state.art == null) Ui.CIRCLE else 0)
        title.text = state.title ?: ""
        artist.text = state.artist ?: ""
        toggle.setImageResource(if (state.playing) R.drawable.ic_pause else R.drawable.ic_play)
        toggle.contentDescription = if (state.playing) "一時停止" else "再生"
        val box = dp(18)
        toggle.setPadding(box, box, box, box)
    }
}

fun circleButton(context: Context, label: String, sizePx: Int, bitmap: () -> Bitmap, onClick: (View) -> Unit): IconView =
    IconView(context).apply {
        contentDescription = label
        layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
        load(label to sizePx to System.identityHashCode(bitmap)) { bitmap() }
        setOnClickListener(onClick)
    }

