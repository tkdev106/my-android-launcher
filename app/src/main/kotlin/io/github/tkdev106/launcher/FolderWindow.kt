package io.github.tkdev106.launcher

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.tkdev106.launcher.Ui.dp
import io.github.tkdev106.launcher.core.AppKey

/**
 * フォルダの窓（docs/spec.md「見た目」）。押したフォルダのアイコンのすぐ上（画面の上半分なら下）に、中身を全部並べて開く。
 * 画面全体を覆い、窓の外をタップすると閉じる。PopupWindow にしないのは、ホームのジェスチャーや戻る操作と1か所でまとめて扱うため。
 */
class FolderWindow(context: Context, private val onLaunch: (AppKey, View) -> Unit) : FrameLayout(context) {
    private val panel = LinearLayout(context)
    // 中身が画面に収まらないほど多いときだけ縦にめくれるようにする
    private val frame = ScrollView(context)
    private val iconPx = dp(52)
    private val gap = dp(16)
    private val pad = dp(14)

    init {
        visibility = GONE
        isClickable = true
        setOnClickListener { close() }
        panel.apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        frame.addView(panel)
        frame.background = Ui.pill(context, 0xE00B0B0A.toInt(), 28f, Ui.BORDER)
        frame.isClickable = true
        frame.clipToOutline = true
        addView(frame, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))
    }

    val isOpen get() = visibility == VISIBLE

    var onClosed: () -> Unit = {}

    fun open(apps: List<AppKey>, anchor: View) {
        val icons = Launcher.icons(context)
        val catalog = Launcher.catalog(context)
        val columns = apps.size.coerceIn(1, COLUMNS)
        val rows = (apps.size + columns - 1) / columns
        panel.removeAllViews()
        // 大きさを先に決めて置き場所を計算するため、GridLayout を使わず固定の大きさの行を積む
        apps.chunked(columns).forEachIndexed { r, chunk ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            chunk.forEachIndexed { i, key ->
                val v = IconView(context)
                v.contentDescription = catalog.label(key)
                v.load(key to iconPx) { icons.app(key, iconPx) }
                v.setOnClickListener {
                    onLaunch(key, it)
                    close()
                }
                row.addView(v, LinearLayout.LayoutParams(iconPx, iconPx).apply { if (i > 0) marginStart = gap })
            }
            panel.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, iconPx).apply { if (r > 0) topMargin = gap })
        }
        val w = pad * 2 + columns * iconPx + (columns - 1) * gap
        val full = pad * 2 + rows * iconPx + (rows - 1) * gap

        val parent = parent as View
        val areaW = parent.width - parent.paddingLeft - parent.paddingRight
        val areaH = parent.height - parent.paddingTop - parent.paddingBottom
        val me = IntArray(2).also { parent.getLocationInWindow(it) }
        val at = IntArray(2).also { anchor.getLocationInWindow(it) }
        val ax = at[0] - me[0] - parent.paddingLeft
        val ay = at[1] - me[1] - parent.paddingTop
        val margin = dp(12)
        val h = full.coerceAtMost(areaH - margin * 2)
        val x = (ax + anchor.width / 2 - w / 2).coerceIn(margin, (areaW - w - margin).coerceAtLeast(margin))
        val below = ay + anchor.height / 2 < areaH / 2
        val y = (if (below) ay + anchor.height + dp(8) else ay - h - dp(8)).coerceIn(margin, (areaH - h - margin).coerceAtLeast(margin))
        frame.scrollTo(0, 0)
        frame.layoutParams = (frame.layoutParams as LayoutParams).apply {
            width = w
            height = h
            leftMargin = x
            topMargin = y
        }
        // アイコンから広がって見えるよう、拡大の中心をアイコンの側に置く
        frame.pivotX = (ax + anchor.width / 2 - x).toFloat().coerceIn(0f, w.toFloat())
        frame.pivotY = if (below) 0f else h.toFloat()

        visibility = VISIBLE
        alpha = 0f
        frame.scaleX = 0.85f
        frame.scaleY = 0.85f
        animate().alpha(1f).setDuration(140).start()
        frame.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
    }

    fun close() {
        if (!isOpen) return
        animate().alpha(0f).setDuration(120).withEndAction {
            visibility = GONE
            onClosed()
        }.start()
    }

    companion object {
        private const val COLUMNS = 4
    }
}
