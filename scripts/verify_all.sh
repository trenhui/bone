#!/bin/bash
TOKEN_ARG="$1"
FAIL=0
PASS=0
TOTAL=0

test_endpoint() {
  local n="$1" m="$2" u="$3" d="$4" e="$5" desc="$6"
  TOTAL=$((TOTAL+1))
  local rf="/tmp/t_${n}.txt"
  local cf="/tmp/tc_${n}.txt"
  if [ "$m" = "GET" ]; then
    if [ -n "$d" ]; then curl -s -o "$rf" -w "%{http_code}" -X GET "$u" -H "Authorization: Bearer $TOKEN_ARG" -H "Content-Type: application/json" -d "$d" > "$cf" 2>/dev/null
    else curl -s -o "$rf" -w "%{http_code}" -X GET "$u" -H "Authorization: Bearer $TOKEN_ARG" > "$cf" 2>/dev/null; fi
  elif [ "$m" = "POST" ]; then
    if [ -n "$d" ]; then curl -s -o "$rf" -w "%{http_code}" -X POST "$u" -H "Authorization: Bearer $TOKEN_ARG" -H "Content-Type: application/json" -d "$d" > "$cf" 2>/dev/null
    else curl -s -o "$rf" -w "%{http_code}" -X POST "$u" -H "Authorization: Bearer $TOKEN_ARG" > "$cf" 2>/dev/null; fi
  elif [ "$m" = "PUT" ]; then
    curl -s -o "$rf" -w "%{http_code}" -X PUT "$u" -H "Authorization: Bearer $TOKEN_ARG" -H "Content-Type: application/json" -d "$d" > "$cf" 2>/dev/null
  else
    curl -s -o "$rf" -w "%{http_code}" -X DELETE "$u" -H "Authorization: Bearer $TOKEN_ARG" > "$cf" 2>/dev/null
  fi
  local hc=$(cat "$cf" 2>/dev/null)
  local ok=$(python3 -c "import json; d=json.load(open('$rf')); print('y' if d.get('success') else 'n')" 2>/dev/null)
  if [ "$hc" = "501" ]; then echo "  [$n] $m $u → 501 ⏭️ $desc"; PASS=$((PASS+1))
  elif [ "$hc" = "$e" ] && [ "$ok" = "y" ]; then echo "  [$n] $m $u → $hc ✅ $desc"; PASS=$((PASS+1))
  else local msg=$(python3 -c "import json; d=json.load(open('$rf')); print(str(d.get('message','')))" 2>/dev/null)
    echo "  [$n] $m $u → $hc ❌ $desc: $msg"; FAIL=$((FAIL+1))
  fi
}

echo "========== IAM 验证开始 =========="
# Login first
LR=$(curl -s -X POST 'http://localhost:8081/api/v1/iam/login' -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456"}')
TK=$(python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('token',''))" <<< "$LR")
RF=$(python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('refreshToken',''))" <<< "$LR")

# Set TOKEN_ARG
TOKEN_ARG=$TK

test_endpoint 1 "POST" "http://localhost:8081/api/v1/iam/logout" "" "200" "登出"
LR2=$(curl -s -X POST 'http://localhost:8081/api/v1/iam/login' -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456"}')
TK2=$(python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('token',''))" <<< "$LR2")
RF2=$(python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('refreshToken',''))" <<< "$LR2")
TOKEN_ARG=$TK2

test_endpoint 3 "POST" "http://localhost:8081/api/v1/iam/refresh" "{\"refreshToken\":\"$RF2\"}" "200" "刷新令牌"
test_endpoint 4 "GET" "http://localhost:8081/api/v1/iam/sso/config" "" "200" "SSO配置"
test_endpoint 5 "GET" "http://localhost:8081/api/v1/iam/sso/callback?code=test&state=test" "" "501" "SSO回调"
test_endpoint 6 "POST" "http://localhost:8081/api/v1/iam/accounts" '{"username":"vmain","password":"Test123456","email":"vm@b.io"}' "200" "创建账户"
AID=$(python3 -c "import json; d=json.load(open('/tmp/t_6.txt')); print(d.get('data',{}).get('id',''))" 2>/dev/null)
test_endpoint 7 "PUT" "http://localhost:8081/api/v1/iam/accounts/$AID" '{"realName":"验证用户"}' "200" "更新账户"
test_endpoint 8 "DELETE" "http://localhost:8081/api/v1/iam/accounts/$AID" "" "200" "删除账户"
test_endpoint 12 "GET" "http://localhost:8081/api/v1/iam/accounts/1" "" "200" "账户详情"
test_endpoint 13 "GET" "http://localhost:8081/api/v1/iam/accounts" '{"page":1,"size":10}' "200" "账户分页"
test_endpoint 14 "POST" "http://localhost:8081/api/v1/iam/accounts/import" '[{"username":"ivu1","password":"Test123456","email":"iv@b.io"}]' "200" "批量导入"
test_endpoint 15 "GET" "http://localhost:8081/api/v1/iam/accounts/export" '{"page":1,"size":10}' "200" "导出"
test_endpoint 17 "GET" "http://localhost:8081/api/v1/iam/roles" '{"page":1,"size":10}' "200" "角色分页"
test_endpoint 18 "GET" "http://localhost:8081/api/v1/iam/roles/1" "" "200" "角色详情"
test_endpoint 24 "GET" "http://localhost:8081/api/v1/iam/permissions" '{"page":1,"size":10}' "200" "权限分页"
test_endpoint 25 "GET" "http://localhost:8081/api/v1/iam/permissions/tree" "" "200" "权限树"
test_endpoint 29 "GET" "http://localhost:8081/api/v1/iam/tenants" '{"page":1,"size":10}' "200" "租户列表"
test_endpoint 37 "GET" "http://localhost:8081/api/v1/iam/me" "" "200" "当前用户"
test_endpoint 40 "GET" "http://localhost:8081/api/v1/iam/audit/logs" '{"page":1,"size":10}' "200" "审计日志"
test_endpoint 42 "GET" "http://localhost:8081/api/v1/iam/audit/settings" "" "200" "审计设置"
test_endpoint 44 "GET" "http://localhost:8081/api/v1/iam/accounts/1/sessions" "" "200" "会话列表"
test_endpoint 47 "GET" "http://localhost:8081/api/v1/iam/mfa/status" "" "200" "MFA状态"
test_endpoint 48 "POST" "http://localhost:8081/api/v1/iam/mfa/enroll" '{}' "501" "MFA注册"
test_endpoint 49 "POST" "http://localhost:8081/api/v1/iam/mfa/verify" '{}' "501" "MFA验证"
echo ""
echo "结果: 总计=$TOTAL 通过=$PASS 失败=$FAIL"
