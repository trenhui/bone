#!/usr/bin/env bash
# 三模块功能联调检查（HTTP，模拟浏览器经 Vite 代理访问）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IAM="http://localhost:8081"
STUDIO="http://localhost:8088"
SHELL="http://localhost:3000"
IAM_APP="http://localhost:3003"
EXT_APP="http://localhost:3008"
FAIL=0

check() {
  local name=$1 url=$2 expect=$3
  local body
  body=$(curl -sf "$url" 2>/dev/null) || body=""
  if echo "$body" | grep -q "$expect"; then
    echo "  [OK] $name"
  else
    echo "  [FAIL] $name — $url (expected: $expect)"
    FAIL=$((FAIL + 1))
  fi
}

echo "==> 后端直连 (8081)"
check "IAM 控制台" "$IAM/api/console/overview" '"success":true'
check "控制台概览" "$IAM/api/console/overview" '"success":true'
check "快捷操作" "$IAM/api/console/quick-actions" '"success":true'
check "扩展点列表" "$STUDIO/api/v1/extension/points" '"success":true'
check "插件列表" "$STUDIO/api/v1/extension/plugins" '"success":true'

echo "==> 登录"
LOGIN=$(curl -sf -X POST "$IAM/api/iam/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' 2>/dev/null) || LOGIN=""
if echo "$LOGIN" | grep -qE '"success":\s*true|"token"'; then
  echo "  [OK] 登录 admin/admin123"
else
  echo "  [FAIL] 登录 — $LOGIN"
  FAIL=$((FAIL + 1))
fi

echo "==> 经 Shell 代理 (3000)"
for path in /api/console/overview /api/v1/extension/points; do
  check "proxy $path" "$SHELL$path" '"success":true'
done

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
check_page "bone-shell" "$SHELL/"
check_page "bone-iam-app" "$IAM_APP/"
check_page "bone-extension-app" "$EXT_APP/"

echo ""
if [ "$FAIL" -eq 0 ]; then
  echo "全部通过。请在浏览器打开 $SHELL 完成 UI 点击验证。"
  exit 0
fi
echo "$FAIL 项失败。查看 logs/e2e/*.log"
exit 1
