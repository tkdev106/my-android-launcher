package io.github.tkdev106.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
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
 * - 単色版が無いアプリは、元のアイコンから地の色と違う部分（ロゴ）だけを抜き出して白で描く
 * - どちらも絵柄の外枠を測り、丸からはみ出すものは縮めて真ん中に置く
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

    fun app(key: AppKey, sizePx: Int, border: Boolean = true, fill: Int = Ui.CIRCLE): Bitmap =
        remember("app:${key.flatten()}:$sizePx:$border:$fill") {
            val bmp = base(sizePx, border, fill)
            val info = Launcher.catalog(context).info(key)
            val icon = info?.getIcon(context.resources.displayMetrics.densityDpi)
            if (icon != null) drawAppGlyph(Canvas(bmp), icon, sizePx)
            bmp
        }

    fun folder(name: String, apps: List<AppKey>, sizePx: Int): Bitmap =
        remember("folder:$name:${apps.joinToString { it.packageName }}:$sizePx") {
            val bmp = base(sizePx, border = true, fill = Ui.CIRCLE)
            val canvas = Canvas(bmp)
            val cell = sizePx * 0.30f
            val gap = sizePx * 0.06f
            val start = (sizePx - cell * 2 - gap) / 2f
            apps.take(4).forEachIndexed { i, key ->
                val x = start + (i % 2) * (cell + gap)
                val y = start + (i / 2) * (cell + gap)
                canvas.save()
                canvas.translate(x, y)
                val icon = Launcher.catalog(context).info(key)?.getIcon(context.resources.displayMetrics.densityDpi)
                if (icon != null) drawAppGlyph(canvas, icon, cell.toInt(), circleClip = true)
                canvas.restore()
            }
            bmp
        }

    /** icon が null なら null。アプリのアイコンでの描き方は呼び出し側で丸の色と縁が違うため、ここでは決めない */
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
     * 単色版（themed icon）があれば白で描く。無ければ、元のアイコンを描いてから地の明るさと違う所だけを白く残す
     * （白黒にしただけだと白い丸や灰色の板になり、ほかの白い絵柄と揃わないため）。
     * 最後に絵柄の外枠を測り、丸に収まる大きさまで縮めて真ん中に置く。ロゴが端まで広がったアイコンが丸で切れないように。
     */
    private fun drawAppGlyph(canvas: Canvas, icon: Drawable, sizePx: Int, circleClip: Boolean = true) {
        val glyph = whiteGlyph(icon, sizePx)
        val box = opaqueBounds(glyph) ?: return
        val bw = box.width().toFloat()
        val bh = box.height().toFloat()
        // 縦横の長い方を直径の 62% まで、対角線を 86% までにすると、丸の内側に余白を残して収まる
        val scale = minOf(1f, sizePx * 0.62f / maxOf(bw, bh), sizePx * 0.86f / Math.hypot(bw.toDouble(), bh.toDouble()).toFloat())
        val w = bw * scale
        val h = bh * scale
        val dst = RectF((sizePx - w) / 2f, (sizePx - h) / 2f, (sizePx + w) / 2f, (sizePx + h) / 2f)
        canvas.save()
        if (circleClip) canvas.clipPath(Path().apply { addCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, Path.Direction.CW) })
        canvas.drawBitmap(glyph, box, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    }

    /**
     * 白い絵柄を、適応アイコンの層の全体（見える範囲の 1.5 倍）の大きさの板に描く。
     * 見える範囲の外まで描いておくのは、はみ出したロゴを縮めて丸に収めるため。
     */
    private fun whiteGlyph(icon: Drawable, sizePx: Int): Bitmap {
        val full = sizePx * 3 / 2
        val out = Bitmap.createBitmap(full, full, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val mono = (icon as? AdaptiveIconDrawable)?.monochrome
        if (mono != null) {
            val d = mono.mutate()
            d.setBounds(0, 0, full, full)
            d.colorFilter = PorterDuffColorFilter(Ui.TEXT, PorterDuff.Mode.SRC_IN)
            d.draw(canvas)
            return out
        }
        val d = icon.mutate()
        if (d is AdaptiveIconDrawable) d.setBounds(0, 0, full, full)
        else d.setBounds((full - sizePx) / 2, (full - sizePx) / 2, (full + sizePx) / 2, (full + sizePx) / 2)
        d.draw(canvas)
        val px = IntArray(full * full)
        out.getPixels(px, 0, full, 0, 0, full, full)
        fun lum(c: Int) = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f

        // 地の明るさは、見える範囲の内側の輪の上の値の中央値とする
        val c = full / 2f
        val ring = (0 until 48).map { i ->
            val a = Math.PI * 2 * i / 48
            val x = (c + Math.cos(a) * sizePx * 0.40).toInt().coerceIn(0, full - 1)
            val y = (c + Math.sin(a) * sizePx * 0.40).toInt().coerceIn(0, full - 1)
            px[y * full + x]
        }
        val opaque = ring.filter { Color.alpha(it) > 128 }
        // 輪の大半が透明なら、アイコン自体がロゴの形をしている。そのときは形をそのまま白にする
        val bg = if (opaque.size < ring.size / 2) null else opaque.map(::lum).sorted()[opaque.size / 2]
        for (i in px.indices) {
            val p = px[i]
            val a = Color.alpha(p) / 255f
            val k = if (bg == null) a else a * ((Math.abs(lum(p) - bg) - 0.08f) * 3f).coerceIn(0f, 1f)
            px[i] = Color.argb((k * 255).toInt(), Color.red(Ui.TEXT), Color.green(Ui.TEXT), Color.blue(Ui.TEXT))
        }
        out.setPixels(px, 0, full, 0, 0, full, full)
        return out
    }

    /** 薄いにじみを拾わないよう、ある程度濃い点だけで外枠を測る。何も描かれていなければ null */
    private fun opaqueBounds(bmp: Bitmap): Rect? {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        var l = w; var t = h; var r = -1; var b = -1
        for (y in 0 until h) for (x in 0 until w) {
            if (Color.alpha(px[y * w + x]) > 60) {
                if (x < l) l = x
                if (x > r) r = x
                if (y < t) t = y
                if (y > b) b = y
            }
        }
        return if (r < 0) null else Rect(l, t, r + 1, b + 1)
    }

    private inline fun remember(key: String, make: () -> Bitmap): Bitmap = cache.get(key) ?: make().also { cache.put(key, it) }
}
