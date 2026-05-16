#!/usr/bin/env bash
# 本地密钥扫描（与 CI secrets-scan 对齐）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

GITLEAKS="${GITLEAKS:-gitleaks}"
BASELINE="${ROOT}/.gitleaks.baseline.json"

run_gitleaks() {
  if command -v "$GITLEAKS" >/dev/null 2>&1; then
    return 0
  fi
  echo "gitleaks not installed; skip gitleaks (install: https://github.com/gitleaks/gitleaks)"
  return 1
}

echo "==> gitleaks (working tree, strict)"
if run_gitleaks; then
  gitleaks detect --source . --no-git --config .gitleaks.toml --verbose
fi

echo "==> gitleaks (git history, baseline — 仅拦截新增泄露)"
if run_gitleaks && [[ -f "$BASELINE" ]]; then
  gitleaks detect --source . --config .gitleaks.toml --baseline-path "$BASELINE" --verbose
else
  echo "skip git history scan (no gitleaks or missing .gitleaks.baseline.json)"
fi

if command -v rg >/dev/null 2>&1; then
  if rg -n --glob '!**/*.md' --glob '!.gitleaks.toml' --glob '!.gitleaks.baseline.json' \
    '@Pukang_123|kQwIOrYvnXmSDkwEiFngrKidMcdrgKor|Fu4uhBw8suvrPGQ4dQHhhfGNIZIqqK|LTAI4G1eXunQDKHkp3E3pxWz' .; then
    echo "ERROR: forbidden secret literals found" >&2
    exit 1
  fi
  echo "rg literal scan: OK"
else
  echo "rg not found; skip literal scan"
fi

echo "secrets scan passed"
