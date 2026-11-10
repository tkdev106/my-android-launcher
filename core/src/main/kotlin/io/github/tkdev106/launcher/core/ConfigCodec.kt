package io.github.tkdev106.launcher.core

/** 設定を1つの JSON ファイルとして読み書きする。項目の意味は docs/spec.md の各節 */
object ConfigCodec {
    private const val VERSION = 2

    fun encode(config: LauncherConfig): String = Json.obj(
        "version" to Json.num(VERSION),
        "homeApps" to Json.arr(config.homeApps.map { Json.obj("app" to Json.Str(it.app.flatten()), "icon" to Json.str(it.icon)) }),
        "dockLeft" to encodeFolder(config.dockLeft),
        "dockRight" to encodeFolder(config.dockRight),
        "drawerButtonIcon" to Json.str(config.drawerButtonIcon),
        "drawerFolders" to Json.arr(config.drawerFolders.map(::encodeFolder)),
    ).stringify()

    /** 壊れていたら JsonException を投げる。呼び出し側は初期値に戻す */
    fun decode(text: String): LauncherConfig {
        val root = Json.parse(text)
        if (root["version"].int != VERSION) throw JsonException("知らない版の設定")
        val home = root["homeApps"].list.map { Shortcut(AppKey.parse(it["app"].required("app")), it["icon"].string) }
        if (home.size != LauncherConfig.HOME_APPS) throw JsonException("ホームのアプリは4つ")
        return LauncherConfig(
            homeApps = home,
            dockLeft = decodeFolder(root["dockLeft"] ?: throw JsonException("dockLeft が無い")),
            dockRight = decodeFolder(root["dockRight"] ?: throw JsonException("dockRight が無い")),
            drawerButtonIcon = root["drawerButtonIcon"].string,
            drawerFolders = root["drawerFolders"].list.map(::decodeFolder),
        )
    }

    /** docs/drawer-folders.json（`{"フォルダ名": [{"title", "component"}]}`）を読む */
    fun decodeNovaFolders(text: String): List<Folder> {
        val root = Json.parse(text) as? Json.Obj ?: throw JsonException("フォルダの一覧がオブジェクトでない")
        return root.fields.map { (name, apps) ->
            Folder(name, apps.list.mapNotNull { it["component"].string?.let(AppKey::parse) })
        }
    }

    private fun encodeFolder(f: Folder) = Json.obj(
        "name" to Json.Str(f.name),
        "apps" to Json.arr(f.apps.map { Json.Str(it.flatten()) }),
        "icon" to Json.str(f.icon),
    )

    private fun decodeFolder(j: Json) = Folder(
        j["name"].required("name"),
        j["apps"].list.map { AppKey.parse(it.required("app")) },
        j["icon"].string,
    )

    private fun Json?.required(name: String): String = string ?: throw JsonException("$name が無い")
}
