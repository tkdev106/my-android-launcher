package io.github.tkdev106.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigTest {
    @Test
    fun `初期値のドロワーにはフォルダが無い`() {
        assertEquals(emptyList<Folder>(), DefaultConfig.create(emptyList()).drawerFolders)
    }

    @Test
    fun `前の版の設定は読めないとして作り直す`() {
        val old = ConfigCodec.encode(DefaultConfig.create(emptyList())).replace("\"version\": 3", "\"version\": 2")
        assertThrows(JsonException::class.java) { ConfigCodec.decode(old) }
    }

    @Test
    fun `初期値のホームは LINE・X・Chrome・YouTube`() {
        val config = DefaultConfig.create(emptyList())
        assertEquals(
            listOf("jp.naver.line.android", "com.twitter.android", "com.android.chrome", "app.revanced.android.youtube"),
            config.homeApps.map { it.app.packageName },
        )
    }

    @Test
    fun `Muse は名前で探して AI フォルダに入れる`() {
        val muse = InstalledApp(AppKey("com.example.muse", "com.example.muse.Main"), "Muse")
        val config = DefaultConfig.create(listOf(muse))
        assertEquals(listOf("com.anthropic.claude", "com.example.muse"), config.dockRight.apps.map { it.packageName })
    }

    @Test
    fun `Muse が入っていなければ AI フォルダは Claude だけ`() {
        val config = DefaultConfig.create(emptyList())
        assertEquals(listOf("com.anthropic.claude"), config.dockRight.apps.map { it.packageName })
    }

    @Test
    fun `保存して読み戻すと同じ設定になる`() {
        val config = Editing.setIcon(DefaultConfig.create(emptyList()), IconSlot.HomeApp(1), IconRef.file("x-1.png"))
        assertEquals(config, ConfigCodec.decode(ConfigCodec.encode(config)))
    }

    @Test
    fun `壊れた設定は読めないとして例外になる`() {
        assertThrows(JsonException::class.java) { ConfigCodec.decode("{\"version\": 2, \"homeApps\": [") }
        assertThrows(JsonException::class.java) { ConfigCodec.decode("{\"version\": 1}") }
    }

    @Test
    fun `アプリ参照の短縮形はパッケージ名を補って読む`() {
        assertEquals(AppKey("a.b", "a.b.Main"), AppKey.parse("a.b/.Main"))
        assertEquals(AppKey("a.b", "c.d.Main"), AppKey.parse("a.b/c.d.Main"))
        assertEquals(AppKey("a.b"), AppKey.parse("a.b"))
    }

    @Test
    fun `参照の activity が無ければ同じパッケージの先頭のアプリに当たる`() {
        val installed = listOf(InstalledApp(AppKey("a.b", "a.b.Main"), "AB"))
        assertEquals(installed[0], AppKey("a.b", "a.b.Gone").resolve(installed))
        assertEquals(null, AppKey("x.y").resolve(installed))
    }

    @Test
    fun `JSON の文字列のエスケープを読み書きできる`() {
        val text = Json.obj("s" to Json.Str("改行\nと\"引用\"と\\")).stringify()
        assertEquals("改行\nと\"引用\"と\\", Json.parse(text)["s"].string)
        assertTrue(Json.parse("{\"u\": \"\\u3042\"}")["u"].string == "あ")
    }
}
