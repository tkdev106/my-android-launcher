package io.github.tkdev106.launcher

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import io.github.tkdev106.launcher.Ui.dp
import io.github.tkdev106.launcher.core.AppKey

/**
 * フォルダの窓（docs/spec.md「見た目」: フォルダは窓で開く）。画面全体を覆い、窓の外をタップすると閉じる。
 * PopupWindow にしないのは、ホームのジェスチャーや戻る操作と1か所でまとめて扱うため。
 */
class FolderWindow(context: Context, private val onLaunch: (AppKey, View) -> Unit) : FrameLayout(context) {
    private val panel = LinearLayout(context)
    private val title = Ui.text(context, 14f, Ui.sans(context))
    private val grid = GridLayout(context)
    private val iconPx = dp(52)

    init {
        visibility = GONE
        isClickable = true
        setOnClickListener { close() }
        panel.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = Ui.pill(context, 0xE00B0B0A.toInt(), 28f, Ui.BORDER)
            setPadding(dp(20), dp(18), dp(20), dp(20))
            isClickable = true
            addView(title, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(16) })
            addView(grid)
        }
        grid.columnCount = 4
        addView(panel, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    val isOpen get() = visibility == VISIBLE

    /** 閉じ終わったとき（ホームのジェスチャーを戻すため） */
    var onClosed: () -> Unit = {}

    fun open(name: String, apps: List<AppKey>) {
        val icons = Launcher.icons(context)
        title.text = name
        grid.removeAllViews()
        grid.columnCount = apps.size.coerceIn(1, 4)
        apps.forEach { key ->
            val v = IconView(context)
            v.contentDescription = Launcher.catalog(context).label(key)
            v.load(key to iconPx) { icons.app(key, iconPx) }
            v.setOnClickListener {
                onLaunch(key, it)
                close()
            }
            grid.addView(v, GridLayout.LayoutParams().apply {
                width = iconPx
                height = iconPx
                setMargins(dp(8), dp(8), dp(8), dp(8))
            })
        }
        visibility = VISIBLE
        alpha = 0f
        panel.scaleX = 0.92f
        panel.scaleY = 0.92f
        animate().alpha(1f).setDuration(140).start()
        panel.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
    }

    fun close() {
        if (!isOpen) return
        animate().alpha(0f).setDuration(120).withEndAction {
            visibility = GONE
            onClosed()
        }.start()
    }
}
