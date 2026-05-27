#!/usr/bin/env bash
# 扩展模块 Docs-as-Code：生成/校验 compliance 产物
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
SYNC_DOC=false
CHECK=false
for arg in "$@"; do
  case "$arg" in
    --sync-doc) SYNC_DOC=true ;;
    --check) CHECK=true ;;
  esac
done
ARGS=()
if [[ "$CHECK" == true ]]; then
  ARGS+=(--check)
elif [[ "$SYNC_DOC" == true ]]; then
  ARGS+=(--sync-doc)
fi
python3 tools/extension-compliance-collector/collect.py "${ARGS[@]}"
if [[ "$CHECK" == false && "$SYNC_DOC" == false ]]; then
  python3 tools/extension-compliance-collector/collect.py --sync-doc
fi
