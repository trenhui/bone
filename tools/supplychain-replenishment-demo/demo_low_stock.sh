#!/usr/bin/env bash
# =============================================================================
# 供应链补货场景 · 手写域演示：库存低预警（blueprint API）
#
# 叙事：**复杂业务留在手写后端**（低库存判定、补货建议、补货单状态机），
#       **管理界面由元数据运行时给出**（seed_catalog.sh 已把补货单/供应商纳管）。
#       —— 手写域与元数据运行时各管一段，构成"双模式协作"演示。
#
# 步骤：
#   ① IAM 登录取 token（blueprint 鉴权：Bearer）
#   ② 抬高商品 900001 的安全库存，使其"可用库存 ≤ 安全库存"触发 lowStock
#   ③ 查询低库存清单（InventoryController.aggregated / page 的 lowStock 标记）
#   ④ 若 blueprint 补货 API 已就绪则直接生成补货单；否则提示走运行时页创建
#
# 环境变量：BP_URL（默认 http://localhost:8082） IAM_URL（默认 http://localhost:8081）
# =============================================================================
set -euo pipefail

BP_URL="${BP_URL:-http://localhost:8082}"
IAM_URL="${IAM_URL:-http://localhost:8081}"
PRODUCT_ID="${PRODUCT_ID:-900001}"

c_g='\033[32m'; c_y='\033[33m'; c_r='\033[31m'; c_b='\033[36m'; c_0='\033[0m'
ok()   { echo -e "${c_g}✅ $*${c_0}"; }
info() { echo -e "${c_b}▶  $*${c_0}"; }
warn() { echo -e "${c_y}⚠️  $*${c_0}"; }
die()  { echo -e "${c_r}❌ $*${c_0}"; exit 1; }

jget() { python3 -c "
import sys, json
d = json.load(sys.stdin)
node = d
try:
    for k in '$1'.split('.'):
        node = node[k] if node is not None else None
except Exception:
    node = None
print('null' if node is None else node)
"; }

# ─① IAM 登录 ────────────────────────────────────────────────────────────────
info "① IAM 登录 ${IAM_URL}"
code=$(curl -s -m 8 -X POST "${IAM_URL}/api/v1/iam/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}' \
  -o /tmp/bone_iam_body -w '%{http_code}' || echo 000)
[[ "$code" == "200" ]] || die "IAM 登录失败（HTTP $code）：$(head -c 200 /tmp/bone_iam_body)"
[[ "$TOKEN" != "null" && -n "$TOKEN" ]] || die "未取到 token"
echo "$TOKEN" > /tmp/bone_token.txt
ok "token 获取成功（已存 /tmp/bone_token.txt；admin=平台租户 0）"

AUTH="Authorization: Bearer ${TOKEN}"

# ─② 当前库存 ────────────────────────────────────────────────────────────────
info "② 查询当前库存（GET /api/v1/inventories）"
code=$(curl -s -m 8 "${BP_URL}/api/v1/inventories?page=1&pageSize=10" \
  -H "$AUTH" -o /tmp/bone_inv_body -w '%{http_code}' || echo 000)
[[ "$code" == "200" ]] || die "查询库存失败（HTTP $code）：$(head -c 300 /tmp/bone_inv_body)"
echo "── 当前库存 ──────────────────────────────────"
python3 - <<'EOF'
import json
recs = (json.load(open('/tmp/bone_inv_body')).get('data') or {}).get('records') or []
for r in recs:
    mark = '🔴 lowStock' if r.get('lowStock') else '  OK'
    print(f"  {r['productId']} {r.get('productName',''):12s} 可用={r['availableQty']:>6} 安全={r['safetyStock']:>6} {mark}")
EOF
echo "──────────────────────────────────────────────"

# ─③ 抬高安全库存触发低库存 ─────────────────────────────────────────────────
info "③ 设置商品 ${PRODUCT_ID} 安全库存（POST /api/v1/inventories/safety-stock）"
code=$(curl -s -m 8 -X POST "${BP_URL}/api/v1/inventories/safety-stock" \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"productId\":${PRODUCT_ID},\"quantity\":10000}" \
  -o /tmp/bone_ss_body -w '%{http_code}' || echo 000)
if [[ "$code" != "200" ]]; then
  warn "设置安全库存失败（HTTP $code）：$(head -c 300 /tmp/bone_ss_body)"
  warn "（商品 ${PRODUCT_ID} 可能不存在；先在渠道商品/库存页创建，或改 PRODUCT_ID 环境变量）"
else
  ok "安全库存已抬高 → lowStock 应为 true"
fi

info "④ 再次查询库存（应见 🔴 lowStock）"
code=$(curl -s -m 8 "${BP_URL}/api/v1/inventories?page=1&pageSize=10" \
  -H "$AUTH" -o /tmp/bone_inv2_body -w '%{http_code}' || echo 000)
python3 - <<'EOF'
import json
try:
    recs = (json.load(open('/tmp/bone_inv2_body')).get('data') or {}).get('records') or []
    for r in recs:
        mark = '🔴 lowStock' if r.get('lowStock') else '  OK'
        print(f"  {r['productId']} {r.get('productName',''):12s} 可用={r['availableQty']:>6} 安全={r['safetyStock']:>6} {mark}")
except Exception as e:
    print('  (解析失败)', e)
EOF

# ─⑤ 补货 API 就绪探测（该切片可能正在并发开发中） ──────────────────────────
info "⑤ 探测 blueprint 补货 API 是否就绪"
REPLEN_PATHS=(
  "/api/v1/replenishment-orders/suggestions?page=1&size=5"
  "/api/v1/replenishments/suggestions?page=1&size=5"
)
API_READY="no"
for p in "${REPLEN_PATHS[@]}"; do
  code=$(curl -s -m 6 "${BP_URL}${p}" -H "$AUTH" -o /tmp/bone_rp_body -w '%{http_code}' || echo 000)
  if [[ "$code" == "200" ]]; then
    echo "  ${p} → 200"
    python3 - <<'EOF'
import json
try:
    d = json.load(open('/tmp/bone_rp_body')).get('data')
    recs = (d or {}).get('records') if isinstance(d, dict) else d
    recs = recs or []
    print(f"  补货建议 {len(recs)} 条：")
    for r in recs[:5]:
        print('   -', r.get('productId'), r.get('productName'), '缺口建议量', r.get('suggestedQty'))
except Exception as e:
    print('  (解析失败)', e)
EOF
    API_READY="yes"
    break
  fi
done
if [[ "$API_READY" != "yes" ]]; then
  warn "补货 API 暂不可用（blueprint 该切片可能处于并发开发中，工作区有未提交变更）"
  echo
  echo -e "${c_y}  应对：补货单不改手写前端，直接在元数据运行时页创建/维护：${c_0}"
  echo -e "     http://localhost:3004/runtime?entity=bp_replenishment_order"
  echo -e "  （status 取值：DRAFT/SUBMITTED/APPROVED/RECEIVED/CANCELLED，"
  echo -e "    低库存判断留在手写域 —— 双模式协作演示成立）"
fi

echo
echo -e "${c_g}══════ 演示收尾 ══════${c_0}"
echo -e "恢复原安全库存（可选）："
echo -e "  curl -X POST ${BP_URL}/api/v1/inventories/safety-stock -H \"Authorization: Bearer \$(cat /tmp/bone_token.txt)\" \\"
echo -e "       -H 'Content-Type: application/json' -d '{\"productId\":${PRODUCT_ID},\"quantity\":20}'"
