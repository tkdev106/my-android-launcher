package io.github.tkdev106.launcher

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.DocumentsContract
import io.github.tkdev106.launcher.core.WallpaperRotation
import java.io.File
import java.util.concurrent.Executors

/**
 * 壁紙を1時間ごとに、選んだフォルダの画像に替える（docs/spec.md「壁紙」）。ホームとロック画面の両方に入れる。
 * いつ・どれに替えたかは設定の JSON に入れない。毎時書き換わる記録で、設定のファイルを書き直したくないため。
 */
class WallpaperRotator(private val context: Context) {
    private val state = File(context.filesDir, "wallpaper.txt")

    /** 裏の線で呼ぶ。替える時刻でなければ何もしない */
    fun rotateIfDue(folder: String?, nowMillis: Long = System.currentTimeMillis()) {
        if (folder == null) return
        val (last, current) = readState()
        if (!WallpaperRotation.isDue(last, nowMillis)) return
        val images = runCatching { listImages(Uri.parse(folder)) }.getOrDefault(emptyMap())
        // 画像が無い（フォルダが空・読めない）ときも記録して、毎分フォルダを読みに行かないようにする
        val name = WallpaperRotation.pick(images.keys.toList(), current) ?: return writeState(nowMillis, current.orEmpty())
        // 読めない画像に当たったときも記録して、毎分重い読み込みをやり直さないようにする
        val ok = runCatching { apply(images.getValue(name)) }.isSuccess
        writeState(nowMillis, if (ok) name else current.orEmpty())
    }

    /** アイコンや天気と同じ線に積むと、大きい写真を読む間ホームの表示が遅れるので、壁紙だけの線を使う */
    fun rotateInBackground(folder: String?) {
        if (folder == null) return
        worker.execute { rotateIfDue(folder) }
    }

    /** フォルダを選び直したら、次にホームを開いたときにすぐ替わるようにする */
    fun reset() {
        state.delete()
    }

    private fun listImages(tree: Uri): Map<String, Uri> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
        val found = LinkedHashMap<String, Uri>()
        context.contentResolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(2)?.startsWith("image/") != true) continue
                found[c.getString(1)] = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
            }
        }
        return found
    }

    private fun apply(uri: Uri) {
        val metrics = context.resources.displayMetrics
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        // 画面より大きい写真をそのまま読むとメモリを食うので、画面を覆える範囲で縮めて読む
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= metrics.widthPixels && bounds.outHeight / (sample * 2) >= metrics.heightPixels) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("画像を読めない")
        // 縦に撮った写真は向きを EXIF にだけ持っていて、そのまま読むと横倒しになる
        val orientation = context.contentResolver.openInputStream(uri)!!.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        val upright = if (degrees == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
        WallpaperManager.getInstance(context).setBitmap(upright, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
        if (upright !== bmp) upright.recycle()
        bmp.recycle()
    }

    private fun readState(): Pair<Long?, String?> {
        val lines = runCatching { state.readLines() }.getOrDefault(emptyList())
        return lines.getOrNull(0)?.toLongOrNull() to lines.getOrNull(1)
    }

    private fun writeState(millis: Long, name: String) {
        runCatching { state.writeText("$millis\n$name\n") }
    }

    companion object {
        private val worker by lazy { Executors.newSingleThreadExecutor() }
    }
}
