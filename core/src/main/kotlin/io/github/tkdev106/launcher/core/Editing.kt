package io.github.tkdev106.launcher.core

/** 編集モードでの操作。どれも新しい設定を返し、元の設定は変えない */
object Editing {

    /** アプリを入れ替えると、前のアプリ用に選んだ絵柄は外す（別のアプリに前の絵柄が残らないように） */
    fun setHomeApp(config: LauncherConfig, index: Int, app: AppKey): LauncherConfig {
        require(index in 0 until LauncherConfig.HOME_APPS)
        val home = config.homeApps.toMutableList()
        home[index] = Shortcut(app, null)
        return config.copy(homeApps = home)
    }

    fun setDockFolderApps(config: LauncherConfig, left: Boolean, apps: List<AppKey>): LauncherConfig {
        val distinct = apps.distinctBy { it.packageName }
        return if (left) config.copy(dockLeft = config.dockLeft.copy(apps = distinct))
        else config.copy(dockRight = config.dockRight.copy(apps = distinct))
    }

    fun setIcon(config: LauncherConfig, slot: IconSlot, icon: String?): LauncherConfig = when (slot) {
        is IconSlot.HomeApp -> config.copy(homeApps = config.homeApps.mapIndexed { i, s -> if (i == slot.index) s.copy(icon = icon) else s })
        IconSlot.DockLeft -> config.copy(dockLeft = config.dockLeft.copy(icon = icon))
        IconSlot.DockRight -> config.copy(dockRight = config.dockRight.copy(icon = icon))
        IconSlot.DrawerButton -> config.copy(drawerButtonIcon = icon)
    }

    // --- ドロワーのフォルダ ---

    fun addFolder(config: LauncherConfig, name: String): LauncherConfig {
        val n = name.trim()
        if (n.isEmpty() || config.drawerFolders.any { it.name == n }) return config
        return config.copy(drawerFolders = (config.drawerFolders + Folder(n, emptyList())).sortedBy { it.name })
    }

    fun renameFolder(config: LauncherConfig, from: String, to: String): LauncherConfig {
        val n = to.trim()
        if (n.isEmpty() || config.drawerFolders.any { it.name == n }) return config
        return config.copy(drawerFolders = config.drawerFolders.map { if (it.name == from) it.copy(name = n) else it }.sortedBy { it.name })
    }

    fun removeFolder(config: LauncherConfig, name: String): LauncherConfig =
        config.copy(drawerFolders = config.drawerFolders.filterNot { it.name == name })

    /** アプリは1つのフォルダにしか入らない。別のフォルダに入っていればそこから抜く */
    fun addToFolder(config: LauncherConfig, name: String, app: AppKey): LauncherConfig {
        if (config.drawerFolders.none { it.name == name }) return config
        val removed = removeFromFolders(config, app)
        return removed.copy(drawerFolders = removed.drawerFolders.map { if (it.name == name) it.copy(apps = it.apps + app) else it })
    }

    fun removeFromFolders(config: LauncherConfig, app: AppKey): LauncherConfig =
        config.copy(drawerFolders = config.drawerFolders.map { f -> f.copy(apps = f.apps.filterNot { it.packageName == app.packageName }) })
}
