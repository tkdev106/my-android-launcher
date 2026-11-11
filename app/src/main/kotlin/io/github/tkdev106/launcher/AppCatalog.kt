package io.github.tkdev106.launcher

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.view.View
import android.widget.Toast
import io.github.tkdev106.launcher.core.AppKey
import io.github.tkdev106.launcher.core.InstalledApp
import io.github.tkdev106.launcher.core.resolve

/** 起動できるアプリの一覧。アプリの出し入れを LauncherApps のコールバックで知り、そのときだけ取り直す */
class AppCatalog(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val listeners = mutableListOf<() -> Unit>()
    private var infos: Map<AppKey, LauncherActivityInfo> = emptyMap()

    var apps: List<InstalledApp> = emptyList()
        private set

    init {
        reload()
        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) = changed()
            override fun onPackageAdded(packageName: String, user: UserHandle) = changed()
            override fun onPackageChanged(packageName: String, user: UserHandle) = changed()
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed()
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed()
        }, Handler(Looper.getMainLooper()))
    }

    fun addListener(l: () -> Unit) {
        listeners += l
    }

    private fun changed() {
        reload()
        listeners.forEach { it() }
    }

    private fun reload() {
        // 仕事用プロファイル等は使っていないので、自分のユーザーのアプリだけを並べる
        val list = launcherApps.getActivityList(null, Process.myUserHandle())
            .filter { it.componentName.packageName != context.packageName }
        infos = list.associateBy { AppKey(it.componentName.packageName, it.componentName.className) }
        apps = list.map { InstalledApp(AppKey(it.componentName.packageName, it.componentName.className), it.label.toString()) }
    }

    fun info(key: AppKey): LauncherActivityInfo? = key.resolve(apps)?.let { infos[it.key] }

    fun label(key: AppKey): String = key.resolve(apps)?.label ?: key.packageName

    fun launch(key: AppKey, from: View?) {
        val info = info(key)
        if (info == null) {
            Toast.makeText(context, "${key.packageName} が見つかりません", Toast.LENGTH_SHORT).show()
            return
        }
        val bounds = from?.let { v ->
            val loc = IntArray(2).also { v.getLocationOnScreen(it) }
            Rect(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height)
        }
        val options = from?.let { ActivityOptions.makeScaleUpAnimation(it, 0, 0, it.width, it.height).toBundle() }
        launcherApps.startMainActivity(ComponentName(info.componentName.packageName, info.componentName.className), info.user, bounds, options)
    }

    /** アプリ情報の画面（アンインストールもここから）。メニューを挟まず直接開く。項目が1つだけなら選ばせる意味が無いため */
    fun openDetails(key: AppKey, from: View?) {
        val info = info(key) ?: return
        val options = from?.let { ActivityOptions.makeScaleUpAnimation(it, 0, 0, it.width, it.height).toBundle() }
        launcherApps.startAppDetailsActivity(ComponentName(info.componentName.packageName, info.componentName.className), info.user, null, options)
    }
}
