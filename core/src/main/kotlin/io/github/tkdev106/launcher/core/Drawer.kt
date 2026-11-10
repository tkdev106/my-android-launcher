package io.github.tkdev106.launcher.core

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** アプリ一覧に並べるもの。フォルダが先、その後にフォルダに入っていないアプリ */
sealed interface DrawerEntry {
    data class Folder(val name: String, val apps: List<InstalledApp>) : DrawerEntry
    data class App(val app: InstalledApp) : DrawerEntry
}

object Drawer {
    const val COLUMNS = 6
    const val ROWS = 7
    const val PER_PAGE = COLUMNS * ROWS

    /** 名前順。日本語の照合順なので英字 → かなの順になる */
    fun labelOrder(): Comparator<String> {
        val collator = Collator.getInstance(Locale.JAPANESE)
        return Comparator { a, b -> collator.compare(a, b) }
    }

    /**
     * 入っているアプリとフォルダの設定から一覧を作る。
     * 入っているアプリが1つも無いフォルダは出さない。フォルダに入れたアプリ（パッケージ単位）は本体から隠す。
     */
    fun entries(installed: List<InstalledApp>, folders: List<Folder>, order: Comparator<String>): List<DrawerEntry> {
        val folderEntries = folders.mapNotNull { folder ->
            val apps = folder.apps.mapNotNull { it.resolve(installed) }.distinct()
            if (apps.isEmpty()) null else DrawerEntry.Folder(folder.name, apps)
        }
        val hidden = folderEntries.flatMap { f -> f.apps.map { it.key.packageName } }.toSet()
        val apps = installed
            .filter { it.key.packageName !in hidden }
            .sortedWith(compareBy(order) { it: InstalledApp -> it.label }.thenBy { it.key.flatten() })
            .map { DrawerEntry.App(it) }
        return folderEntries + apps
    }

    /** 横にめくるページに分ける。空でも1ページは返す（空の一覧を描くため） */
    fun <T> pages(items: List<T>, perPage: Int = PER_PAGE): List<List<T>> =
        if (items.isEmpty()) listOf(emptyList()) else items.chunked(perPage)

    /**
     * 検索。フォルダの中のアプリも含めて、名前に語を含むものを返す。
     * 先頭が一致するものを前に出す。大文字小文字・全角半角・ひらがなとカタカナの違いは無視する。
     */
    fun search(query: String, installed: List<InstalledApp>, order: Comparator<String>): List<InstalledApp> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        return installed
            .map { it to normalize(it.label) }
            .filter { (_, label) -> label.contains(q) }
            .sortedWith(compareBy<Pair<InstalledApp, String>> { (_, label) -> if (label.startsWith(q)) 0 else 1 }
                .thenBy(order) { it.first.label })
            .map { it.first }
    }

    fun normalize(text: String): String {
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()
        val sb = StringBuilder()
        for (c in nfkc) {
            if (c.isWhitespace()) continue
            // カタカナをひらがなに寄せる（ァ〜ヶ）
            sb.append(if (c in 'ァ'..'ヶ') c - 0x60 else c)
        }
        return sb.toString()
    }
}
