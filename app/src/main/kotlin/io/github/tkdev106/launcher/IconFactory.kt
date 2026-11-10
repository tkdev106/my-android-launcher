package io.github.tkdev106.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import io.github.tkdev106.launcher.Ui.dp
import io.github.tkdev106.launcher.core.AppKey
import io.github.tkdev106.launcher.core.IconRef

/**
 * アイコンを丸に揃えて描く（docs/spec.md「見た目」）。
 * - 自作の絵柄（builtin）と各アプリの単色版アイコンは、暗い半透明の丸に白で描く
 * - 単色版が無いアプリは、元のアイコンを白黒にして丸に収める
 * - 編集モードで選んだ画像は、丸に切り抜く
 * 描いた結果は大きさごとに覚えておき、ドロワーを開くたびに描き直さない。
 */
class IconFactory(private val context: Context) {
    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun cached(key: String): Bitmap? = cache.get(key)

    fun forget(prefix: String) {
        cache.snapshot().keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }
    }

    fun clear() = cache.evictAll()

    /** 丸の大きさ sizePx、縁を付けるか */
    fun app(key: AppKey, sizePx: Int, border: Boolean = true, fill: Int = Ui.CIRCLE): Bitmap =
        remember("app:${key.flatten()}:$sizePx:$border:$fill") {
            val bmp = base(sizePx, border, fill)
            val info = Launcher.catalog(context).info(key)
            val icon = info?.getIcon(context.resources.displayMetrics.densityDpi)
            if (icon != null) drawAppGlyph(Canvas(bmp), icon, sizePx)
            bmp
        }

    /** フォルダの丸。中に入っているアプリの単色版アイコンを4つまで、2×2 に並べる */
    fun folder(name: String, apps: List<AppKey>, sizePx: Int): Bitmap =
        remember("folder:$name:${apps.joinToString { it.packageName }}:$sizePx") {
            val bmp = base(sizePx, border = true, fill = Ui.CIRCLE)
            val canvas = Canvas(bmp)
            val cell = sizePx * 0.30f
            val gap = sizePx * 0.06f
            val start = (sizePx - cell * 2 - gap) / 2f
            apps.take(4).forEachIndexed { i, key ->
                val icon = Launcher.catalog(context).info(key)?.getIcon(context.resources.displayMetrics.densityDpi) ?: return@forEachIndexed
                val x = start + (i % 2) * (cell + gap)
                val y = start + (i / 2) * (cell + gap)
                canvas.save()
                canvas.translate(x, y)
                drawAppGlyph(canvas, icon, cell.toInt(), circleClip = true)
                canvas.restore()
            }
            bmp
        }

    /** 自作の絵柄か選んだ画像。icon が null なら null を返し、呼び出し側がアプリのアイコンで描く */
    fun custom(icon: String?, sizePx: Int, fill: Int = Ui.CIRCLE, border: Boolean = false): Bitmap? {
        val ref = IconRef.parse(icon) ?: return null
        return remember("custom:$icon:$sizePx:$fill:$border") {
            when (ref) {
                is IconRef.Builtin -> glyph(builtinRes(ref.name), sizePx, fill, border)
                is IconRef.File -> fileIcon(ref.name, sizePx) ?: glyph(R.drawable.ic_drawer, sizePx, fill, border)
            }
        }
    }

    /** 線の絵柄を丸の中に白で描く。絵柄は丸の直径の 46% の大きさ（H 案の 26/56） */
    fun glyph(res: Int, sizePx: Int, fill: Int = Ui.CIRCLE, border: Boolean = false, scale: Float = 0.46f): Bitmap {
        val bmp = base(sizePx, border, fill)
        val d = context.getDrawable(res)!!.mutate()
        val g = (sizePx * scale).toInt()
        val off = (sizePx - g) / 2
        d.setBounds(off, off, off + g, off + g)
        d.setTint(Ui.TEXT)
        d.draw(Canvas(bmp))
        return bmp
    }

    private fun builtinRes(name: String): Int = when (name) {
        "line" -> R.drawable.ic_line
        "x" -> R.drawable.ic_x
        "chrome" -> R.drawable.ic_chrome
        "youtube" -> R.drawable.ic_youtube
        "payments" -> R.drawable.ic_payments
        "ai" -> R.drawable.ic_ai
        "drawer" -> R.drawable.ic_drawer
        else -> R.drawable.ic_drawer
    }

    private fun fileIcon(name: String, sizePx: Int): Bitmap? {
        val src = BitmapFactory.decodeFile(Launcher.store(context).iconFile(name).path) ?: return null
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val scale = sizePx.toFloat() / minOf(src.width, src.height)
        val m = Matrix().apply {
            setScale(scale, scale)
            postTranslate((sizePx - src.width * scale) / 2f, (sizePx - src.height * scale) / 2f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also { it.setLocalMatrix(m) }
        }
        Canvas(out).drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)
        return out
    }

    private fun base(sizePx: Int, border: Boolean, fill: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = sizePx / 2f
        c.drawCircle(r, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
        if (border) {
            val w = context.dp(1f).coerceAtLeast(1).toFloat()
            c.drawCircle(r, r, r - w / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = w
                color = Ui.BORDER
            })
        }
        return bmp
    }

    /**
     * 単色版（themed icon）があれば白で、無ければ元の絵を白黒にして描く。
     * 適応アイコンの層は見える範囲の 1.5 倍の大きさで作られているので、丸に合わせて外側を広げて描く。
     */
    private fun drawAppGlyph(canvas: Canvas, icon: Drawable, sizePx: Int, circleClip: Boolean = true) {
        canvas.save()
        if (circleClip) canvas.clipPath(Path().apply { addCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, Path.Direction.CW) })
        val mono = (icon as? AdaptiveIconDrawable)?.monochrome
        if (mono != null) {
            val d = mono.mutate()
            val ext = sizePx / 4
            d.setBounds(-ext, -ext, sizePx + ext, sizePx + ext)
            d.colorFilter = PorterDuffColorFilter(Ui.TEXT, PorterDuff.Mode.SRC_IN)
            d.draw(canvas)
        } else {
            val d = icon.mutate()
            d.setBounds(0, 0, sizePx, sizePx)
            d.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            d.draw(canvas)
        }
        canvas.restore()
    }

    private inline fun remember(key: String, make: () -> Bitmap): Bitmap = cache.get(key) ?: make().also { cache.put(key, it) }
}
