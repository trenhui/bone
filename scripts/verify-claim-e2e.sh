#!/usr/bin/env bash
# =============================================================================
# synap-claim 理赔链路端到端联调（经网关 :8888 南北向入口，模拟人工操作）
#
# 覆盖：登录鉴权 → 报案（幂等创建/重放/冲突）→ 保单校验拒收 → 详情/分页/防枚举 404
#       → 全状态机工作流（分配→初审→转人工→调赔→决策→复核→ finished）
#       → CORS 预检（bone-claim-app :3013 联调源）
#
# 前置：网关 :8888、synap-claim :8087（dev profile，DevPolicySnapshotSeeder 已灌测试保单）
# 用法：bash scripts/verify-claim-e2e.sh
# =============================================================================
set -u

GW="${GW:-http://localhost:8888}"
CLAIM_DIRECT="${CLAIM_DIRECT:-http://localhost:8087}"
USER_NAME="${USER_NAME:-admin}"
PASSWORD="${PASSWORD:-123456}"

PASS=0
FAIL=0
FAILED_CASES=()

# ---------- 断言助手 ----------
# check <名称> <实际HTTP码> <期望码(可空格分隔多个)>
check() {
  local name="$1" actual="$2" expected="$3"
  if [[ " $expected " == *" $actual "* ]]; then
    PASS=$((PASS + 1))
    printf "  \033[32m✓\033[0m %-52s [%s]\n" "$name" "$actual"
  else
    FAIL=$((FAIL + 1))
    FAILED_CASES+=("$name (期望 $expected, 实际 $actual)")
    printf "  \033[31m✗\033[0m %-52s [%s != %s]\n" "$name" "$actual" "$expected"
  fi
}

# jget <点分路径>：从 stdin JSON 按 data.token 形式取值（无则空串）
jget() {
  python3 -c "
import sys, json
def dig(d, path):
    for k in path.split('.'):
        d = d.get(k) if isinstance(d, dict) else None
        if d is None:
            return ''
    return d
try:
    print(dig(json.load(sys.stdin), '$1'))
except Exception:
    print('')
" 2>/dev/null
}

# jlen <点分路径>：取 JSON 数组长度
jlen() {
  python3 -c "
import sys, json
def dig(d, path):
    for k in path.split('.'):
        d = d.get(k) if isinstance(d, dict) else None
        if d is None:
            return ''
    return d
try:
    v = dig(json.load(sys.stdin), '$1')
    print(len(v) if isinstance(v, list) else 0)
except Exception:
    print(0)
" 2>/dev/null
}

# jfirst <点分路径> <字段>：按路径取数组元素字段（如 jfirst data.0 status）；
# 路径段为纯数字时按数组下标解析，故详情接口 data 为数组也可用。
jfirst() {
  python3 -c "
import sys, json
try:
    v = json.load(sys.stdin)
    for k in '$1'.split('.'):
        if isinstance(v, list):
            v = v[int(k)] if k.isdigit() and int(k) < len(v) else None
        elif isinstance(v, dict):
            v = v.get(k)
        else:
            v = None
        if v is None:
            break
    print(v.get('$2', '') if isinstance(v, dict) else '')
except Exception:
    print('')
" 2>/dev/null
}

code_of() { echo "$1" | tail -n 1; }
body_of() { echo "$1" | sed '$d'; }

req() { # req <method> <url> [curl额外参数...]；输出：body\nHTTP码
  local method="$1" url="$2"; shift 2
  curl -s --noproxy '*' -X "$method" "$url" "$@" -w "\n%{http_code}"
}

echo "=========================================================="
echo " synap-claim 理赔链路 E2E（网关 ${GW}）"
echo " $(date '+%Y-%m-%d %H:%M:%S')"
echo "=========================================================="

# ---------- Step 0：基础设施健康 ----------
echo ""
echo "[Step 0] 基础设施健康检查"
GW_HEALTH=$(code_of "$(req GET "$GW/actuator/health")")
check "网关 :8888 /actuator/health" "$GW_HEALTH" "200"
CLAIM_HEALTH=$(code_of "$(req GET "$CLAIM_DIRECT/actuator/health")")
check "理赔 :8087 /actuator/health（直连）" "$CLAIM_HEALTH" "200"

# ---------- Step 1：登录取 JWT ----------
echo ""
echo "[Step 1] 登录获取 JWT（IAM admin）"
LOGIN=$(req POST "$GW/api/v1/iam/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER_NAME\",\"password\":\"$PASSWORD\"}")
LOGIN_CODE=$(code_of "$LOGIN")
LOGIN_BODY=$(body_of "$LOGIN")
check "POST /api/v1/iam/login" "$LOGIN_CODE" "200"
TOKEN=$(echo "$LOGIN_BODY" | jget data.token)
[ -z "$TOKEN" ] && TOKEN=$(echo "$LOGIN_BODY" | jget data.accessToken)
if [ -z "$TOKEN" ]; then
  printf "  \033[31m✗ 未取得 JWT，后续鉴权用例无法执行\033[0m\n"
  echo "  响应: $(echo "$LOGIN_BODY" | head -c 300)"
  exit 1
fi
AUTH=(-H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json")

# ---------- Step 2：鉴权 enforcement ----------
echo ""
echo "[Step 2] 网关鉴权 enforcement"
NOAUTH=$(code_of "$(req GET "$GW/api/v1/claims")")
check "无 token 访问 /api/v1/claims 被拒" "$NOAUTH" "401 403"

# ---------- Step 3：报案（幂等创建） ----------
echo ""
echo "[Step 3] 报案创建（带 Idempotency-Key）"
IDEM_KEY="e2e-$(uuidgen | tr 'A-Z' 'a-z')"
CLAIM_BODY='{
  "policyNo": "E2E-POLICY-0001",
  "planId": 9001,
  "insuredIdHash": "e2e-insured-hash-0001",
  "claimType": "OUTPATIENT",
  "outInsureTime": "2026-06-15T08:00:00Z",
  "items": [
    {"itemName": "门诊挂号费", "itemCode": "A001", "category": "SERVICE", "quantity": 1, "unitPrice": 50.00, "amount": 50.00},
    {"itemName": "药品费", "itemCode": "B002", "category": "DRUG", "quantity": 2, "unitPrice": 125.00, "amount": 250.00}
  ]
}'
CREATE=$(req POST "$GW/api/v1/claims" "${AUTH[@]}" \
  -H "Idempotency-Key: $IDEM_KEY" -d "$CLAIM_BODY")
CREATE_CODE=$(code_of "$CREATE")
CREATE_BODY=$(body_of "$CREATE")
check "POST /api/v1/claims（报案）" "$CREATE_CODE" "201"
CLAIM_ID=$(echo "$CREATE_BODY" | jget data.claimId)
CLAIM_NO=$(echo "$CREATE_BODY" | jget data.claimNo)
echo "  → claimId=$CLAIM_ID claimNo=$CLAIM_NO"
if [ -z "$CLAIM_ID" ]; then
  printf "  \033[31m✗ 未取得 claimId，中止\033[0m\n"; echo "  响应: $(echo "$CREATE_BODY" | head -c 400)"; exit 1
fi

# ---------- Step 4：幂等重放（同键同体 → 原响应） ----------
echo ""
echo "[Step 4] 幂等重放（同键同体）"
REPLAY=$(req POST "$GW/api/v1/claims" "${AUTH[@]}" \
  -H "Idempotency-Key: $IDEM_KEY" -d "$CLAIM_BODY")
REPLAY_CODE=$(code_of "$REPLAY")
REPLAY_BODY=$(body_of "$REPLAY")
check "同键同体重放被幂等接管" "$REPLAY_CODE" "200 201"
REPLAY_ID=$(echo "$REPLAY_BODY" | jget data.claimId)
if [ "$REPLAY_ID" = "$CLAIM_ID" ]; then
  PASS=$((PASS + 1)); printf "  \033[32m✓\033[0m 重放返回同一 claimId（${REPLAY_ID}）\n"
else
  FAIL=$((FAIL + 1)); FAILED_CASES+=("幂等重放 claimId 不一致 ($REPLAY_ID != $CLAIM_ID)")
  printf "  \033[31m✗\033[0m 重放 claimId 不一致（${REPLAY_ID} != ${CLAIM_ID}）\n"
fi

# ---------- Step 5：幂等冲突（同键异体 → 409） ----------
echo ""
echo "[Step 5] 幂等冲突（同键异体 → 409）"
CONFLICT_BODY=$(echo "$CLAIM_BODY" | python3 -c "import sys,json; d=json.load(sys.stdin); d['items'][0]['amount']=99.00; print(json.dumps(d))")
CONFLICT=$(req POST "$GW/api/v1/claims" "${AUTH[@]}" \
  -H "Idempotency-Key: $IDEM_KEY" -d "$CONFLICT_BODY")
check "同键异体返回 409 冲突" "$(code_of "$CONFLICT")" "409"

# ---------- Step 6：保单校验拒收（失效保单 → 422） ----------
echo ""
echo "[Step 6] 保单前置校验拒收"
EXPIRED_BODY=$(echo "$CLAIM_BODY" | python3 -c "import sys,json; d=json.load(sys.stdin); d['policyNo']='E2E-POLICY-EXPIRED'; d['planId']=9002; print(json.dumps(d))")
EXPIRED=$(req POST "$GW/api/v1/claims" "${AUTH[@]}" \
  -H "Idempotency-Key: e2e-$(uuidgen | tr 'A-Z' 'a-z')" -d "$EXPIRED_BODY")
check "失效保单报案被拒（422）" "$(code_of "$EXPIRED")" "422"
MISMATCH_BODY=$(echo "$CLAIM_BODY" | python3 -c "import sys,json; d=json.load(sys.stdin); d['insuredIdHash']='wrong-hash'; print(json.dumps(d))")
MISMATCH=$(req POST "$GW/api/v1/claims" "${AUTH[@]}" \
  -H "Idempotency-Key: e2e-$(uuidgen | tr 'A-Z' 'a-z')" -d "$MISMATCH_BODY")
check "被保人不匹配被拒（403）" "$(code_of "$MISMATCH")" "403 422"

# ---------- Step 7：详情 / 分页 / 防枚举 ----------
echo ""
echo "[Step 7] 查询链路"
DETAIL=$(req GET "$GW/api/v1/claims/$CLAIM_ID" "${AUTH[@]}")
check "GET /api/v1/claims/{id}（详情）" "$(code_of "$DETAIL")" "200"
DETAIL_BODY=$(body_of "$DETAIL")
ITEM_COUNT=$(echo "$DETAIL_BODY" | jlen data)
check "详情返回 2 条费用明细" "$ITEM_COUNT" "2"
# 分页默认口径是「工作台待办 = 待核赔 40」，刚报案的案件处于 ACCEPTED，故显式指定状态集；
# 不指定会拿到空列表（这是设计取舍，不是缺陷）。
PAGE=$(req GET "$GW/api/v1/claims?statuses=ACCEPTED&page=1&size=10" "${AUTH[@]}")
check "GET /api/v1/claims（分页）" "$(code_of "$PAGE")" "200"
if echo "$(body_of "$PAGE")" | grep -q "$CLAIM_NO"; then
  PASS=$((PASS + 1)); printf "  \033[32m✓\033[0m 分页结果包含新报案 $CLAIM_NO\n"
else
  FAIL=$((FAIL + 1)); FAILED_CASES+=("分页未包含 $CLAIM_NO"); printf "  \033[31m✗\033[0m 分页未包含 $CLAIM_NO\n"
fi
NOTFOUND=$(req GET "$GW/api/v1/claims/999999999999" "${AUTH[@]}")
check "不存在 ID 返回 404（防枚举）" "$(code_of "$NOTFOUND")" "404"

# ---------- Step 8：全状态机工作流 ----------
echo ""
echo "[Step 8] 理赔工作流（分配→初审→转人工→调赔→决策→复核→finished）"
# 工作流接口的操作人经请求头 X-Operator-Id 传入（部分接口另有 X-Approved-Amount /
# X-Approved / X-Hangup-* 必填头）；漏传会被 MVC 判为缺少请求头（应为 400）。
# 复核人必须不同于第一审核人（双人复核硬约束），故第二段换一个操作人。
OP1=(-H "X-Operator-Id: e2e-operator")
OP2=(-H "X-Operator-Id: e2e-reviewer2")
run_flow() {
  local name="$1" method="$2" path="$3" body="${4:-}"
  shift 4 2>/dev/null || true
  local resp
  if [ -n "$body" ]; then
    resp=$(req "$method" "$GW$path" "${AUTH[@]}" "$@" -d "$body")
  else
    resp=$(req "$method" "$GW$path" "${AUTH[@]}" "$@")
  fi
  check "$name" "$(code_of "$resp")" "200 204"
}
run_flow "allocate 分配初审"        POST "/api/v1/claims/$CLAIM_ID/allocate" "" "${OP1[@]}"
run_flow "pre-exam/start 开始初审"  POST "/api/v1/claims/$CLAIM_ID/pre-exam/start" "" "${OP1[@]}"
run_flow "pre-exam/complete 完成初审" POST "/api/v1/claims/$CLAIM_ID/pre-exam/complete" "" \
  "${OP1[@]}" -H "X-Approved-Amount: 300.00"
run_flow "escalate 转人工核赔"      POST "/api/v1/claims/$CLAIM_ID/escalate" "" "${OP1[@]}"
run_flow "adjust/claim 认领调赔"    POST "/api/v1/claims/$CLAIM_ID/adjust/claim" "" "${OP1[@]}"
run_flow "decision 人工核赔决策"    POST "/api/v1/claims/$CLAIM_ID/decision" \
  "{\"decision\":\"APPROVED\",\"reviewerId\":\"e2e-reviewer\",\"approvedAmount\":250.00}" "${OP1[@]}"
run_flow "second-review 提交复核"   POST "/api/v1/claims/$CLAIM_ID/second-review" "" "${OP2[@]}"
run_flow "second-review/complete 完成复核" POST "/api/v1/claims/$CLAIM_ID/second-review/complete" "" \
  "${OP2[@]}" -H "X-Approved: true"

FINAL=$(body_of "$(req GET "$GW/api/v1/claims/$CLAIM_ID" "${AUTH[@]}")")
FINAL_STATUS=$(echo "$FINAL" | jfirst data.0 status)
check "终态为 finished" "$FINAL_STATUS" "FINISHED finished"

# ---------- Step 9：CORS 预检（前端 bone-claim-app :3013） ----------
echo ""
echo "[Step 9] CORS 预检（Origin: http://localhost:3013）"
CORS=$(req OPTIONS "$GW/api/v1/claims" \
  -H "Origin: http://localhost:3013" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: Authorization,Content-Type,Idempotency-Key")
check "OPTIONS 预检" "$(code_of "$CORS")" "200 204"
CORS_ORIGIN=$(echo "$(req OPTIONS "$GW/api/v1/claims" \
  -H "Origin: http://localhost:3013" \
  -H "Access-Control-Request-Method: POST" -D - -o /dev/null)" | grep -i "access-control-allow-origin" | tr -d '\r' | awk '{print $2}')
if echo "$CORS_ORIGIN" | grep -q "3013"; then
  PASS=$((PASS + 1)); printf "  \033[32m✓\033[0m Allow-Origin 回显 3013（${CORS_ORIGIN}）\n"
else
  FAIL=$((FAIL + 1)); FAILED_CASES+=("CORS Allow-Origin 缺失/不含 3013（实际: ${CORS_ORIGIN:-空}）")
  printf "  \033[31m✗\033[0m Allow-Origin 不含 3013（实际: ${CORS_ORIGIN:-空}）\n"
fi

# ---------- 汇总 ----------
echo ""
echo "=========================================================="
TOTAL=$((PASS + FAIL))
if [ "$FAIL" -eq 0 ]; then
  printf " \033[32m全部通过：%d/%d\033[0m\n" "$PASS" "$TOTAL"
else
  printf " \033[31m通过 %d / %d，失败 %d 项：\033[0m\n" "$PASS" "$TOTAL" "$FAIL"
  for c in "${FAILED_CASES[@]}"; do echo "   - $c"; done
fi
echo "=========================================================="
exit $((FAIL > 0 ? 1 : 0))
