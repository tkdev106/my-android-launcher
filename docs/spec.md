# 自作ランチャー 仕様（Nova Launcher 8.2.4 のバックアップから起こしたもの）

目的: Nova で実際に使っている機能だけを再現し、Nova より軽いホームアプリにする。自分の端末専用（Pixel 7 / Android 17 / 2400×1080 / 420dpi）。Play ストアには出さない。

## 作り方

- 素の Android View（AndroidX も Compose も使わない）。依存は Kotlin 標準ライブラリだけにして、起動とメモリを最小にする
- minSdk = targetSdk = Android 17 の API。古い版への分岐を持たない
- 配置とフォルダはアプリ内部の JSON ファイル1つに保存する（Room 等を足さない。そのままバックアップにもなる）
- 常駐するのはユーザー補助サービス1つだけ（ピンチアウトの最近使ったアプリと、下スワイプの通知に使う）

## やらないこと

- Google フィード（左のページ）
- ページの追加・ページ一覧表示（ホームは1ページ固定）
- 通知バッジ、アイコンパック、予測表示、ドロワーのタブ、スクロール効果
- 多機能な設定画面

## ホーム画面（1ページ・4列×7行・半マス単位）

座標は (列, 行)、大きさは 列×行。重ね置きを許す（ウィジェットの上にタップ領域を重ねる）。

| 位置 | 大きさ | 中身 |
| --- | --- | --- |
| (0, 0) | 2×2.5 | ウィジェット: Google 時計 `com.google.android.deskclock/com.android.alarmclock.DigitalAppWidgetProvider` |
| (2.5, 0.5) | 1×1 | 透明なタップ領域 → Google カレンダー |
| (0, 1.5) | 2×1.5 | ウィジェット: Yahoo!天気 `jp.co.yahoo.android.weather.type1/jp.co.yahoo.android.weather.feature.widget.ForecastWidgetReceiver` |
| (0, 3) | 4×1.5 | ウィジェット: aCalendar 予定一覧 `org.withouthat.acalendar/org.withouthat.acalendar.agenda.AgendaWidget` |
| (1,3) (0,3.5) (1.5,3.5) (1.5,4) | 各 1×1 | 透明なタップ領域 → Google カレンダー（aCalendar の上に重ねる） |
| (0, 4) | 4×2 | ウィジェット: Apple Music `com.apple.android.music/com.apple.android.music.widget.AppleMusicWidget` |
| (0, 4.5) | 1×1 | 透明なタップ領域 → Apple Music |
| (0..3, 6) | 各 1×1 | LINE / X / Chrome / YouTube（`app.revanced.android.youtube`）。白線の丸いカスタムアイコン、ラベルなし |

自作では「透明なアイコン」ではなく、ウィジェットの上に置く**タップ領域**として定義する。

重なりの上下: ウィジェットとアイコンは後から置いた方が上。タップ領域は常にウィジェットより上（押せるように）。

## ドック（5列、4つ）

左から1〜4列目に置き、5列目（右端）は空ける。

1. フォルダ「PayPay / 楽天ペイ」（カード柄のカスタムアイコン）
2. FUJI ROCK FESTIVAL '26（`jp.eplus.fujirock`）
3. アプリ一覧を開くボタン（点9個のカスタムアイコン）
4. SUMMER SONIC（`jp.eplus.summer_sonic`）

## アプリ一覧（ドロワー）

- 横にページをめくる形式、6列×7行、半透明の背景、上に検索バー
- フォルダを先頭に並べる: Entertainment / Finance / Food / Games / Shopping / Social / Travel
- フォルダに入れたアプリは一覧本体から隠す
- 並びは名前順（英字 → かな）
- 検索はアプリ名の部分一致。フォルダに入ったアプリも結果に出す
- 新しく入れたアプリはドロワーに出るだけで、ホームには何も足さない
- フォルダの中身は Nova のバックアップの値を初期値にする（別ファイル）

## ジェスチャー

| 操作 | 動作 |
| --- | --- |
| 上へスワイプ / 2本指で下へ | アプリ一覧 |
| 下へスワイプ | 通知を開く |
| ピンチアウト | 最近使ったアプリ（ユーザー補助の `GLOBAL_ACTION_RECENTS`） |
| 2本指で上へ | 編集モード（設定画面） |

## 編集モード（最小限の設定画面）

- ホームの配置の変更（ウィジェット・アイコン・タップ領域の追加・移動・大きさ変更・削除）
- ドロワーのフォルダの編集
- 個別アイコンの差し替え

## 見た目

- 常にダーク、縦画面固定、壁紙はスクロールしない
- フォルダのアイコンは円形、フォルダは窓で開く
