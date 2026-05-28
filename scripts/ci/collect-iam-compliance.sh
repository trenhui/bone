#!/usr/bin/env bash
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

if [[ "$CHECK" == true ]]; then
  python3 tools/iam-compliance-collector/collect.py --check
  exit 0
fi

if [[ "$SYNC_DOC" == true ]]; then
  python3 tools/iam-compliance-collector/collect.py --sync-doc
else
  python3 tools/iam-compliance-collector/collect.py
fi
