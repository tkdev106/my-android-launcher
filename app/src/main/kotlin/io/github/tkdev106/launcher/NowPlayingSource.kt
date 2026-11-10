package io.github.tkdev106.launcher

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.View

/**
 * 再生中の曲を読む。通知へのアクセスの許可（MediaListenerService）があるときだけ動く。
 * ホームが前に出ている間だけ見張り、隠れたら止める。
 */
class NowPlayingSource(private val context: Context, private val onChanged: (NowPlayingState?) -> Unit) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, MediaListenerService::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pick(it ?: emptyList()) }
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() = start()
    }

    fun start() {
        stop()
        val sessions = runCatching { manager.getActiveSessions(listener) }.getOrNull() ?: run {
            onChanged(null)
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
            onChanged(null)
            return
        }
        val art: Bitmap? = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART)
        onChanged(NowPlayingState(
            art = art,
            title = m.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
        ))
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
    }
}
