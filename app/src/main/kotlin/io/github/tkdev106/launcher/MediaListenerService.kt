package io.github.tkdev106.launcher

import android.service.notification.NotificationListenerService

/**
 * 通知へのアクセスの許可を受けるためだけの空のサービス（docs/spec.md「作り方」で常駐を認めた2つ目）。
 * 再生中の曲は、この許可を使ってホームが MediaSessionManager から読む。通知そのものは扱わない。
 */
class MediaListenerService : NotificationListenerService()
