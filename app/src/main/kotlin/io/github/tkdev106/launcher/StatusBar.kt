package io.github.tkdev106.launcher

import android.app.StatusBarManager
import android.content.Context

/**
 * 通知とクイック設定をユーザー補助を使わずに開く。ユーザー補助をオンにすると PayPay などの決済アプリが動かなくなるため。
 * 開く操作は公開の API に無いので StatusBarManager の非公開の関数を呼ぶ（ホームアプリが昔から使っている方法で、権限は EXPAND_STATUS_BAR だけ）。
 * 呼べなかったら false を返し、呼び出し側がユーザー補助に任せる。
 */
object StatusBar {
    fun openNotifications(c: Context): Boolean = call(c, "expandNotificationsPanel")

    fun openQuickSettings(c: Context): Boolean = call(c, "expandSettingsPanel")

    private fun call(c: Context, method: String): Boolean = runCatching {
        val sbm = c.getSystemService(StatusBarManager::class.java)
        sbm.javaClass.getMethod(method).invoke(sbm)
        true
    }.getOrDefault(false)
}
