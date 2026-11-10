package io.github.tkdev106.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Test

class EditingTest {
    private val base = LauncherConfig(
        homeApps = List(4) { Shortcut(AppKey("p$it"), IconRef.builtin("i$it")) },
        dockLeft = Folder("決済", listOf(AppKey("pay")), IconRef.builtin("payments")),
        dockRight = Folder("AI", listOf(AppKey("ai")), IconRef.builtin("ai")),
        drawerButtonIcon = IconRef.builtin("drawer"),
        drawerFolders = listOf(Folder("Food", listOf(AppKey("food"))), Folder("Social", listOf(AppKey("line")))),
    )

    @Test
    fun `ホームのアプリを入れ替えると、前のアプリの絵柄は外れる`() {
        val c = Editing.setHomeApp(base, 2, AppKey("new"))
        assertEquals(Shortcut(AppKey("new"), null), c.homeApps[2])
        assertEquals(base.homeApps[1], c.homeApps[1])
    }

    @Test
    fun `ドックのフォルダの中身を入れ替える。同じアプリは1つにまとめる`() {
        val c = Editing.setDockFolderApps(base, left = true, listOf(AppKey("a"), AppKey("b"), AppKey("a", "a.X")))
        assertEquals(listOf(AppKey("a"), AppKey("b")), c.dockLeft.apps)
        assertEquals(base.dockRight, c.dockRight)
    }

    @Test
    fun `アイコンを差し替え、null で元に戻す`() {
        val c = Editing.setIcon(base, IconSlot.DockRight, IconRef.file("ai.png"))
        assertEquals("file:ai.png", c.dockRight.icon)
        assertEquals(null, Editing.setIcon(c, IconSlot.DrawerButton, null).drawerButtonIcon)
    }

    @Test
    fun `フォルダを足すと名前順の位置に入る。空や同名は足さない`() {
        val c = Editing.addFolder(base, " Games ")
        assertEquals(listOf("Food", "Games", "Social"), c.drawerFolders.map { it.name })
        assertEquals(c, Editing.addFolder(c, "Food"))
        assertEquals(c, Editing.addFolder(c, " "))
    }

    @Test
    fun `フォルダの名前を変える。既にある名前には変えない`() {
        assertEquals(listOf("Eat", "Social"), Editing.renameFolder(base, "Food", "Eat").drawerFolders.map { it.name })
        assertEquals(base, Editing.renameFolder(base, "Food", "Social"))
    }

    @Test
    fun `フォルダを消すと中のアプリは一覧本体に戻る`() {
        val c = Editing.removeFolder(base, "Food")
        assertEquals(listOf("Social"), c.drawerFolders.map { it.name })
    }

    @Test
    fun `アプリは1つのフォルダにしか入らない`() {
        val c = Editing.addToFolder(base, "Food", AppKey("line", "line.Main"))
        assertEquals(listOf(AppKey("food"), AppKey("line", "line.Main")), c.drawerFolders[0].apps)
        assertEquals(emptyList<AppKey>(), c.drawerFolders[1].apps)
    }

    @Test
    fun `フォルダからアプリを抜く`() {
        assertEquals(emptyList<AppKey>(), Editing.removeFromFolders(base, AppKey("food")).drawerFolders[0].apps)
    }
}
