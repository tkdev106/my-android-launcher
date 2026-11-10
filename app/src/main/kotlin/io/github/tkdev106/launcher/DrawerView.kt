package io.github.tkdev106.launcher

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import io.github.tkdev106.launcher.Ui.dp
import io.github.tkdev106.launcher.core.Drawer
import io.github.tkdev106.launcher.core.DrawerEntry
import io.github.tkdev106.launcher.core.Folder
import io.github.tkdev106.launcher.core.InstalledApp

/**
 * アプリ一覧（docs/spec.md「アプリ一覧」）。背景は透明で、上に検索バー、6×7 を横にめくる。
 * 検索中はページの代わりに結果だけを並べる。
 */
class DrawerView(
    context: Context,
    private val onLaunch: (InstalledApp, View) -> Unit,
    private val onOpenFolder: (DrawerEntry.Folder, View) -> Unit,
    private val onClose: () -> Unit,
) : LinearLayout(context) {
    private val icons = Launcher.icons(context)
    private val iconPx = dp(48)
    private val order = Drawer.labelOrder()
    private var installed: List<InstalledApp> = emptyList()
    private var entries: List<DrawerEntry> = emptyList()

    private val search = EditText(context)
    private val grid = PagedGrid(context, Drawer.COLUMNS, Drawer.ROWS)
    private val results = PagedGrid(context, Drawer.COLUMNS, Drawer.ROWS)
    private val dots = PageDots(context)

    init {
        orientation = VERTICAL
        setPadding(dp(16), dp(32), dp(16), dp(20))

        val bar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.pill(context, Ui.CIRCLE, 24f, Ui.BORDER)
            setPadding(dp(18), 0, dp(18), 0)
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_search)
                setColorFilter(Ui.TEXT_DIM)
            }, LayoutParams(dp(18), dp(18)))
            addView(search, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { marginStart = dp(10) })
        }
        search.apply {
            background = null
            hint = context.getString(R.string.search_hint)
            setHintTextColor(0xFF9A9893.toInt())
            setTextColor(Ui.TEXT)
            typeface = Ui.serif(context)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
            setPadding(0, 0, 0, 0)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_GO
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) = showResults()
            })
            // 確定キーでは先頭の結果を開く
            setOnEditorActionListener { v, _, _ ->
                val first = Drawer.search(text.toString(), installed, order).firstOrNull()
                if (first != null) onLaunch(first, v)
                true
            }
        }
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)).apply { setMargins(dp(4), 0, dp(4), 0) })

        val pages = FrameLayout(context)
        pages.addView(grid, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        pages.addView(results, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        results.visibility = GONE
        addView(pages, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(24) })
        addView(dots, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })

        grid.onPageChanged = { p -> dots.count = grid.pageCount; dots.current = p }
        grid.onSwipeDown = onClose
        results.onSwipeDown = onClose
    }

    fun bind(installed: List<InstalledApp>, folders: List<Folder>) {
        this.installed = installed
        entries = Drawer.entries(installed, folders, order)
        grid.setItems(entries.map(::cell))
        dots.count = grid.pageCount
        showResults()
    }

    /** 開くたびに1ページ目・空の検索から始める */
    fun reset() {
        search.setText("")
        search.clearFocus()
        hideKeyboard()
        grid.showPage(0, animate = false)
    }

    fun hideKeyboard() {
        context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(windowToken, 0)
    }

    /** 裏の線でアイコンを先に描いておく。初めて開いたときに丸が空のまま出ないように */
    fun warmUp() {
        val list = entries
        Launcher.background.execute {
            list.forEach {
                when (it) {
                    is DrawerEntry.App -> icons.app(it.app.key, iconPx)
                    is DrawerEntry.Folder -> icons.folder(it.name, it.apps.map { a -> a.key }, iconPx)
                }
            }
        }
    }

    private fun showResults() {
        val q = search.text.toString()
        if (q.isBlank()) {
            results.visibility = GONE
            grid.visibility = VISIBLE
            dots.visibility = VISIBLE
            return
        }
        results.setItems(Drawer.search(q, installed, order).map { cell(DrawerEntry.App(it)) })
        results.showPage(0, animate = false)
        results.visibility = VISIBLE
        grid.visibility = INVISIBLE
        dots.visibility = INVISIBLE
    }

    private fun cell(entry: DrawerEntry): View = FrameLayout(context).apply {
        val icon = IconView(context)
        when (entry) {
            is DrawerEntry.App -> {
                icon.contentDescription = entry.app.label
                icon.load(entry.app.key to iconPx) { icons.app(entry.app.key, iconPx) }
                icon.setOnClickListener { onLaunch(entry.app, it) }
            }
            is DrawerEntry.Folder -> {
                icon.contentDescription = entry.name
                val keys = entry.apps.map { it.key }
                icon.load(entry.name to keys) { icons.folder(entry.name, keys, iconPx) }
                icon.setOnClickListener { onOpenFolder(entry, it) }
            }
        }
        addView(icon, FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER))
    }
}
