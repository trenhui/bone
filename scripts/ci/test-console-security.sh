#!/usr/bin/env bash
# 控制台方法级鉴权回归（不启动完整 E2E 栈，仅 Surefire）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
echo "[console-security] mvn test ConsoleSecurityTest ..."
mvn -q -pl bone-platform/bone-system test -Dtest=ConsoleSecurityTest
echo "[console-security] OK"
