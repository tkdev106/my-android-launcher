package io.github.tkdev106.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerTest {
    private val order = Drawer.labelOrder()

    private fun app(pkg: String, label: String, cls: String = "$pkg.Main") = InstalledApp(AppKey(pkg, cls), label)

    private val line = app("jp.naver.line.android", "LINE")
    private val maps = app("com.google.android.apps.maps", "Maps")
    private val chrome = app("com.android.chrome", "Chrome")
    private val mercari = app("com.kouzoh.mercari", "メルカリ")
    private val installed = listOf(mercari, maps, line, chrome)

    private fun names(entries: List<DrawerEntry>) = entries.map {
        when (it) {
            is DrawerEntry.Folder -> "[${it.name}]"
            is DrawerEntry.App -> it.app.label
        }
    }

    @Test
    fun `フォルダを先頭に並べ、その後にアプリを名前順に並べる`() {
        val folders = listOf(Folder("Travel", listOf(AppKey("com.google.android.apps.maps"))))
        assertEquals(listOf("[Travel]", "Chrome", "LINE", "メルカリ"), names(Drawer.entries(installed, folders, order)))
    }

    @Test
    fun `フォルダに入れたアプリは一覧本体に出ない`() {
        val folders = listOf(Folder("Social", listOf(AppKey("jp.naver.line.android", "jp.naver.line.android.activity.SplashActivity"))))
        val entries = Drawer.entries(installed, folders, order)
        assertTrue(entries.none { it is DrawerEntry.App && it.app == line })
        assertEquals(listOf(line), (entries[0] as DrawerEntry.Folder).apps)
    }

    @Test
    fun `activity 名が古くてもパッケージ名が同じならフォルダに入る`() {
        val folders = listOf(Folder("Social", listOf(AppKey.parse("jp.naver.line.android/.activity.OldActivity"))))
        assertEquals(listOf(line), (Drawer.entries(installed, folders, order)[0] as DrawerEntry.Folder).apps)
    }

    @Test
    fun `入っているアプリが1つも無いフォルダは出さない`() {
        val folders = listOf(Folder("Games", listOf(AppKey("jp.pokemon.pokemonsleep"))))
        assertEquals(listOf("Chrome", "LINE", "Maps", "メルカリ"), names(Drawer.entries(installed, folders, order)))
    }

    @Test
    fun `英字の名前はかなの名前より前に並ぶ`() {
        val entries = Drawer.entries(listOf(app("a.b", "あいう"), app("c.d", "Zoom")), emptyList(), order)
        assertEquals(listOf("Zoom", "あいう"), names(entries))
    }

    @Test
    fun `42個ごとに1ページに分ける`() {
        val pages = Drawer.pages((1..85).toList())
        assertEquals(listOf(42, 42, 1), pages.map { it.size })
    }

    @Test
    fun `空の一覧でも1ページ返す`() {
        assertEquals(listOf(emptyList<Int>()), Drawer.pages(emptyList<Int>()))
    }

    @Test
    fun `検索は名前の部分一致で、フォルダの中のアプリも出す`() {
        assertEquals(listOf(maps), Drawer.search("ap", installed, order))
    }

    @Test
    fun `検索は先頭が一致するものを前に出す`() {
        val a = app("x.a", "Google Maps")
        val b = app("x.b", "Maps.me")
        assertEquals(listOf(b, a), Drawer.search("maps", listOf(a, b), order))
    }

    @Test
    fun `検索は大文字小文字・全角半角・ひらがなとカタカナを区別しない`() {
        assertEquals(listOf(line), Drawer.search("ｌｉｎｅ", installed, order))
        assertEquals(listOf(mercari), Drawer.search("めるかり", installed, order))
    }

    @Test
    fun `空の語では何も出さない`() {
        assertEquals(emptyList<InstalledApp>(), Drawer.search("  ", installed, order))
    }
}
