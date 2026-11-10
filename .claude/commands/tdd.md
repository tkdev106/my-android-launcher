---
description: "テストケースの洗い出し → テスト（赤）→ 実装（緑）を、3つのサブエージェントで1周回す"
---

# /tdd — テスト駆動で1件実装する

```
test-designer（洗い出し） → test-writer（赤） → implementer（緑）
```

コミットも push もしない（それは `/next` の仕事）。引数が無いときは何を実装するか聞き返す。推測で始めない。
サブエージェントは会話を見ていない。**タスクの本文と `docs/spec.md` の該当節を毎回渡す。**

1. **`test-designer` を呼ぶ**
2. **一覧を検分する。** 根拠（`docs/spec.md` の記述）の無い行と、実装詳細（呼び出し回数・内部状態）を握る行を落とす。「判断が要るもの」があれば止まる
3. **`test-writer` を呼ぶ**（検分後の一覧を渡す）
4. **赤を自分で確かめる。** `./gradlew testDebugUnitTest` で、落ち方がコンパイルエラーではなく期待値の不一致か `NotImplementedError` であること。確かめたらテストを明示して stage し、基準を記録する:
   ```sh
   git add <書かれたテストファイル>
   mkdir -p .tdd && git write-tree > .tdd/tests-at-red.tree
   ```
5. **`implementer` を呼ぶ**
6. **緑を自分で確かめる。** `./gradlew verify` と、テストが動いていないこと（次の2つがどちらも何も出さない）:
   ```sh
   git diff --name-only "$(cat .tdd/tests-at-red.tree)" -- '*/src/test/*'
   git ls-files --others --exclude-standard -- '*/src/test/*'
   ```
   出たら `implementer` に差し戻す。テストが書き換わっていたら緑は意味を持たない

## 差し戻す条件

| 見たもの | 戻す先 |
| --- | --- |
| 根拠の無いケース・実装詳細のケース | `test-designer` |
| 赤がコンパイルエラー | `test-writer`（スタブが足りない） |
| テストファイルが書き換わっている | `implementer` |
| 入力を見て期待値を返しているだけの実装 | `implementer` |

`implementer` が「テストが仕様として誤っている」と報告したら止まる。テストを直すかは仕様の判断である。
