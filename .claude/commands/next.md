---
description: "backlog の先頭タスクを1件だけ、作業ブランチ1本 + PR 1本で main に入れる（自律ループの1周）"
---

# /next — 自律ループの1周

`docs/backlog.md` の先頭タスクを**1件だけ**実装し、PR にして `main` に入れる。引数があればそれをタスクの指定として扱う。

1. **同期する**: `git switch main && git pull --ff-only`。未コミットの変更が残っていたら止めて報告する
2. **タスクを選ぶ**: 「次にやること」の先頭。1 PR に収まらないなら、実装ではなく backlog を分割する PR を出す
3. **枝を切る**: `git switch -c <type>/<slug>`（止まったときにドラフト PR を push できるよう、最初に切る）
4. **実装する**: `/tdd` を呼ぶ。テストで押さえられない部分（画面・システムとの結合）は最小限にとどめ、PR に「端末で確かめてほしいこと」として書く
5. **`./gradlew verify`** を緑にする（`/verify`）。同じ失敗を2回直せなかったら止まる
6. **自己レビュー**: `git fetch origin main` のあと `reviewer` に `origin/main...HEAD` の差分を見させる。指摘は直すか、直さない理由を PR に書く
7. **コミット**: 入れるファイルを明示して stage する。1コミット1目的。**backlog の済んだ行はこの PR で消す**
8. **PR**: `git push -u origin <branch>` → GitHub の MCP ツールで作成。本文は「何を・なぜ」「端末で確かめてほしいこと」の2節
9. **CI を待つ**: 赤ならログを読んで直す。`sleep` で回して待たない
10. **squash merge** → `git switch main && git pull --ff-only`

止まる条件は `CLAUDE.md`「止まる条件」。当たったらドラフト PR を push し、1つの質問にして終える。
