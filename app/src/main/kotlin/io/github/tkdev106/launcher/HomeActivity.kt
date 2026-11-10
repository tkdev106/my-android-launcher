package io.github.tkdev106.launcher

import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout
import android.window.OnBackInvokedDispatcher

class HomeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this))
        // ホームでの「戻る」は何もしない（ランチャー自身が背面に回って黒い画面になるのを避ける）
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {}
    }
}
