#!/usr/bin/env bash
# E2E 辅助：从 IAM 登录响应提取 JWT，供 bone-system 控制台等受保护 API 使用。
# shellcheck disable=SC2034  # E2E_TOKEN 由调用方 export

e2e_extract_token() {
  local login_json=$1
  echo "$login_json" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    data = d.get('data') or {}
    print(data.get('token') or data.get('accessToken') or '')
except Exception:
    print('')
" 2>/dev/null
}

e2e_iam_login() {
  local iam_base=$1
  local user=${2:-admin}
  local pass=${3:-123456}
  curl -sf -X POST "${iam_base}/api/v1/iam/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${user}\",\"password\":\"${pass}\"}" 2>/dev/null || true
}
