#!/usr/bin/env bash
# =============================================================================
# synap-claim A7 资金链端到端验证（软占 → 大额两次认证 → 支付 → 额度确认）
#
# 覆盖：
#   建单（SOFT_RESERVED，软占额度）
#   → 大额审核通过（≥1 万进入 REVIEW_FREEZE，落库审核发起人）
#   → 冻结态直接支付被拒（第二道认证不可跳过）
#   → 同一账号自复核被拒（双人复核不可退化为自我声明）
#   → 换真实第二身份复核放行（REVIEW_FREEZE → HARD_RESERVED）
#   → 支付（PAID，额度 frozen → claimed）
#   → 库侧台账：review_first_approver + 追加式资金流水（两次认证各一条）
#
# 为什么要单独一个脚本：主 E2E（verify-claim-e2e.sh）是纯 HTTP 的，而资金链的前置
# （理算结果 + 个人额度账户）在一期没有对外写接口（理算要证据锁定 + 责任配置，额度账户
# 由保单同步灌入）。本脚本用最小 SQL 夹具补齐这两个前置，只走 HTTP 打被测逻辑，
# 因此限定 dev 库使用。
#
# 前置：网关 :8888、synap-claim :8087（dev profile）、MySQL 可连（读 .env）
# 用法：bash scripts/verify-claim-funding-e2e.sh
#       CLAIM_ID=<已结案案件ID> bash scripts/verify-claim-funding-e2e.sh
# =============================================================================
set -uo pipefail

GW="${GW:-http://localhost:8888}"
ENV_FILE="${ENV_FILE:-.env}"
DB_HOST=$(grep '^BONE_DB_HOST=' "$ENV_FILE" 2>/dev/null | cut -d= -f2-)
DB_PORT=$(grep '^BONE_DB_PORT=' "$ENV_FILE" 2>/dev/null | cut -d= -f2-)
DB_USER=$(grep '^BONE_DB_USERNAME=' "$ENV_FILE" 2>/dev/null | cut -d= -f2-)
DB_PASS=$(grep '^BONE_DB_PASSWORD=' "$ENV_FILE" 2>/dev/null | cut -d= -f2-)
DB_NAME=$(grep '^BONE_DB_NAME=' "$ENV_FILE" 2>/dev/null | cut -d= -f2-)
MYSQL_BIN="${MYSQL_BIN:-}"
if [ -z "$MYSQL_BIN" ]; then
  for c in mysql /usr/local/mysql/bin/mysql /opt/homebrew/bin/mysql; do
    if command -v "$c" > /dev/null 2>&1 || [ -x "$c" ]; then MYSQL_BIN="$c"; break; fi
  done
fi
[ -z "$MYSQL_BIN" ] && { echo "未找到 mysql 客户端（可用 MYSQL_BIN 指定）"; exit 1; }

sql() { "$MYSQL_BIN" -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -N -e "$1" 2>/dev/null; }

req() { local m="$1" u="$2"; shift 2; curl -s --noproxy '*' -X "$m" "$u" "$@" -w "\n%{http_code}"; }
code_of() { echo "$1" | tail -1; }
body_of() { echo "$1" | sed '$d'; }
login() {
  req POST "$GW/api/v1/iam/login" -H "Content-Type: application/json" \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}"
}
jget() {
  python3 -c "import sys,json
d=json.load(sys.stdin)
for k in '$1'.split('.'):
    d = d[int(k)] if isinstance(d, list) else d.get(k)
print(d if d is not None else '')" 2>/dev/null
}
jfirst() {
  python3 -c "import sys,json
d=json.load(sys.stdin)
for k in '$1'.split('.'):
    d = d[int(k)] if isinstance(d, list) else d.get(k)
print((d or {}).get('$2', ''))" 2>/dev/null
}

PASS=0
FAIL=0
FAILED_CASES=()
check() { # check <名称> <实际> <期望码(空格分隔多个)>
  local name="$1" actual="$2" expect="$3"
  if [[ " $expect " == *" $actual "* ]]; then
    PASS=$((PASS + 1)); printf "  \033[32m✓\033[0m %s  [%s]\n" "$name" "$actual"
  else
    FAIL=$((FAIL + 1)); FAILED_CASES+=("$name (实际=$actual 期望=$expect)")
    printf "  \033[31m✗\033[0m %s 实际=%s 期望=%s\n" "$name" "$actual" "$expect"
  fi
}
has() { # has <名称> <文本> <期望包含>
  if echo "$2" | grep -q "$3"; then
    PASS=$((PASS + 1)); printf "  \033[32m✓\033[0m %s\n" "$1"
  else
    FAIL=$((FAIL + 1)); FAILED_CASES+=("$1（未包含『$3』）")
    printf "  \033[31m✗\033[0m %s（未包含『%s』）\n" "$1" "$3"
  fi
}

echo "=========================================================="
echo " synap-claim A7 资金链 E2E（网关 ${GW}）"
echo " $(date '+%Y-%m-%d %H:%M:%S')"
echo "=========================================================="

# ---------- 身份：赔案核赔人 / 第二复核人 / 财务审核人 ----------
# 三个身份缺一不可：核赔人定价、财务审核人发起放款、另一个人复核放行。
# 资金侧的两次认证都不接受请求体里的名单，服务端只认 token。
echo ""
echo "[Step 1] 三个真实身份"
TA=$(body_of "$(login admin 123456)" | jget data.token)
check "admin（赔案核赔人）登录" "$([ -n "$TA" ] && echo 200 || echo 000)" "200"

REVIEWER_USER="${REVIEWER_USER:-e2ereviewer}"
REVIEWER_PASS="${REVIEWER_PASS:-Review@12345}"
TB=$(body_of "$(login "$REVIEWER_USER" "$REVIEWER_PASS")" | jget data.token)
check "复核人登录（${REVIEWER_USER}）" "$([ -n "$TB" ] && echo 200 || echo 000)" "200"

FIN_USER="${FIN_USER:-e2efinance}"
FIN_PASS="${FIN_PASS:-Fin@123456}"
TC=$(body_of "$(login "$FIN_USER" "$FIN_PASS")" | jget data.token)
if [ -z "$TC" ]; then
  echo "  · 首次运行：自举财务审核人账号"
  AUTH_SEED=(-H "Authorization: Bearer $TA" -H "Content-Type: application/json")
  DEPT_ID=$(body_of "$(req GET "$GW/api/v1/iam/depts/tree" "${AUTH_SEED[@]}")" | jfirst data.0 id)
  [ -z "$DEPT_ID" ] && DEPT_ID=$(body_of "$(req POST "$GW/api/v1/iam/depts" "${AUTH_SEED[@]}" \
    -d '{"name":"e2e-claim-fund","parentId":null,"orderNo":1}')" | jget data)
  req POST "$GW/api/v1/iam/accounts" "${AUTH_SEED[@]}" \
    -d "{\"username\":\"$FIN_USER\",\"password\":\"$FIN_PASS\",\"email\":\"$FIN_USER@bone.local\",\"realName\":\"E2E 财务审核\",\"tenantId\":0,\"deptId\":$DEPT_ID,\"roleIds\":[1]}" > /dev/null
  TC=$(body_of "$(login "$FIN_USER" "$FIN_PASS")" | jget data.token)
fi
check "财务审核人登录（${FIN_USER}）" "$([ -n "$TC" ] && echo 200 || echo 000)" "200"
[ -z "$TA" ] || [ -z "$TB" ] || [ -z "$TC" ] && { echo "身份不足，终止"; exit 1; }
AUTH=(-H "Authorization: Bearer $TA" -H "Content-Type: application/json")
AUTH2=(-H "Authorization: Bearer $TB" -H "Content-Type: application/json")
AUTH3=(-H "Authorization: Bearer $TC" -H "Content-Type: application/json")

# ---------- 载体：最近一个已结案赔案 ----------
echo ""
echo "[Step 2] 载体案件与资金前置夹具（dev 库）"
if [ -n "${CLAIM_ID:-}" ]; then
  ROW=$(sql "SELECT id, claim_no, tenant_id, insured_id_hash, policy_no, plan_id FROM t_claim_case WHERE id=$CLAIM_ID;")
else
  ROW=$(sql "SELECT id, claim_no, tenant_id, insured_id_hash, policy_no, plan_id FROM t_claim_case WHERE status='FINISHED' ORDER BY created_at DESC LIMIT 1;")
fi
CLAIM_ID=$(echo "$ROW" | awk '{print $1}')
CLAIM_NO=$(echo "$ROW" | awk '{print $2}')
TENANT=$(echo "$ROW" | awk '{print $3}')
INSH=$(echo "$ROW" | awk '{print $4}')
POLICY=$(echo "$ROW" | awk '{print $5}')
PLAN=$(echo "$ROW" | awk '{print $6}')
if [ -z "$CLAIM_ID" ]; then
  echo "  ✗ 无已结案案件可作载体（先跑 scripts/verify-claim-e2e.sh）"
  exit 1
fi
printf "  · 案件 %s（id=%s tenant=%s policy=%s plan=%s）\n" "$CLAIM_NO" "$CLAIM_ID" "$TENANT" "$POLICY" "$PLAN"

# 夹具：理算结果 15000（≥1 万走双人复核）+ 个人额度账户；并清掉本案件历史资金态，保证可重复跑。
"$MYSQL_BIN" -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "
DELETE FROM settle_funding_event WHERE claim_id=$CLAIM_ID;
DELETE FROM settle_order WHERE claim_id=$CLAIM_ID;
DELETE FROM t_quota_transaction WHERE claim_id=$CLAIM_ID;
DELETE FROM t_adj_calculation WHERE claim_id=$CLAIM_ID;
DELETE FROM t_adj_calculation WHERE id=900000091;
INSERT INTO t_adj_calculation (id, claim_id, liability_uuid, liability_version, rule_version, formula_snapshot, formula_hash, total_amount, result_status, tenant_id, created_at, updated_at)
VALUES (900000091, $CLAIM_ID, 'e2e-liability', 'v1', 'v1', '{\"e2e\":true}', REPEAT('a',64), 15000.00, 'CONFIRMED', $TENANT, NOW(3), NOW(3));
INSERT INTO t_quota_account (id, insured_id_hash, policy_no, plan_id, quota_year, quota_type, liability_uuid, quota_initial, quota_claimed, quota_frozen, tenant_id, created_at, updated_at)
VALUES (900000092, '$INSH', '$POLICY', $PLAN, YEAR(NOW()), 'PERSONAL', '', 200000.00, 0, 0, $TENANT, NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE quota_initial=200000.00, quota_claimed=0, quota_frozen=0;" 2>&1 | grep -v "Using a password" || true
check "夹具就绪（理算 15000 + 额度 200000）" "$(sql "SELECT COUNT(*) FROM t_adj_calculation WHERE claim_id=$CLAIM_ID;")" "1"

# ---------- Step 3：建单（软占） ----------
echo ""
echo "[Step 3] 建结算单（软占额度）"
R=$(req POST "$GW/api/v1/claims/$CLAIM_ID/settlement" "${AUTH3[@]}" -d '{"attempt":1,"accountType":"PERSONAL"}')
check "POST /settlement" "$(code_of "$R")" "201"
check "初始阶段 SOFT_RESERVED" "$(body_of "$R" | jget data.fundingStage)" "SOFT_RESERVED"
check "软占落额度 frozen" "$(sql "SELECT quota_frozen FROM t_quota_account WHERE policy_no='$POLICY' AND plan_id=$PLAN;")" "15000.00"

# ---------- Step 4：大额审核通过（第一道认证） ----------
echo ""
echo "[Step 4] 审核通过（第一道认证，财务审核人）"
R=$(req POST "$GW/api/v1/claims/$CLAIM_ID/settlement/audit-pass" "${AUTH3[@]}")
check "POST /settlement/audit-pass" "$(code_of "$R")" "200"
check "大额进入 REVIEW_FREEZE" "$(body_of "$R" | jget data.fundingStage)" "REVIEW_FREEZE"
check "审核发起人落库（token userId）" \
  "$(sql "SELECT IF(review_first_approver IS NULL,'NULL','SET') FROM settle_order WHERE claim_id=$CLAIM_ID;")" "SET"

# ---------- Step 5：第二道认证不可跳过 ----------
echo ""
echo "[Step 5] 冻结态直接支付 / 自复核 → 均被拒"
R=$(req POST "$GW/api/v1/claims/$CLAIM_ID/settlement/pay" "${AUTH3[@]}")
check "冻结态支付被拒" "$(code_of "$R")" "400 409 422"
has "拒付理由指向复核" "$(body_of "$R")" "复核"

R=$(req POST "$GW/api/v1/claims/$CLAIM_ID/settlement/review-confirm" "${AUTH3[@]}")
check "同一账号自复核被拒" "$(code_of "$R")" "400 409 422"
has "拒因为『审核的发起人』" "$(body_of "$R")" "审核的发起人"

# 赔案核赔人（第一审核人）也不得充当资金复核人
R=$(req POST "$GW/api/v1/claims/$CLAIM_ID/settlement/review-confirm" "${AUTH[@]}")
check "赔案核赔人复核被拒" "$(code_of "$R")" "400 409 422"
has "拒因为『第一审核人』" "$(body_of "$R")" "第一审核人"

# ---------- Step 6：换真实第二身份复核放行 ----------
echo ""
echo "[Step 6] 第二身份复核放行（REVIEW_FREEZE → HARD_RESERVED）"
R=$(req POST "$GW/api/v1/claims/$CLAIM_ID/settlement/review-confirm" "${AUTH2[@]}")
check "POST /settlement/review-confirm" "$(code_of "$R")" "200"
check "阶段 HARD_RESERVED" "$(body_of "$R" | jget data.fundingStage)" "HARD_RESERVED"

# ---------- Step 7：支付 ----------
echo ""
echo "[Step 7] 支付（PAID）：额度 frozen → claimed"
R=$(req POST "$GW/api/v1/claims/$CLAIM_ID/settlement/pay" "${AUTH2[@]}")
check "POST /settlement/pay" "$(code_of "$R")" "200"
check "阶段 PAID" "$(body_of "$R" | jget data.fundingStage)" "PAID"
check "额度已确认扣减（claimed=15000, frozen=0）" \
  "$(sql "SELECT CONCAT(quota_claimed,'/',quota_frozen) FROM t_quota_account WHERE policy_no='$POLICY' AND plan_id=$PLAN;")" \
  "15000.00/0.00"

# ---------- Step 8：台账（两次认证各一条流水） ----------
echo ""
echo "[Step 8] 资金台账（追加式流水 + 审核发起人）"
EV=$(sql "SELECT GROUP_CONCAT(CONCAT(stage,':',operator) ORDER BY id) FROM settle_funding_event WHERE claim_id=$CLAIM_ID;")
echo "  · $EV"
has "流水含 SOFT_RESERVED/REVIEW_FREEZE/HARD_RESERVED/PAID" "$EV" "PAID"
has "冻结态由审核发起人产生" "$EV" "REVIEW_FREEZE"
has "放行态由另一身份产生" "$EV" "HARD_RESERVED"
FIRST_APPROVER=$(sql "SELECT review_first_approver FROM settle_order WHERE claim_id=$CLAIM_ID;")
SECOND_APPROVER=$(sql "SELECT operator FROM settle_funding_event WHERE claim_id=$CLAIM_ID AND stage='HARD_RESERVED';")
if [ -n "$FIRST_APPROVER" ] && [ -n "$SECOND_APPROVER" ] && [ "$FIRST_APPROVER" != "$SECOND_APPROVER" ]; then
  PASS=$((PASS + 1)); printf "  \033[32m✓\033[0m 两次认证为两个不同身份（%s / %s）\n" "$FIRST_APPROVER" "$SECOND_APPROVER"
else
  FAIL=$((FAIL + 1)); FAILED_CASES+=("两次认证身份相同或缺失（${FIRST_APPROVER:-空} / ${SECOND_APPROVER:-空}）")
  printf "  \033[31m✗\033[0m 两次认证身份相同或缺失\n"
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
