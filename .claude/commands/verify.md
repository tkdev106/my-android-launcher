---
description: "./gradlew verify を走らせ、落ちた段だけを報告する"
---

# /verify — 完了の定義を確かめる

```
./gradlew verify
```

`lint` → `testDebugUnitTest` → `assembleDebug` の順に走る。緑なら1行で報告する。
落ちた段は**エラーの出力そのもの**と原因の判断を添える。

- テストを skip・無効化・削除して緑にしない
- lint の規則を緩めたり baseline に逃がしたりして緑にしない
- コンテナに Android SDK が無くて走らないときは、そう書いて CI の結果で判断する。緑を装わない
- 同じ失敗を2回直せなかったら止めて報告する
