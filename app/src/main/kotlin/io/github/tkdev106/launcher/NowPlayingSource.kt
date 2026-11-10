package io.github.tkdev106.launcher

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import java.net.URI

/**
 * 再生中の曲を読む。通知へのアクセスの許可（MediaListenerService）があるときだけ動く。
 * ホームが前に出ている間だけ見張り、隠れたら止める。
 */
class NowPlayingSource(private val context: Context, private val onChanged: (NowPlayingState?, Boolean) -> Unit) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, MediaListenerService::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null

    // ジャケットを URI でしか渡さないプレーヤーがあるので、読み込んだものを1つだけ覚えておく
    private var artUri: String? = null
    private var artBitmap: Bitmap? = null

    val hasAccess get() = context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(listener)

    /** 通知へのアクセスの許可の画面を、このアプリの項目を開いた状態で出す */
    fun openAccessSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener.flattenToString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pick(it ?: emptyList()) }
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() = start()
    }

    fun start() {
        stop()
        val sessions = (if (hasAccess) runCatching { manager.getActiveSessions(listener) }.getOrNull() else null) ?: run {
            onChanged(null, false)
            return
        }
        runCatching { manager.addOnActiveSessionsChangedListener(sessionsListener, listener, main) }
        pick(sessions)
    }

    fun stop() {
        runCatching { manager.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(callback)
        controller = null
    }

    /** 再生中のものを優先し、無ければ一時停止中で曲の情報があるものを出す */
    private fun pick(sessions: List<MediaController>) {
        controller?.unregisterCallback(callback)
        controller = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull { it.metadata != null }
        controller?.registerCallback(callback, main)
        publish()
    }

    private fun publish() {
        val c = controller
        val m = c?.metadata
        if (c == null || m == null) {
            onChanged(null, true)
            return
        }
        val uri = ART_URI_KEYS.firstNotNullOfOrNull { m.getString(it)?.takeIf(String::isNotBlank) }
            ?: m.description?.iconUri?.toString()
        val art: Bitmap? = ART_KEYS.firstNotNullOfOrNull { m.getBitmap(it) }
            ?: m.description?.iconBitmap
            ?: artBitmap?.takeIf { uri != null && uri == artUri }
        onChanged(NowPlayingState(
            art = art,
            title = m.getString(MediaMetadata.METADATA_KEY_TITLE) ?: m.description?.title?.toString(),
            artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: m.description?.subtitle?.toString(),
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
        ), true)
        if (art == null && uri != null && uri != artUri) loadArt(uri)
    }

    private fun loadArt(uri: String) {
        artUri = uri
        artBitmap = null
        Launcher.background.execute {
            val bmp = runCatching {
                val u = Uri.parse(uri)
                // 裏の線はアイコンや天気と共用なので、応答の無い URL で詰まらないよう待ち時間を切る
                val input = if (u.scheme == "http" || u.scheme == "https") {
                    URI(uri).toURL().openConnection().apply { connectTimeout = 10_000; readTimeout = 10_000 }.getInputStream()
                } else context.contentResolver.openInputStream(u)
                input?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            main.post {
                if (artUri == uri && bmp != null) {
                    artBitmap = bmp
                    publish()
                }
            }
        }
    }

    fun toggle() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun open(from: View, catalog: AppCatalog) {
        val c = controller
        val pending = c?.sessionActivity
        if (pending != null) {
            runCatching {
                pending.send(context, 0, null, null, null, null,
                    android.app.ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
                        .toBundle())
            }.onSuccess { return }
        }
        catalog.launch(io.github.tkdev106.launcher.core.AppKey(c?.packageName ?: APPLE_MUSIC), from)
    }

    companion object {
        const val APPLE_MUSIC = "com.apple.android.music"
        private val ART_KEYS = listOf(MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        private val ART_URI_KEYS = listOf(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
    }
}
