package io.github.tkdev106.launcher

import android.content.Context
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * ホームと編集モードで共有するもの。Application を足さず、最初に触った画面が作る。
 * ランチャーは常に同じプロセスで動き続けるので、使い回したほうが速い。
 */
object Launcher {
    private var catalog: AppCatalog? = null
    private var store: ConfigStore? = null
    private var icons: IconFactory? = null

    /** アイコンの描画・天気の取得など、画面を止めたくない仕事を1本の裏の線で順に回す */
    val background: ExecutorService by lazy { Executors.newSingleThreadExecutor() }

    fun catalog(c: Context): AppCatalog = catalog ?: AppCatalog(c.applicationContext).also { catalog = it }
    fun store(c: Context): ConfigStore = store ?: ConfigStore(c.applicationContext).also { store = it }
    fun icons(c: Context): IconFactory = icons ?: IconFactory(c.applicationContext).also { icons = it }
}
