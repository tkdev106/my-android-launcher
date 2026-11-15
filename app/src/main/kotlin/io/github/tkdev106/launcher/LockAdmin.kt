package io.github.tkdev106.launcher

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * ダブルタップで画面を消すための端末管理アプリの権限（画面のロックだけを求める）。
 * ユーザー補助で消すと PayPay が動かなくなるので、こちらを使う。常駐はせず、許可の受け口があるだけ。
 */
class LockAdmin : DeviceAdminReceiver() {
    companion object {
        private fun component(c: Context) = ComponentName(c, LockAdmin::class.java)

        /** 許可されていなければ false */
        fun lock(c: Context): Boolean {
            val dpm = c.getSystemService(DevicePolicyManager::class.java)
            if (!dpm.isAdminActive(component(c))) return false
            dpm.lockNow()
            return true
        }

        fun requestIntent(c: Context): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(c))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "ホームをダブルタップして画面を消すためだけに使います")
    }
}
