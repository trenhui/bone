#!/usr/bin/env bash
# =============================================================================
# synap-claim 理赔链路端到端联调（经网关 :8888 南北向入口，模拟人工操作）
#
# 覆盖：登录鉴权 → 报案（幂等创建/重放/冲突）→ 保单校验拒收 → 详情/分页/防枚举 404
#       → 全状态机工作流（分配→初审→转人工→调赔→决策→复核→ finished）
#       → 职责分离（双人复核：同一账号/伪造身份头都必须被拒）
#       → CORS 预检（bone-claim-app :3013 联调源）
#
# 前置：网关 :8888、synap-claim :8087（dev profile，DevPolicySnapshotSeeder 已灌测试保单）
#      复核人账号由脚本首次运行时经 IAM 自举（建部门+建账号+绑超级管理员角色），之后复用。
# 用法：bash scripts/verify-claim-e2e.sh
# =============================================================================
set -u

GW="${GW:-http://localhost:8888}"
CLAIM_DIRECT="${CLAIM_DIRECT:-http://localhost:8087}"
BONE_E2E_USER="${BONE_E2E_USER:-admin}"
BONE_E2E_PWD="${BONE_E2E_PWD:-123456}"

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
  -d "{\"username\":\"$BONE_E2E_USER\",\"password\":\"$BONE_E2E_PWD\"}")
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

# ---------- Step 1b：第二身份（复核人）----------
echo ""
echo "[Step 1b] 第二身份（复核人账号）"
# 操作人身份一律取自已验签 token（服务端不再接受 X-Operator-Id），
# 因此「双人复核」必须真的由两个账号完成——用一个账号伪造身份头的时代结束了。
REVIEWER_USER="${REVIEWER_USER:-e2ereviewer}"
REVIEWER_PASS="${REVIEWER_PASS:-Review@12345}"

login_req() { # login_req <user> <pass>
  req POST "$GW/api/v1/iam/login" -H "Content-Type: application/json" \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}"
}

REVIEWER_LOGIN=$(login_req "$REVIEWER_USER" "$REVIEWER_PASS")
if [ "$(code_of "$REVIEWER_LOGIN")" != "200" ]; then
  echo "  · 首次运行：自举复核人账号"
  DEPT_TREE=$(req GET "$GW/api/v1/iam/depts/tree" "${AUTH[@]}")
  DEPT_ID=$(body_of "$DEPT_TREE" | jfirst data.0 id)
  if [ -z "$DEPT_ID" ]; then
    DEPT_ID=$(body_of "$(req POST "$GW/api/v1/iam/depts" "${AUTH[@]}" \
      -d '{"name":"e2e-claim-review","parentId":null,"orderNo":1}')" | jget data)
  fi
  req POST "$GW/api/v1/iam/accounts" "${AUTH[@]}" \
    -d "{\"username\":\"$REVIEWER_USER\",\"password\":\"$REVIEWER_PASS\",\"email\":\"$REVIEWER_USER@bone.local\",\"realName\":\"E2E 复核人\",\"tenantId\":0,\"deptId\":$DEPT_ID,\"roleIds\":[1]}" > /dev/null
  REVIEWER_LOGIN=$(login_req "$REVIEWER_USER" "$REVIEWER_PASS")
fi
check "复核人账号登录（${REVIEWER_USER}）" "$(code_of "$REVIEWER_LOGIN")" "200"
TOKEN2=$(body_of "$REVIEWER_LOGIN" | jget data.token)
[ -z "$TOKEN2" ] && TOKEN2=$(body_of "$REVIEWER_LOGIN" | jget data.accessToken)
if [ -n "$TOKEN2" ] && [ "$TOKEN2" != "$TOKEN" ]; then
  PASS=$((PASS + 1)); printf "  \033[32m✓\033[0m 复核人持有独立 token（与第一审核人不同账号）\n"
else
  FAIL=$((FAIL + 1)); FAILED_CASES+=("未取得独立的复核人 token")
  printf "  \033[31m✗ 未取得独立复核人 token，双人复核用例将无法执行\033[0m\n"
fi
AUTH2=(-H "Authorization: Bearer $TOKEN2" -H "Content-Type: application/json")

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
# 操作人由服务端从 token 取（不再有 X-Operator-Id）：
#   AUTH  = admin           → 领取核赔任务者，即「第一审核人」
#   AUTH2 = 复核人账号      → 双人复核的第二个真实身份
# 仍在请求头里传的业务参数只剩 X-Approved-Amount / X-Approved / X-Hangup-*（它们不是身份）。
flow() { # flow <a|b> <名称> <method> <path> [body] [额外请求头...]
  local who="$1" name="$2" method="$3" path="$4"; shift 4
  local body="" resp
  if [ $# -gt 0 ] && { [ -z "${1:-}" ] || [ "${1:0:1}" = "{" ]; }; then body="$1"; shift; fi
  if [ "$who" = "b" ]; then
    if [ -n "$body" ]; then resp=$(req "$method" "$GW$path" "${AUTH2[@]}" "$@" -d "$body")
    else resp=$(req "$method" "$GW$path" "${AUTH2[@]}" "$@"); fi
  else
    if [ -n "$body" ]; then resp=$(req "$method" "$GW$path" "${AUTH[@]}" "$@" -d "$body")
    else resp=$(req "$method" "$GW$path" "${AUTH[@]}" "$@"); fi
  fi
  check "$name" "$(code_of "$resp")" "200 204"
}

flow a "allocate 分配初审"          POST "/api/v1/claims/$CLAIM_ID/allocate"
flow a "pre-exam/start 开始初审"    POST "/api/v1/claims/$CLAIM_ID/pre-exam/start"
flow a "pre-exam/complete 完成初审" POST "/api/v1/claims/$CLAIM_ID/pre-exam/complete" "" \
  -H "X-Approved-Amount: 300.00"
flow a "escalate 转人工核赔"        POST "/api/v1/claims/$CLAIM_ID/escalate"
flow a "adjust/claim 认领调赔（=第一审核人）" POST "/api/v1/claims/$CLAIM_ID/adjust/claim"
# 决策体里不再有 reviewerId：审核人就是 token 持有人
flow a "decision 人工核赔决策"      POST "/api/v1/claims/$CLAIM_ID/decision" \
  '{"decision":"APPROVED","approvedAmount":250.00}'

# 前置状态断言：SoD 断言必须在「已审核(ADJUSTED)」状态下成立，
# 否则状态冲突导致的 4xx 会被误读成「职责分离生效」（假绿）。
AFTER_DECISION=$(body_of "$(req GET "$GW/api/v1/claims/$CLAIM_ID" "${AUTH[@]}")")
check "决策后状态为 ADJUSTED（SoD 断言前置）" "$(echo "$AFTER_DECISION" | jfirst data.0 status)" "ADJUSTED"

# ---- 职责分离（SoD）回归：身份可伪造则双人复核形同虚设，故这四条是硬断言 ----
echo ""
echo "[Step 8.1] 职责分离（双人复核不可被绕过）"
SOD_SELF=$(req POST "$GW/api/v1/claims/$CLAIM_ID/second-review" "${AUTH[@]}")
check "同一账号自审自复核被拒（复核人=第一审核人）" "$(code_of "$SOD_SELF")" "400 409"
if echo "$(body_of "$SOD_SELF")" | grep -q "第一审核人"; then
  PASS=$((PASS + 1)); printf "  \033[32m✓\033[0m 拒绝理由为职责分离（非状态冲突）\n"
else
  FAIL=$((FAIL + 1)); FAILED_CASES+=("SoD 拒绝理由不含『第一审核人』: $(body_of "$SOD_SELF" | head -c 120)")
  printf "  \033[31m✗\033[0m SoD 拒绝理由异常\n"
fi
SOD_FORGED=$(req POST "$GW/api/v1/claims/$CLAIM_ID/second-review" "${AUTH[@]}" \
  -H "X-Operator-Id: e2e-forged-reviewer" -H "X-User-Id: e2e-forged-reviewer")
check "伪造 X-Operator-Id / X-User-Id 无法绕过 SoD" "$(code_of "$SOD_FORGED")" "400 409"
if echo "$(body_of "$SOD_FORGED")" | grep -q "第一审核人"; then
  PASS=$((PASS + 1)); printf "  \033[32m✓\033[0m 伪造身份头后仍判为职责分离冲突（头已失效）\n"
else
  FAIL=$((FAIL + 1)); FAILED_CASES+=("伪造头改变了判定结果: $(body_of "$SOD_FORGED" | head -c 120)")
  printf "  \033[31m✗\033[0m 伪造头改变了判定结果\n"
fi

flow b "second-review 复核人发起复核"  POST "/api/v1/claims/$CLAIM_ID/second-review"
# 登记复核人之后，第一审核人也不能替结论（复核人必须是登记的那位）
SOD_WRONG=$(req POST "$GW/api/v1/claims/$CLAIM_ID/second-review/complete" "${AUTH[@]}" \
  -H "X-Approved: true")
check "非登记复核人提交结论被拒" "$(code_of "$SOD_WRONG")" "400 409"
flow b "second-review/complete 复核人提交结论" \
  POST "/api/v1/claims/$CLAIM_ID/second-review/complete" "" -H "X-Approved: true"

FINAL=$(body_of "$(req GET "$GW/api/v1/claims/$CLAIM_ID" "${AUTH[@]}")")
FINAL_STATUS=$(echo "$FINAL" | jfirst data.0 status)
check "终态为 finished" "$FINAL_STATUS" "FINISHED finished"
SECOND_REVIEWER=$(echo "$FINAL" | jfirst data.0 secondReviewerId)
if [ -n "$SECOND_REVIEWER" ] && [ "$SECOND_REVIEWER" != "1" ]; then
  PASS=$((PASS + 1)); printf "  \033[32m✓\033[0m 落库复核人取自 token（userId=%s，非第一审核人 1）\n" "$SECOND_REVIEWER"
else
  FAIL=$((FAIL + 1)); FAILED_CASES+=("复核人未按 token 落库（secondReviewerId=${SECOND_REVIEWER:-空}）")
  printf "  \033[31m✗\033[0m 复核人落库异常（secondReviewerId=${SECOND_REVIEWER:-空}）\n"
fi

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
