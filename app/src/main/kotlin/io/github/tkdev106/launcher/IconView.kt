package io.github.tkdev106.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.View

/**
 * 丸いアイコン1つ。絵は裏の線で作り、できたら描き直す。
 * ドロワーの百個あまりを開くたびに画面の線で描くと引っかかるため。
 */
class IconView(context: Context) : View(context) {
    private var bitmap: Bitmap? = null
    private var requestKey: Any? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val dst = android.graphics.RectF()

    init {
        isClickable = true
        isFocusable = true
    }

    /** cacheKey が同じなら描き直さない。make は裏の線で呼ばれる */
    fun load(cacheKey: Any, make: () -> Bitmap?) {
        if (cacheKey == requestKey && bitmap != null) return
        requestKey = cacheKey
        bitmap = null
        invalidate()
        Launcher.background.execute {
            val bmp = runCatching { make() }.getOrNull()
            main.post {
                if (requestKey == cacheKey) {
                    bitmap = bmp
                    invalidate()
                }
            }
        }
    }

    fun set(bmp: Bitmap?) {
        requestKey = null
        bitmap = bmp
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val b = bitmap ?: return
        val s = minOf(width, height)
        val l = (width - s) / 2f
        val t = (height - s) / 2f
        dst.set(l, t, l + s, t + s)
        canvas.drawBitmap(b, null, dst, paint)
    }

    companion object {
        private val main = Handler(Looper.getMainLooper())
    }
}
