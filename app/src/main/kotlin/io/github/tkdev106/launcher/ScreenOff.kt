package io.github.tkdev106.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import java.io.File

/**
 * ダブルタップで画面を消す（docs/spec.md「ジェスチャー」）。画面のタイムアウトを一時的に最短にし、真っ黒な画面を出して自然に消えるのを待つ。
 * ユーザー補助で消すと PayPay が動かず、端末管理の lockNow は次の解除で指紋が使えなくなるため、どちらも使わない。
 * 元のタイムアウトはファイルに残し、消えたとき・触ったとき・ホームを開いたときに戻す（途中で落ちても戻せるように）。
 */
object ScreenOff {
    /** システムはこれより短いタイムアウトを最短（Pixel では約10秒）に切り上げる */
    private const val SHORTEST_MILLIS = 1000

    private fun saved(c: Context) = File(c.filesDir, "screen_timeout.txt")

    fun permitted(c: Context): Boolean = Settings.System.canWrite(c)

    fun requestIntent(c: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.fromParts("package", c.packageName, null))

    /** 許可が無ければ false */
    fun start(activity: Activity): Boolean {
        if (!permitted(activity)) return false
        val file = saved(activity)
        // 前の消去の途中で落ちて戻せていない値があれば、それが本当の元の値なので上書きしない
        if (!file.exists()) {
            val original = Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30_000)
            file.writeText(original.toString())
        }
        Settings.System.putInt(activity.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, SHORTEST_MILLIS)
        activity.startActivity(Intent(activity, BlackActivity::class.java))
        activity.overridePendingTransition(0, 0)
        return true
    }

    /** 縮めたままのタイムアウトがあれば元に戻す */
    fun restore(c: Context) {
        val file = saved(c)
        val original = runCatching { file.readText().trim().toInt() }.getOrNull() ?: return
        if (permitted(c)) Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, original)
        file.delete()
    }

    /** 画面が自然に消えるまで出しておく真っ黒な画面。触ったら消すのをやめる */
    class BlackActivity : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val v = View(this).apply { setBackgroundColor(0xFF000000.toInt()) }
            setContentView(v)
            window.attributes = window.attributes.apply { screenBrightness = 0f }
            window.insetsController?.hide(WindowInsets.Type.systemBars())
        }

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) finish()
            return true
        }

        override fun onPause() {
            super.onPause()
            // 画面が消えた（または触ってやめた）ので、タイムアウトを戻して閉じる
            restore(this)
            finish()
            overridePendingTransition(0, 0)
        }
    }
}
