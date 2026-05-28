#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

CHECK=false
for arg in "$@"; do
  case "$arg" in
    --check) CHECK=true ;;
  esac
done

if [[ "$CHECK" == true ]]; then
  python3 tools/console-compliance-collector/collect.py --check
  exit 0
fi

python3 tools/console-compliance-collector/collect.py
