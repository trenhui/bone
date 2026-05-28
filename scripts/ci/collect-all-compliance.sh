#!/usr/bin/env bash
# 运行全部已注册模块的 Docs-as-Code 收集器
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

SYNC_DOC=false
CHECK_ONLY=false
for arg in "$@"; do
  case "$arg" in
    --sync-doc) SYNC_DOC=true ;;
    --check) CHECK_ONLY=true ;;
  esac
done

# 支持 --sync-doc 的收集器（详设嵌入 GENERATED 块）
SYNC_COLLECTORS=(
  extension-compliance-collector
  iam-compliance-collector
  metadata-compliance-collector
  integration-compliance-collector
  masterdata-compliance-collector
  generator-compliance-collector
)

# 仅生成 _generated 输出、不嵌入详设的收集器
PLAIN_COLLECTORS=(
  blueprint-compliance-collector
  console-compliance-collector
)

ALL_COLLECTORS=("${SYNC_COLLECTORS[@]}" "${PLAIN_COLLECTORS[@]}")

if [[ "$CHECK_ONLY" == true ]]; then
  for c in "${ALL_COLLECTORS[@]}"; do
    python3 "tools/${c}/collect.py" --check
  done
  echo "[ok] all compliance.json files up to date (${#ALL_COLLECTORS[@]} modules)"
  exit 0
fi

for c in "${SYNC_COLLECTORS[@]}"; do
  if [[ "$SYNC_DOC" == true ]]; then
    python3 "tools/${c}/collect.py" --sync-doc
  else
    python3 "tools/${c}/collect.py"
  fi
done
for c in "${PLAIN_COLLECTORS[@]}"; do
  python3 "tools/${c}/collect.py"
done

for c in "${ALL_COLLECTORS[@]}"; do
  python3 "tools/${c}/collect.py" --check
done
echo "[ok] ${#ALL_COLLECTORS[@]} module compliance artifacts refreshed"
