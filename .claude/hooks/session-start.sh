#!/usr/bin/env bash
# Claude Code on the web のコンテナは毎回まっさらな clone から始まるため、消える設定を入れ直す。
# ローカルの対話セッションでは何もしない。
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}"

git config user.name "tkdev106"
git config user.email "178723293+tkdev106@users.noreply.github.com"

# SDK の導入は環境のセットアップスクリプトの仕事。ここでは有無だけを知らせる（無ければ CI で判断する）
if [ -n "${ANDROID_HOME:-}" ] && [ -d "${ANDROID_HOME}" ]; then
  echo "android sdk: ${ANDROID_HOME}"
else
  echo "android sdk: 無い。./gradlew verify は CI の結果で判断する" >&2
fi
