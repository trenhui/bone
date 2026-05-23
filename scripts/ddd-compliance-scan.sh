#!/usr/bin/env bash
# Bone-DDD 合规扫描（只读）。真源：doc/architecture/Bone-DDD-最终实践方案.md
#
# 用法：
#   bash scripts/ddd-compliance-scan.sh              # 扫描默认应用模块
#   bash scripts/ddd-compliance-scan.sh bone-iam     # 仅扫描指定模块路径
#
# 退出码：发现违规时返回 1，否则 0。

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

DEFAULT_MODULES=(
  bone-blueprint
  bone-platform/bone-iam
  bone-platform/bone-masterdata
  bone-platform/bone-system
  bone-platform/bone-integration
  bone-engine/studio-generator
  bone-engine/bone-extension-engine/bone-extension-studio
  bone-engine/bone-metadata-server
)

MODULES=("${@:-${DEFAULT_MODULES[@]}}")

violations=0

scan() {
  local label="$1"
  local path="$2"
  local pattern="$3"
  if [ ! -d "$path/src/main/java" ] 2>/dev/null && [ ! -d "$path/src" ] 2>/dev/null; then
    return
  fi
  local hits
  hits=$(rg -n --glob '*.java' "$pattern" "$path/src" 2>/dev/null || true)
  if [ -n "$hits" ]; then
    echo ""
    echo "== $label ($path) =="
    echo "$hits"
    violations=$((violations + 1))
  fi
}

echo "Bone-DDD compliance scan @ $ROOT"
echo "Modules: ${MODULES[*]}"

for mod in "${MODULES[@]}"; do
  [ -d "$mod" ] || continue
  scan "P0-7 UseCase class" "$mod" 'class \w+UseCase\b'
  scan "usecase package" "$mod" 'application\.usecase'
  scan "domain.store" "$mod" 'domain\.store'
  scan "BusinessException class" "$mod" 'class (BusinessException|\w+BusinessException)\b'
  scan "*Cmd class" "$mod" 'class \w+Cmd\b'
  scan "*Qry class" "$mod" 'class \w+Qry\b'
  scan "root controller package" "$mod" 'package com\.bone\.[^.]+\.controller;'
  scan "converter *Cmd method" "$mod" 'to\w+Cmd\('
done

echo ""
if [ "$violations" -gt 0 ]; then
  echo "FAIL: $violations violation category(ies) found."
  echo "Fix with scripts/ddd-rename-cmd-qry.py, scripts/ddd-archtest-template.py (see Bone-DDD appendix B.2)."
  exit 1
fi
echo "OK: no known violation patterns in scanned modules."
exit 0
