#!/usr/bin/env bash
# 元数据模块 Docs-as-Code 合规收集
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

SYNC=false
CHECK=false
for arg in "$@"; do
  case "$arg" in
    --sync-doc) SYNC=true ;;
    --check) CHECK=true ;;
  esac
done

if [[ "$CHECK" == true ]]; then
  python3 tools/metadata-compliance-collector/collect.py --check
  exit 0
fi

if [[ "$SYNC" == true ]]; then
  python3 tools/metadata-compliance-collector/collect.py --sync-doc
else
  python3 tools/metadata-compliance-collector/collect.py
fi
python3 tools/metadata-compliance-collector/collect.py --check
echo "[ok] metadata compliance artifacts refreshed"
