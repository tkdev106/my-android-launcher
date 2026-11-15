package io.github.tkdev106.launcher

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * 最近使ったアプリを開くだけに使うユーザー補助（docs/spec.md「作り方」で常駐を認めた1つ目）。通知とクイック設定は StatusBar、画面を消すのは ScreenOff で行う。画面を消すのは、システム設定の変更の許可が無いときだけこちらに任せる。
 * イベントは受け取らず、ホームから呼ばれたときに performGlobalAction するだけ。
 */
class GestureAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        // サービスは同じプロセスにあり、ホームとの受け渡しはこれだけで足りる（Binder を足すほどではない）
        @Suppress("StaticFieldLeak")
        private var instance: GestureAccessibilityService? = null

        fun perform(action: Int): Boolean = instance?.performGlobalAction(action) ?: false
    }
}
