#!/usr/bin/env bash
# 使用 git-filter-repo 从全历史中替换已知泄露字面量（破坏性操作）。
# 用法：bash scripts/git-filter-secrets.sh
# 完成后须：force-push、通知协作者 re-clone、轮换已泄露凭证、重新生成 .gitleaks.baseline.json
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

REPLACEMENTS="${ROOT}/scripts/git-filter-secrets-replacements.txt"

if ! command -v git-filter-repo >/dev/null 2>&1; then
  echo "git-filter-repo 未安装。可执行：pip3 install git-filter-repo" >&2
  exit 1
fi

if [[ ! -f "$REPLACEMENTS" ]]; then
  echo "missing $REPLACEMENTS" >&2
  exit 1
fi

if [[ -n "$(git status --porcelain 2>/dev/null)" ]]; then
  echo "WARN: 工作区有未提交变更，将使用 --force 继续（建议先 commit 或 stash）"
fi

echo "==> git filter-repo --replace-text (rewrite all commits)"
git filter-repo --force --replace-text "$REPLACEMENTS"

echo ""
echo "NOTE: git-filter-repo 会移除 origin 远程。推送前请重新添加，例如："
echo "  git remote add origin git@gitee.com:meishan315/bone.git"
echo ""
echo "==> 历史已重写。请在本机执行："
echo "  1. 轮换 KT / Metadata SDK / 合作方等已泄露凭证"
echo "  2. gitleaks detect --config .gitleaks.toml --report-path .gitleaks.baseline.json  # 刷新 baseline"
echo "  3. git push --force-with-lease origin <branch>   # 需团队确认"
echo "  4. 通知所有人重新 clone 仓库"
