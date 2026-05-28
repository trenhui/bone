#!/usr/bin/env bash
# 三模块功能联调检查（v2.0+，HTTP，模拟浏览器经 Vite 代理访问）
# v2.0 起：
#   - 控制台 API 由 bone-system:8083 提供 /api/v1/console/*（需 JWT + sys:console:read）
#   - IAM 登录路径 /api/v1/iam/login（默认 admin/123456，与 bone-init.sql 一致）
#   - 扩展由 bone-extension-studio:8088 提供 /api/v1/extension/*
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=scripts/lib/e2e-jwt.sh
source "$ROOT/scripts/lib/e2e-jwt.sh"
IAM="http://localhost:8081"
SYSTEM="http://localhost:8083"
STUDIO="http://localhost:8088"
SHELL_URL="http://localhost:3000"
IAM_APP="http://localhost:3003"
EXT_APP="http://localhost:3008"
FAIL=0
E2E_TOKEN=""

check() {
  local name=$1 url=$2 expect=$3
  local body
  body=$(curl -sf "$url" 2>/dev/null) || body=""
  if echo "$body" | grep -qE "$expect"; then
    echo "  [OK] $name"
  else
    echo "  [FAIL] $name — $url (expected pattern: $expect)"
    FAIL=$((FAIL + 1))
  fi
}

check_auth() {
  local name=$1 url=$2 expect=$3
  if [ -z "$E2E_TOKEN" ]; then
    echo "  [FAIL] $name — 无 JWT（请先登录 IAM）"
    FAIL=$((FAIL + 1))
    return
  fi
  local body
  body=$(curl -sf -H "Authorization: Bearer ${E2E_TOKEN}" "$url" 2>/dev/null) || body=""
  if echo "$body" | grep -qE "$expect"; then
    echo "  [OK] $name"
  else
    echo "  [FAIL] $name — $url (expected pattern: $expect)"
    FAIL=$((FAIL + 1))
  fi
}

echo "==> 登录（bone-iam）"
LOGIN=$(e2e_iam_login "$IAM")
E2E_TOKEN=$(e2e_extract_token "$LOGIN")
if [ -n "$E2E_TOKEN" ] && echo "$LOGIN" | grep -qE '"code":\s*200'; then
  echo "  [OK] 登录 admin/123456 (/api/v1/iam/login)"
else
  echo "  [FAIL] 登录 — $LOGIN"
  FAIL=$((FAIL + 1))
fi

echo "==> 后端直连（bone-system 控制台 · 需 sys:console:read）"
check_auth "控制台概览"  "$SYSTEM/api/v1/console/overview"      '"code":\s*200'
check_auth "服务状态"    "$SYSTEM/api/v1/console/services"      '"code":\s*200'
check_auth "资源使用"    "$SYSTEM/api/v1/console/resources"     '"code":\s*200'
check_auth "关键指标"    "$SYSTEM/api/v1/console/metrics"       '"code":\s*200'
check_auth "快捷操作"    "$SYSTEM/api/v1/console/quick-actions" '"code":\s*200'

echo "==> 后端直连（bone-extension-studio）"
check "扩展点列表"  "$STUDIO/api/v1/extension/points"  '"success":\s*true'
check "插件列表"    "$STUDIO/api/v1/extension/plugins" '"success":\s*true'

echo "==> 经 Shell Vite 代理 (3000)"
if [ -n "$E2E_TOKEN" ]; then
  check_auth "proxy /api/v1/console/overview" "$SHELL_URL/api/v1/console/overview" '"code":\s*200'
else
  echo "  [SKIP] proxy /api/v1/console/overview — 无 JWT"
  FAIL=$((FAIL + 1))
fi
check "proxy /api/v1/extension/points" "$SHELL_URL/api/v1/extension/points" '"code":\s*200|"success":\s*true'

echo "==> 前端页面可访问"
check_page() {
  local label=$1 url=$2
  if curl -sf -o /dev/null "$url" 2>/dev/null; then
    echo "  [OK] $label $url"
  else
    echo "  [FAIL] $label $url"
    FAIL=$((FAIL + 1))
  fi
}
check_page "bone-shell"          "$SHELL_URL/"
check_page "bone-iam-app"        "$IAM_APP/"
check_page "bone-extension-app"  "$EXT_APP/"

echo ""
if [ "$FAIL" -eq 0 ]; then
  echo "全部通过。请在浏览器打开 $SHELL_URL 完成 UI 点击验证。"
  exit 0
fi
echo "$FAIL 项失败。查看 logs/e2e/*.log"
exit 1
