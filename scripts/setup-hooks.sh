#!/usr/bin/env bash
# 安装入库版 git pre-commit。
#
# 此前本脚本只做 `chmod +x scripts/*.sh` 再 echo「hooks 已配置」，
# 却并没有安装任何 git hook；仓库里唯一生效的 `.git/hooks/pre-commit` 是本机手工文件，
# Git 不跟踪它，新 clone 上本地门禁等于不存在（文档却写着已接线）。
# 现在把 `scripts/hooks/pre-commit` 作为唯一真源安装，不改 git config。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

chmod +x scripts/*.sh

HOOK_DIR="$(git rev-parse --git-path hooks)"
mkdir -p "$HOOK_DIR"

# 两个 hook 同一套安装逻辑：pre-commit（门禁 + 接口↔用例同步）、pre-push（影响面用例实跑）
for NAME in pre-commit pre-push; do
  SRC="scripts/hooks/$NAME"
  if [ ! -f "$SRC" ]; then
    echo "缺少入库版 hook: $SRC" >&2
    exit 1
  fi
  TARGET="$HOOK_DIR/$NAME"
  if [ -f "$TARGET" ] && ! cmp -s "$SRC" "$TARGET"; then
    cp "$TARGET" "$TARGET.bak"
    echo "已备份原 hook -> $TARGET.bak"
  fi
  cp "$SRC" "$TARGET"
  chmod +x "$TARGET"
  chmod +x "$SRC"
  echo "$NAME 已安装: $TARGET"
done

echo "自检入口: ./scripts/check.sh（连续失败熔断计数由 check.sh 自维护）"
echo "接口变更同步: ./scripts/e2e/impact.py --staged --strict（pre-commit 已自动调用）"
echo "造数资产同步: ./scripts/check-feature-data-impact.py（提交自动刷新 coverage / 校验同步义务；推送按 rev 校验）"
echo "推送前验证: ./scripts/e2e/impact.py --rev <upstream>...HEAD（pre-push 自动跑受影响用例）"
echo "说明: .claude/hooks/hooks.json 是编辑器侧 hooks，与 git hooks 是两套，互不影响。"
