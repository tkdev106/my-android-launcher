package io.github.tkdev106.launcher.core

/** 初回起動の設定。docs/spec.md の「ホーム画面」「ドック」をそのまま写したもの */
object DefaultConfig {

    /**
     * installed は、パッケージ名が分からないアプリ（Muse）を名前で探すために使う。
     * 見つからなければそのアプリはフォルダに入れない（後から編集モードで足せる）。
     */
    fun create(novaFolders: List<Folder>, installed: List<InstalledApp>): LauncherConfig {
        val muse = installed.firstOrNull { it.label.trim().equals("Muse", ignoreCase = true) }?.key
        return LauncherConfig(
            homeApps = listOf(
                Shortcut(AppKey("jp.naver.line.android"), IconRef.builtin("line")),
                Shortcut(AppKey("com.twitter.android"), IconRef.builtin("x")),
                Shortcut(AppKey("com.android.chrome"), IconRef.builtin("chrome")),
                Shortcut(AppKey("app.revanced.android.youtube"), IconRef.builtin("youtube")),
            ),
            dockLeft = Folder(
                "決済",
                listOf(
                    AppKey("jp.ne.paypay.android.app"),
                    AppKey("jp.co.rakuten.pay"),
                    AppKey("com.google.android.apps.walletnfcrel"),
                ),
                IconRef.builtin("payments"),
            ),
            dockRight = Folder("AI", listOfNotNull(AppKey("com.anthropic.claude"), muse), IconRef.builtin("ai")),
            drawerButtonIcon = IconRef.builtin("drawer"),
            // 一覧の先頭はフォルダ名の順（spec の並び）
            drawerFolders = novaFolders.sortedBy { it.name },
        )
    }
}
