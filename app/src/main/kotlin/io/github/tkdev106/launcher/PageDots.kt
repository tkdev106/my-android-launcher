package io.github.tkdev106.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import io.github.tkdev106.launcher.Ui.dp

/** ドロワーの下のページの点。今のページだけ白く、ほかは薄く */
class PageDots(context: Context) : View(context) {
    private val on = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.TEXT }
    private val off = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.TEXT; alpha = 77 }
    var count = 1
        set(v) { field = v; invalidate() }
    var current = 0
        set(v) { field = v; invalidate() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(6))
    }

    override fun onDraw(canvas: Canvas) {
        if (count <= 1) return
        val r = dp(3).toFloat()
        val gap = dp(8)
        val total = count * dp(6) + (count - 1) * gap
        var x = (width - total) / 2f + r
        for (i in 0 until count) {
            canvas.drawCircle(x, height / 2f, r, if (i == current) on else off)
            x += dp(6) + gap
        }
    }
}
