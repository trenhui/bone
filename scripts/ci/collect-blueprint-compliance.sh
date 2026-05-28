#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
CHECK=false
for arg in "$@"; do
  [[ "$arg" == "--check" ]] && CHECK=true
done
if [[ "$CHECK" == true ]]; then
  python3 tools/blueprint-compliance-collector/collect.py --check
else
  python3 tools/blueprint-compliance-collector/collect.py
fi
