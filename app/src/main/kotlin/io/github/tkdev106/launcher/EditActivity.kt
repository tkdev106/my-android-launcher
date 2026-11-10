package io.github.tkdev106.launcher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.tkdev106.launcher.Ui.dp
import io.github.tkdev106.launcher.core.AppKey
import io.github.tkdev106.launcher.core.Drawer
import io.github.tkdev106.launcher.core.Editing
import io.github.tkdev106.launcher.core.IconRef
import io.github.tkdev106.launcher.core.IconSlot
import io.github.tkdev106.launcher.core.InstalledApp
import io.github.tkdev106.launcher.core.LauncherConfig

/**
 * 編集モード（docs/spec.md「編集モード」）。2本指で上へのジェスチャーで開く最小限の設定画面。
 * 変えたらすぐ保存し、ホームは戻ったときに描き直す。
 */
class EditActivity : Activity() {
    private val catalog by lazy { Launcher.catalog(this) }
    private val store by lazy { Launcher.store(this) }
    private lateinit var list: LinearLayout
    private var pendingIconSlot: IconSlot? = null

    private val config: LauncherConfig get() = store.load(catalog.apps)
    private val apps: List<InstalledApp> get() = catalog.apps.sortedWith(compareBy(Drawer.labelOrder()) { it.label })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(32))
        }
        val scroll = ScrollView(this).apply { addView(list) }
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun save(c: LauncherConfig) {
        store.save(c)
        Launcher.icons(this).clear()
        render()
    }

    private fun render() {
        list.removeAllViews()
        val c = config

        header("許可")
        row("ユーザー補助（ジェスチャー）", "設定を開く") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        row("通知へのアクセス（再生中の曲）", "設定を開く") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(this, MediaListenerService::class.java).flattenToString()))
        }
        row("カレンダーと位置情報（予定・天気）", "許可する") {
            requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        }

        header("ホームのアプリ")
        c.homeApps.forEachIndexed { i, s ->
            row("${i + 1}. ${catalog.label(s.app)}", "アプリ", "アイコン", "元に戻す") { which ->
                when (which) {
                    0 -> pickApp { save(Editing.setHomeApp(config, i, it)) }
                    1 -> pickIcon(IconSlot.HomeApp(i))
                    2 -> save(Editing.setIcon(config, IconSlot.HomeApp(i), defaultIcon(IconSlot.HomeApp(i))))
                }
            }
        }

        header("ドック")
        row("${c.dockLeft.name}: ${c.dockLeft.apps.joinToString { catalog.label(it) }}", "中身", "アイコン", "元に戻す") { which ->
            when (which) {
                0 -> pickApps(config.dockLeft.apps) { save(Editing.setDockFolderApps(config, left = true, it)) }
                1 -> pickIcon(IconSlot.DockLeft)
                2 -> save(Editing.setIcon(config, IconSlot.DockLeft, defaultIcon(IconSlot.DockLeft)))
            }
        }
        row("アプリ一覧ボタン", "アイコン", "元に戻す") { which ->
            if (which == 0) pickIcon(IconSlot.DrawerButton) else save(Editing.setIcon(config, IconSlot.DrawerButton, defaultIcon(IconSlot.DrawerButton)))
        }
        row("${c.dockRight.name}: ${c.dockRight.apps.joinToString { catalog.label(it) }}", "中身", "アイコン", "元に戻す") { which ->
            when (which) {
                0 -> pickApps(config.dockRight.apps) { save(Editing.setDockFolderApps(config, left = false, it)) }
                1 -> pickIcon(IconSlot.DockRight)
                2 -> save(Editing.setIcon(config, IconSlot.DockRight, defaultIcon(IconSlot.DockRight)))
            }
        }

        header("ドロワーのフォルダ")
        c.drawerFolders.forEach { f ->
            row("${f.name}（${f.apps.size}）", "中身", "名前", "削除") { which ->
                when (which) {
                    0 -> pickApps(f.apps) { chosen ->
                        var next = config
                        f.apps.forEach { next = Editing.removeFromFolders(next, it) }
                        chosen.forEach { next = Editing.addToFolder(next, f.name, it) }
                        save(next)
                    }
                    1 -> askText("フォルダの名前", f.name) { save(Editing.renameFolder(config, f.name, it)) }
                    2 -> confirm("「${f.name}」を消しますか。中のアプリは一覧本体に戻ります") { save(Editing.removeFolder(config, f.name)) }
                }
            }
        }
        row("", "フォルダを足す") { askText("新しいフォルダの名前", "") { save(Editing.addFolder(config, it)) } }
    }

    /** 元に戻すと、初期の自作の絵柄（初期と違うアプリに変えた場所はアプリの単色アイコン）にする */
    private fun defaultIcon(slot: IconSlot): String? {
        val d = store.defaults(catalog.apps)
        return when (slot) {
            is IconSlot.HomeApp -> d.homeApps[slot.index].takeIf { it.app.packageName == config.homeApps[slot.index].app.packageName }?.icon
            IconSlot.DockLeft -> d.dockLeft.icon
            IconSlot.DockRight -> d.dockRight.icon
            IconSlot.DrawerButton -> d.drawerButtonIcon
        }
    }

    private fun header(text: String) {
        list.addView(TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(Ui.TEXT_DIM)
            letterSpacing = 0.08f
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(24); bottomMargin = dp(4) })
    }

    private fun row(label: String, vararg actions: String, onAction: (Int) -> Unit) {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
        }
        r.addView(TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(Ui.TEXT)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        actions.forEachIndexed { i, a ->
            r.addView(Button(this, null, android.R.attr.borderlessButtonStyle).apply {
                text = a
                isAllCaps = false
                setOnClickListener { onAction(i) }
            })
        }
        list.addView(r)
    }

    private fun pickApp(onPicked: (AppKey) -> Unit) {
        val all = apps
        AlertDialog.Builder(this)
            .setTitle("アプリを選ぶ")
            .setItems(all.map { it.label }.toTypedArray()) { _, i -> onPicked(all[i].key) }
            .setNegativeButton("やめる", null)
            .show()
    }

    private fun pickApps(current: List<AppKey>, onPicked: (List<AppKey>) -> Unit) {
        val all = apps
        val checked = BooleanArray(all.size) { i -> current.any { it.packageName == all[i].key.packageName } }
        AlertDialog.Builder(this)
            .setTitle("入れるアプリを選ぶ")
            .setMultiChoiceItems(all.map { it.label }.toTypedArray(), checked) { _, i, on -> checked[i] = on }
            .setPositiveButton("決める") { _, _ ->
                // 前からあった順を保ち、新しく選んだものを後ろに足す
                val keep = current.filter { k -> all.indices.any { checked[it] && all[it].key.packageName == k.packageName } }
                val added = all.indices.filter { checked[it] && keep.none { k -> k.packageName == all[it].key.packageName } }.map { all[it].key }
                onPicked(keep + added)
            }
            .setNegativeButton("やめる", null)
            .show()
    }

    private fun askText(title: String, initial: String, onDone: (String) -> Unit) {
        val input = EditText(this).apply { setText(initial); setSingleLine() }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("決める") { _, _ -> onDone(input.text.toString()) }
            .setNegativeButton("やめる", null)
            .show()
    }

    private fun confirm(message: String, onYes: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("消す") { _, _ -> onYes() }
            .setNegativeButton("やめる", null)
            .show()
    }

    private fun pickIcon(slot: IconSlot) {
        pendingIconSlot = slot
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), REQUEST_ICON)
    }

    @Deprecated("Activity#onActivityResult の上書き（AndroidX を入れないため旧 API を使う）")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val slot = pendingIconSlot ?: return
        val uri = data?.data
        if (requestCode != REQUEST_ICON || resultCode != RESULT_OK || uri == null) return
        val name = copyIcon(uri, slot) ?: return
        save(Editing.setIcon(config, slot, IconRef.file(name)))
    }

    /** 選んだ画像は小さくしてアプリ内に写す（元の画像が消えても困らないように） */
    private fun copyIcon(uri: Uri, slot: IconSlot): String? = runCatching {
        val src = contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
        val size = 256
        val scale = size.toFloat() / minOf(src.width, src.height)
        val scaled = Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1), true)
        val tag = when (slot) {
            is IconSlot.HomeApp -> "home-${slot.index}"
            IconSlot.DockLeft -> "dock-left"
            IconSlot.DockRight -> "dock-right"
            IconSlot.DrawerButton -> "drawer"
        }
        val name = "$tag-${System.currentTimeMillis()}.png"
        store.iconFile(name).outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        name
    }.getOrNull()

    companion object {
        private const val REQUEST_ICON = 10
    }
}

