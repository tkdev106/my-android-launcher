package io.github.tkdev106.launcher

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Space
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import io.github.tkdev106.launcher.Ui.dp
import io.github.tkdev106.launcher.core.Folder
import io.github.tkdev106.launcher.core.Gesture
import io.github.tkdev106.launcher.core.GestureClassifier
import io.github.tkdev106.launcher.core.HomeAction
import io.github.tkdev106.launcher.core.LauncherConfig
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * ホーム画面（docs/spec.md「ホーム画面」）。配置は固定で、上から 時計・天気・予定・余白・再生中の曲・アプリ4つ・ドック。
 * アプリ一覧とフォルダの窓は、別の画面にせずこの上に重ねて開く（開く速さのため）。
 */
class HomeActivity : Activity() {
    private val catalog by lazy { Launcher.catalog(this) }
    private val store by lazy { Launcher.store(this) }
    private val icons by lazy { Launcher.icons(this) }
    private val weatherSource by lazy { WeatherSource(this) }
    private val calendar by lazy { CalendarSource(this) }

    private lateinit var root: GestureLayer
    private lateinit var clock: ClockPanel
    private lateinit var weather: WeatherPanel
    private lateinit var agenda: AgendaPanel
    private lateinit var nowPlaying: NowPlayingPanel
    private lateinit var nowPlayingSource: NowPlayingSource
    private lateinit var appsRow: LinearLayout
    private lateinit var dock: LinearLayout
    private lateinit var drawer: DrawerView
    private lateinit var folder: FolderWindow

    private var shownVersion = -1
    private var config: LauncherConfig? = null
    private var overlayBack: OnBackInvokedCallback? = null

    private var agendaReadAt = 0L

    private val tick = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateClockAndAgenda(force = false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = GestureLayer(this, ::onGesture)

        clock = ClockPanel(this)
        weather = WeatherPanel(this)
        agenda = AgendaPanel(this) { calendar.open(it) }
        nowPlaying = NowPlayingPanel(this, onOpen = { nowPlayingSource.open(it, catalog) }, onToggle = { nowPlayingSource.toggle() })
        nowPlayingSource = NowPlayingSource(this) { nowPlaying.update(it) }
        appsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        dock = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

        val home = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(32), dp(20), dp(16))
            addView(clock)
            addView(weather, lp(top = 24))
            addView(agenda, lp(top = 20))
            addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
            addView(nowPlaying, lp())
            addView(appsRow, lp(top = 22))
            addView(dock, lp(top = 12))
        }
        drawer = DrawerView(this, onLaunch = { app, v -> catalog.launch(app.key, v) }, onOpenFolder = { f, _ -> openFolder(f.name, f.apps.map { it.key }) }, onClose = ::closeDrawer)
        drawer.visibility = View.GONE
        folder = FolderWindow(this) { key, v -> catalog.launch(key, v) }
        folder.onClosed = ::overlayChanged

        root.addView(home, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(drawer, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(folder, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        // 画面の端まで描く前提で、ステータスバーとナビゲーションバーの分だけ内側に寄せる
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(root)

        // ホームでの「戻る」は何もしない（ランチャー自身が背面に回って黒い画面になるのを避ける）
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {}

        catalog.addListener {
            icons.clear()
            bindConfig(force = true)
        }
        askPermissionsOnce()
    }

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }

    override fun onResume() {
        super.onResume()
        bindConfig(force = false)
        updateClockAndAgenda()
        registerReceiver(tick, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }, RECEIVER_NOT_EXPORTED)
        val today = LocalDateTime.now().toLocalDate()
        weather.update(weatherSource.cached(today))
        weatherSource.refresh(today) { weather.update(it) }
        nowPlayingSource.start()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(tick)
        nowPlayingSource.stop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        folder.close()
        closeDrawer()
    }

    /** カレンダーは毎分は読まず、15分ごとと画面に戻ったときだけ読む（毎分の読み出しは軽さに響くため） */
    private fun updateClockAndAgenda(force: Boolean = true) {
        val now = LocalDateTime.now()
        clock.update(now)
        val t = System.currentTimeMillis()
        if (force || t - agendaReadAt >= 15 * 60 * 1000L) {
            agendaReadAt = t
            agenda.update(calendar.lines(now, ZoneId.systemDefault()))
        }
    }

    private fun bindConfig(force: Boolean) {
        if (!force && shownVersion == store.version && config != null) return
        val c = store.load(catalog.apps)
        config = c
        shownVersion = store.version
        if (force) icons.clear()
        bindApps(c)
        bindDock(c)
        drawer.bind(catalog.apps, c.drawerFolders)
        drawer.warmUp()
    }

    private fun bindApps(c: LauncherConfig) {
        appsRow.removeAllViews()
        val size = dp(56)
        c.homeApps.forEach { s ->
            val cell = FrameLayout(this)
            cell.addView(circleButton(this, catalog.label(s.app), size, {
                icons.custom(s.icon, size, Ui.CIRCLE) ?: icons.app(s.app, size, border = false)
            }) { catalog.launch(s.app, it) }.apply { layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER) })
            appsRow.addView(cell, LinearLayout.LayoutParams(0, size, 1f))
        }
    }

    private fun bindDock(c: LauncherConfig) {
        dock.removeAllViews()
        val size = dp(52)
        fun slot(view: View?) {
            val cell = FrameLayout(this)
            if (view != null) cell.addView(view, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
            dock.addView(cell, LinearLayout.LayoutParams(0, size, 1f))
        }
        fun folderButton(f: Folder) = circleButton(this, f.name, size, {
            icons.custom(f.icon, size, Ui.CIRCLE_DOCK, border = true) ?: icons.folder(f.name, f.apps, size)
        }) { openFolder(f.name, f.apps) }
        slot(folderButton(c.dockLeft))
        slot(null)
        slot(circleButton(this, "アプリ一覧", size, {
            icons.custom(c.drawerButtonIcon, size, Ui.CIRCLE_DOCK) ?: icons.glyph(R.drawable.ic_drawer, size, Ui.CIRCLE_DOCK, scale = 0.38f)
        }) { openDrawer() })
        slot(null)
        slot(folderButton(c.dockRight))
    }

    private fun onGesture(g: Gesture) {
        if (drawer.visibility == View.VISIBLE || folder.isOpen) return
        when (GestureClassifier.actionFor(g)) {
            HomeAction.OPEN_DRAWER -> openDrawer()
            HomeAction.OPEN_NOTIFICATIONS -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            HomeAction.OPEN_RECENTS -> global(AccessibilityService.GLOBAL_ACTION_RECENTS)
            HomeAction.OPEN_QUICK_SETTINGS -> global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            HomeAction.LOCK_SCREEN -> global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            HomeAction.OPEN_EDIT_MODE -> startActivity(Intent(this, EditActivity::class.java))
        }
    }

    private fun global(action: Int) {
        if (GestureAccessibilityService.perform(action)) return
        Toast.makeText(this, "ユーザー補助の「${getString(R.string.accessibility_label)}」をオンにしてください", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openDrawer() {
        if (drawer.visibility == View.VISIBLE) return
        drawer.reset()
        drawer.visibility = View.VISIBLE
        drawer.alpha = 0f
        drawer.translationY = dp(40).toFloat()
        drawer.animate().alpha(1f).translationY(0f).setDuration(180).start()
        root.active = false
        overlayChanged()
    }

    private fun closeDrawer() {
        if (drawer.visibility != View.VISIBLE) return
        drawer.hideKeyboard()
        drawer.animate().alpha(0f).translationY(dp(40).toFloat()).setDuration(150).withEndAction {
            drawer.visibility = View.GONE
            overlayChanged()
        }.start()
        root.active = true
    }

    private fun openFolder(name: String, apps: List<io.github.tkdev106.launcher.core.AppKey>) {
        folder.open(name, apps)
        root.active = false
        overlayChanged()
    }

    private fun overlayChanged() {
        val open = drawer.visibility == View.VISIBLE || folder.visibility == View.VISIBLE
        if (open && overlayBack == null) {
            val cb = OnBackInvokedCallback {
                if (folder.isOpen) folder.close() else closeDrawer()
            }
            overlayBack = cb
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, cb)
        } else if (!open) {
            overlayBack?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
            overlayBack = null
            root.active = true
        }
    }

    /** カレンダーと位置の許可は初回に1度だけ聞く。断られたらその部品は空のまま（編集モードから聞き直せる） */
    private fun askPermissionsOnce() {
        val prefs = getSharedPreferences("home", MODE_PRIVATE)
        if (prefs.getBoolean("askedPermissions", false)) return
        prefs.edit().putBoolean("askedPermissions", true).apply()
        val wanted = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.ACCESS_COARSE_LOCATION)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) requestPermissions(wanted.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateClockAndAgenda()
    }
}
