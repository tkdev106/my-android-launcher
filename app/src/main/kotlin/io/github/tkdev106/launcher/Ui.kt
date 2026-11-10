package io.github.tkdev106.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.TextView

/** 色と書体。値は docs/spec.md「見た目」と H 案のデザインから */
object Ui {
    val TEXT = Color.parseColor("#F1F0EC")
    val TEXT_DIM = Color.parseColor("#D6D4CF")
    val INK = Color.parseColor("#0B0B0A")
    val CIRCLE = Color.argb(140, 11, 11, 10)
    val CIRCLE_DOCK = Color.argb(153, 11, 11, 10)
    val BORDER = Color.argb(36, 241, 240, 236)
    val TRACK = Color.argb(56, 241, 240, 236)
    private val SHADOW = Color.argb(140, 0, 0, 0)

    private var serif: Typeface? = null
    private var sans: Typeface? = null
    private var mono: Typeface? = null

    fun serif(c: Context): Typeface = serif ?: c.resources.getFont(R.font.instrument_serif).also { serif = it }
    fun sans(c: Context): Typeface = sans ?: c.resources.getFont(R.font.geist).also { sans = it }
    fun mono(c: Context): Typeface = mono ?: c.resources.getFont(R.font.geist_mono).also { mono = it }

    fun Context.dp(v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()
    fun Context.dp(v: Int): Int = dp(v.toFloat())
    fun View.dp(v: Int): Int = context.dp(v)
    fun View.dp(v: Float): Int = context.dp(v)

    /** 壁紙の上に置く文字。字の大きさは端末の文字サイズ設定に引きずられないよう dp で決める（固定レイアウトのため） */
    fun text(c: Context, sizeDp: Float, face: Typeface, color: Int = TEXT, letterSpacing: Float = 0f): TextView =
        TextView(c).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp)
            typeface = face
            setTextColor(color)
            this.letterSpacing = letterSpacing
            includeFontPadding = false
            setShadowLayer(c.dp(10f).toFloat(), 0f, c.dp(1f).toFloat(), SHADOW)
        }

    fun pill(c: Context, fill: Int, radiusDp: Float, borderColor: Int? = null): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = c.dp(radiusDp).toFloat()
        if (borderColor != null) setStroke(c.dp(1f).coerceAtLeast(1), borderColor)
    }
}
