package io.github.tkdev106.launcher

import android.content.Context
import io.github.tkdev106.launcher.core.ConfigCodec
import io.github.tkdev106.launcher.core.DefaultConfig
import io.github.tkdev106.launcher.core.InstalledApp
import io.github.tkdev106.launcher.core.LauncherConfig
import java.io.File

/** 設定ファイル（filesDir/launcher.json）の読み書き。無いか壊れていれば初期値から作り直す */
class ConfigStore(private val context: Context) {
    private val file = File(context.filesDir, "launcher.json")

    /** 書き換えのたびに増える。ホームはこれを見て描き直すかを決める */
    var version = 0
        private set

    private var cached: LauncherConfig? = null

    fun load(installed: List<InstalledApp>): LauncherConfig {
        cached?.let { return it }
        val loaded = runCatching { ConfigCodec.decode(file.readText()) }.getOrNull()
        val config = loaded ?: defaults(installed).also { write(it) }
        cached = config
        return config
    }

    fun defaults(installed: List<InstalledApp>): LauncherConfig {
        val nova = context.assets.open("drawer-folders.json").bufferedReader().use { it.readText() }
        return DefaultConfig.create(ConfigCodec.decodeNovaFolders(nova), installed)
    }

    fun save(config: LauncherConfig) {
        cached = config
        version++
        write(config)
    }

    private fun write(config: LauncherConfig) {
        // 途中で落ちても壊れたファイルが残らないよう、別名に書いてから置き換える
        val tmp = File(file.parentFile, "launcher.json.tmp")
        tmp.writeText(ConfigCodec.encode(config))
        tmp.renameTo(file)
    }

    fun iconFile(name: String): File = File(File(context.filesDir, "icons").also { it.mkdirs() }, name)
}
