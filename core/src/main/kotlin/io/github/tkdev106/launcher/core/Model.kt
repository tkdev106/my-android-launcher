package io.github.tkdev106.launcher.core

/**
 * アプリの参照。className が無いときはそのパッケージの最初のランチャー用 activity を指す。
 * Nova のバックアップの activity 名は古いことがあるので、同一性はパッケージ名で見る。
 */
data class AppKey(val packageName: String, val className: String? = null) {
    fun flatten(): String = if (className == null) packageName else "$packageName/$className"

    companion object {
        /** `pkg`・`pkg/cls`・`pkg/.cls`（Nova や ComponentName の短縮形）を読む */
        fun parse(text: String): AppKey {
            val s = text.trim()
            val slash = s.indexOf('/')
            if (slash < 0) return AppKey(s)
            val pkg = s.substring(0, slash)
            val cls = s.substring(slash + 1)
            return AppKey(pkg, when {
                cls.isEmpty() -> null
                cls.startsWith(".") -> pkg + cls
                else -> cls
            })
        }
    }
}

/** 端末に入っている起動できるアプリ（ランチャー用 activity 1つ）。key は className まで持つ */
data class InstalledApp(val key: AppKey, val label: String)

/** 起動できるアプリの中から参照に当たるものを選ぶ。activity 名が一致しなければ同じパッケージの先頭を使う */
fun AppKey.resolve(installed: List<InstalledApp>): InstalledApp? =
    installed.firstOrNull { it.key.packageName == packageName && className != null && it.key.className == className }
        ?: installed.firstOrNull { it.key.packageName == packageName }

/**
 * アイコンの指定。`builtin:<名前>`（APK に入れた自作の線の絵柄）か `file:<名前>`（編集モードで選んだ画像）。
 * null はそのアプリの単色版アイコンを使う。
 */
sealed interface IconRef {
    data class Builtin(val name: String) : IconRef
    data class File(val name: String) : IconRef

    companion object {
        fun parse(text: String?): IconRef? = when {
            text == null -> null
            text.startsWith("builtin:") -> Builtin(text.removePrefix("builtin:"))
            text.startsWith("file:") -> File(text.removePrefix("file:"))
            else -> null
        }

        fun builtin(name: String) = "builtin:$name"
        fun file(name: String) = "file:$name"
    }
}

/** ホームのアプリ1つ。icon は IconRef の文字列 */
data class Shortcut(val app: AppKey, val icon: String? = null)

data class Folder(val name: String, val apps: List<AppKey>, val icon: String? = null)

data class LauncherConfig(
    /** ホームのアプリ4つ（左から） */
    val homeApps: List<Shortcut>,
    /** ドックの1列目と5列目のフォルダ */
    val dockLeft: Folder,
    val dockRight: Folder,
    val drawerButtonIcon: String?,
    val drawerFolders: List<Folder>,
) {
    companion object {
        const val HOME_APPS = 4
    }
}

/** アイコンを差し替えられる場所 */
sealed interface IconSlot {
    data class HomeApp(val index: Int) : IconSlot
    data object DockLeft : IconSlot
    data object DockRight : IconSlot
    data object DrawerButton : IconSlot
}
