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
 * - 決済・AI のフォルダに入れるアプリは、パッケージ名で自作の絵柄に置き換える
 * - 単色版が無いアプリは、元のアイコンから地の色と違う部分（ロゴ）だけを抜き出して白で描く
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
            val own = ownGlyph(key)
            if (own != null) return@remember glyph(own, sizePx, fill, border)
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
                val own = ownGlyph(key)
                if (own != null) {
                    val d = context.getDrawable(own)!!.mutate()
                    d.setBounds(0, 0, cell.toInt(), cell.toInt())
                    d.setTint(Ui.TEXT)
                    d.draw(canvas)
                } else {
                    val icon = Launcher.catalog(context).info(key)?.getIcon(context.resources.displayMetrics.densityDpi)
                    if (icon != null) drawAppGlyph(canvas, icon, cell.toInt(), circleClip = true)
                }
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
     * 単色版（themed icon）があれば白で描く。適応アイコンの層は見える範囲の 1.5 倍の大きさで作られているので、丸に合わせて外側を広げて描く。
     * 単色版が無ければ、元のアイコンを描いてから地の明るさと違う所だけを白く残す。
     * 白黒にしただけだと白い丸や灰色の板になり、ほかの白い絵柄のアイコンと揃わないため。
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
            canvas.drawBitmap(logoMask(icon, sizePx), 0f, 0f, null)
        }
        canvas.restore()
    }

    /** アイコンのロゴだけを白で抜き出す。地の明るさは、アイコンの内側の輪の上の値の中央値とする */
    private fun logoMask(icon: Drawable, sizePx: Int): Bitmap {
        val src = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val d = icon.mutate()
        if (d is AdaptiveIconDrawable) {
            val ext = sizePx / 4
            d.setBounds(-ext, -ext, sizePx + ext, sizePx + ext)
        } else {
            d.setBounds(0, 0, sizePx, sizePx)
        }
        d.draw(Canvas(src))
        val px = IntArray(sizePx * sizePx)
        src.getPixels(px, 0, sizePx, 0, 0, sizePx, sizePx)
        fun lum(c: Int) = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f

        val c = sizePx / 2f
        val ring = (0 until 48).map { i ->
            val a = Math.PI * 2 * i / 48
            val x = (c + Math.cos(a) * sizePx * 0.40).toInt().coerceIn(0, sizePx - 1)
            val y = (c + Math.sin(a) * sizePx * 0.40).toInt().coerceIn(0, sizePx - 1)
            px[y * sizePx + x]
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
        src.setPixels(px, 0, sizePx, 0, 0, sizePx, sizePx)
        return src
    }

    /** Muse はパッケージ名が分からないので、名前で当てる（docs/spec.md「ドック」） */
    private fun ownGlyph(key: AppKey): Int? = APP_GLYPHS[key.packageName]
        ?: R.drawable.ic_app_muse.takeIf { Launcher.catalog(context).label(key).trim().equals("Muse", ignoreCase = true) }

    companion object {
        /** 自作の絵柄に置き換えるアプリ（ドックの決済・AI のフォルダの中身）。ホームの4つは設定の IconRef で持つ */
        private val APP_GLYPHS = mapOf(
            "jp.ne.paypay.android.app" to R.drawable.ic_app_paypay,
            "jp.co.rakuten.pay" to R.drawable.ic_app_rakuten_pay,
            "com.google.android.apps.walletnfcrel" to R.drawable.ic_app_wallet,
            "com.anthropic.claude" to R.drawable.ic_app_claude,
        )
    }

    private inline fun remember(key: String, make: () -> Bitmap): Bitmap = cache.get(key) ?: make().also { cache.put(key, it) }
}
