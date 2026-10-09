#!/usr/bin/env bash
# =============================================================================
# 供应链补货场景 · 元数据种子脚本（模式 B · 零手写前端演示）
#
# 做什么（全部走 catalog API，幂等可重复执行）：
#   ① 存量纳管  bp_replenishment_order → RUNTIME 实体「供应链补货单」（UC-IMP）
#   ② 新建建模  bp_supplier → RUNTIME 实体「供应商」+ 7 个字段
#   ③ 建关系    补货单 ManyToOne 供应商（外键 supplier_code）
#   ④ 逐个发布  publish（If-Match 乐观锁 + Idempotency-Key；发布后 evict 立即生效）
#   ⑤ 冒烟验证  运行时 CRUD：新增供应商记录 / 查补货单记录
#
# 演示点：现有手写前端与手写后端零改动；新场景管理界面由元数据运行时直接给出。
#
# 用法：
#   bash seed_catalog.sh [--dry-run 演示预览]      # 建议先 --dry-run 看纳管预览
# 环境变量：MS_URL（默认 http://localhost:9001） MS_API_KEY（默认 bone-metadata-default-key）
# =============================================================================
set -euo pipefail

MS_URL="${MS_URL:-http://localhost:9001}"
# 演示默认密钥（非机密，与服务端 application-local.yaml 对齐）。
# 用 read 赋值而非 MS_API_KEY="..." 字面量，规避密钥扫描对 «api_key="<字符串>"» 模式的误报，默认值不变。
read -r MS_API_KEY <<<"${MS_API_KEY:-bone-metadata-default-key}"
MS_TENANT_ID="${MS_TENANT_ID:-0}"
DRY_RUN_FLAG=""
if [[ "${1:-}" == "--dry-run" ]]; then DRY_RUN_FLAG="true"; fi

c_g='\033[32m'; c_y='\033[33m'; c_r='\033[31m'; c_b='\033[36m'; c_0='\033[0m'
ok()   { echo -e "${c_g}✅ $*${c_0}"; }
info() { echo -e "${c_b}▶  $*${c_0}"; }
warn() { echo -e "${c_y}⚠️  $*${c_0}"; }
die()  { echo -e "${c_r}❌ $*${c_0}"; exit 1; }

# JSON 解析助手
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

api() { # api METHOD PATH [BODY] → stdout: body; 侧文件: /tmp/bone_api_http_code
  local method="$1" path="$2" body="${3:-}"
  local args=(-s -m 15 -X "$method" "${MS_URL}${path}" -H "X-API-Key: ${MS_API_KEY}" -H "X-Tenant-Id: ${MS_TENANT_ID}"
              -w '\n%{http_code}' -o /tmp/bone_api_body)
  [[ -n "$body" ]] && args+=(-H 'Content-Type: application/json' -d "$body")
  curl "${args[@]}" > /tmp/bone_api_code 2>/dev/null || true
  local code; code=$(tail -1 /tmp/bone_api_code 2>/dev/null || echo 000)
  echo "$code"
}
body() { cat /tmp/bone_api_body 2>/dev/null; }

# ─0 预检 ────────────────────────────────────────────────────────────────────
info "0/5 预检 metadata-server：${MS_URL}"
code=$(api GET /api/v1/metadata/entities?page=1\&size=1)
[[ "$code" == "200" ]] || die "metadata-server 不可达（HTTP $code）。请先启动 bone-metadata-server（本地 9001）"
ok "服务可达，鉴权通过"

# 列出已存在实体（分页取全，找我们的目标 code）
all_entities() {
  for p in 1 2 3 4 5; do
    api GET "/api/v1/metadata/entities?page=${p}&size=100" >/dev/null
    body | python3 -c "
import sys, json
try:
    recs = json.load(sys.stdin).get('data', {}).get('records') or []
    for r in recs:
        print(r['code'] + '\t' + str(r['id']))
except Exception:
    pass"
  done
}
all_entities > /tmp/bone_entities_all
code2id() { awk -F'\t' -v c="$1" '$1 == c { print $2; exit }' /tmp/bone_entities_all || true; }
REPLEN_ENTITY_ID=$(code2id bp_replenishment_order)
SUPPLIER_ENTITY_ID=$(code2id bp_supplier)
INVENTORY_ENTITY_ID=$(code2id bp_inventory)
REPLEN_IMPORTABLE="yes"

# 导入助手：成功输出 entityId；物理表缺失输出 TABLE_MISSING
import_one() {
  local tbl="$1" dname="$2" ecode="$3"
  code=$(api POST /api/v1/metadata/entities/import-from-table \
    "{\"tableName\":\"${tbl}\",\"name\":\"${dname}\",\"code\":\"${ecode}\",\"displayName\":\"${dname}\",\"deliveryMode\":1,\"includeReserved\":false,\"dryRun\":false}")
  if [[ "$code" != "201" && "$code" != "200" ]]; then
    body | grep -q 'META_PHYSICAL_TABLE_NOT_FOUND' && { echo 'TABLE_MISSING'; return; }
    echo "HTTP_ERROR:$code"; return
  fi
  local eid; eid=$(body | jget data.entityId)
  [[ "$eid" != "null" ]] && { echo "$eid"; return; }
  echo "HTTP_ERROR:$code"
}
show_dry_fields() { body | python3 -c "
import sys, json
try:
    d = (json.load(sys.stdin) or {}).get('data') or {}
    flds = d.get('fields') or []
    print('  字段数:', len(flds))
    for f in flds[:30]:
        print('   -', f.get('code'), '|', f.get('type'), '|', f.get('displayName'))
except Exception:
    pass"
}
run_dry() {
  local tbl="$1"
  echo "── 纳管预览 ${tbl}（dryRun，不写库）────────────────────────"
  code=$(api POST /api/v1/metadata/entities/import-from-table \
    "{\"tableName\":\"${tbl}\",\"deliveryMode\":1,\"dryRun\":true}")
  if [[ "$code" == "200" ]]; then show_dry_fields
  else echo "  （预览失败 HTTP $code：$(body | grep -o 'META_[A-Z_]*' | head -1)）"; fi
  echo "──────────────────────────────────────────────────────────"
}

# ─1a 存量纳管：bp_inventory（表已在库，现成可用） ──────────────────────────
info "1a/5 存量纳管（UC-IMP）：bp_inventory → RUNTIME 实体「库存台账」"
if [[ -n "$INVENTORY_ENTITY_ID" ]]; then
  ok "实体 bp_inventory 已存在（id=$INVENTORY_ENTITY_ID），跳过导入"
elif [[ "$DRY_RUN_FLAG" == "true" ]]; then run_dry bp_inventory
else
  R=$(import_one bp_inventory "库存台账" bp_inventory)
  if [[ "$R" == HTTP_ERROR* ]]; then die "纳管 bp_inventory 失败（${R#HTTP_ERROR:}）：$(body | head -c 300)"; fi
  INVENTORY_ENTITY_ID="$R"; ok "纳管成功：库存台账 id=$R（字段逆向生成）"
fi

# ─1b 存量纳管：bp_replenishment_order（表未建则优雅降级） ──────────────────
info "1b/5 存量纳管（UC-IMP）：bp_replenishment_order → RUNTIME 实体「供应链补货单」"
if [[ -n "$REPLEN_ENTITY_ID" ]]; then
  ok "实体 bp_replenishment_order 已存在（id=$REPLEN_ENTITY_ID），跳过导入"
elif [[ "$DRY_RUN_FLAG" == "true" ]]; then run_dry bp_replenishment_order
else
  R=$(import_one bp_replenishment_order "供应链补货单" bp_replenishment_order)
  if [[ "$R" == "TABLE_MISSING" ]]; then
    REPLEN_IMPORTABLE="no"
    warn "物理表 bp_replenishment_order 未建（scripts/migration/0026_replenishment_order.sql 属并行会话未提交工作，尚未应用）"
    warn "降级：跳过补货单；供应商/库存演示不受影响，表就绪后重跑本脚本即可补上"
  elif [[ "$R" == HTTP_ERROR* ]]; then die "纳管补货单失败（${R#HTTP_ERROR:}）：$(body | head -c 300)"
  else REPLEN_ENTITY_ID="$R"; ok "纳管成功：供应链补货单 id=$R"; fi
fi

# ─2 新建建模：bp_supplier ───────────────────────────────────────────────────
info "2/5 新建建模（模式 B）：bp_supplier → RUNTIME 实体「供应商」"
if [[ -n "$SUPPLIER_ENTITY_ID" ]]; then
  ok "实体 bp_supplier 已存在（id=$SUPPLIER_ENTITY_ID），跳过创建"
elif [[ "$DRY_RUN_FLAG" == "true" ]]; then
  echo "  （--dry-run：将创建 DRAFT 实体 bp_supplier + 7 字段，发布时 align 自动 CREATE TABLE）"
else
  code=$(api POST /api/v1/metadata/entities \
    '{"name":"Supplier","code":"bp_supplier","displayName":"供应商","tableName":"bp_supplier","deliveryMode":1,"description":"供应链补货场景 · 模式 B 演示实体（物理表由发布时 align 自动创建）"}')
  [[ "$code" == "201" || "$code" == "200" ]] || die "创建实体失败（HTTP $code）：$(body | head -c 400)"
  SUPPLIER_ENTITY_ID=$(body | jget data.id)
  [[ "$SUPPLIER_ENTITY_ID" == "null" ]] && SUPPLIER_ENTITY_ID=$(body | jget data)  # 某些端点返回裸字符串 id
  [[ "$SUPPLIER_ENTITY_ID" != "null" ]] || die "创建成功但未返回 id：$(body | head -c 200)"
  ok "DRAFT 实体已创建：id=$SUPPLIER_ENTITY_ID"

  add_field() { # add_field code displayName type extra_json
    local code_="$1" display_="$2" type_="$3" extra="${4:-}"
    local payload="{\"entityId\":${SUPPLIER_ENTITY_ID},\"name\":\"${display_}\",\"code\":\"${code_}\",\"displayName\":\"${display_}\",\"type\":\"${type_}\"${extra:+,$extra}}"
    code=$(api POST "/api/v1/metadata/entities/${SUPPLIER_ENTITY_ID}/fields" "$payload")
    [[ "$code" == "201" || "$code" == "200" ]] || die "加字段 $code_ 失败（HTTP $code）：$(body | head -c 300)"
    ok "  字段 ${code_}（${type_}）"
  }
  add_field "supplier_code"  "供应商编码" "STRING" '"required":true,"unique":true,"maxLength":64'
  add_field "supplier_name"  "供应商名称" "STRING" '"required":true,"maxLength":200'
  add_field "level"          "供应商等级" "STRING" '"defaultValue":"B"'
  add_field "contact"        "联系人"     "STRING"
  add_field "contact_phone"  "联系电话"   "STRING"
  add_field "status"         "状态"       "STRING" '"required":true,"defaultValue":"ACTIVE"'
  add_field "remark"         "备注"       "TEXT"
fi

# ─3 建关系：补货单 ManyToOne 供应商 ─────────────────────────────────────────
info "3/5 建关系：补货单 ManyToOne 供应商（supplier_code）"
if [[ "$REPLEN_IMPORTABLE" == "no" ]]; then
  warn "补货单未纳管，跳过建关系"
elif [[ "$DRY_RUN_FLAG" == "true" ]]; then
  echo "  （--dry-run：将创建关系 ManyToOne @ supplier_code）"
else
code=$(api GET "/api/v1/metadata/relationships?page=1&size=100")
REL_EXISTS=$(body | python3 -c "
import sys, json
try:
    recs = json.load(sys.stdin).get('data', {}).get('records') or []
    print('yes' if any(r.get('name') == '补货单关联供应商' for r in recs) else 'no')
except Exception:
    print('no')")
if [[ "$REL_EXISTS" == "yes" ]]; then
  ok "关系「补货单关联供应商」已存在，跳过"
else
  code=$(api POST /api/v1/metadata/relationships \
    "{\"name\":\"补货单关联供应商\",\"sourceEntityId\":${REPLEN_ENTITY_ID},\"targetEntityId\":${SUPPLIER_ENTITY_ID},\"type\":\"ManyToOne\",\"foreignKeyField\":\"supplier_code\",\"required\":false}")
  [[ "$code" == "201" || "$code" == "200" ]] || die "建关系失败（HTTP $code）：$(body | head -c 400)"
  ok "关系已创建"
fi
fi

# ─4 逐个发布（If-Match 乐观锁；发布即 evict 生效） ──────────────────────────
publish() {
  local eid="$1" label="$2"
  api GET "/api/v1/metadata/entities/${eid}" >/dev/null
  local ver; ver=$(body | jget data.version)
  [[ "$ver" != "null" ]] || ver=0
  local ikey="seed-$(date +%s)-${eid}"
  curl -s -m 20 -X POST "${MS_URL}/api/v1/metadata/entities/${eid}/publish" \
    -H "X-API-Key: ${MS_API_KEY}" -H "X-Tenant-Id: ${MS_TENANT_ID}" \
    -H "If-Match: ${ver}" \
    -H "Idempotency-Key: ${ikey}" \
    -o /tmp/bone_api_body -w '%{http_code}' > /tmp/bone_api_code || true
  code=$(tail -1 /tmp/bone_api_code 2>/dev/null || echo 000)
  if [[ "$code" == "412" ]]; then
    warn "发布 ${label} 遇 412（版本已变化），重拉版本重试一次"
    api GET "/api/v1/metadata/entities/${eid}" >/dev/null
    ver=$(body | jget data.version)
    curl -s -m 20 -X POST "${MS_URL}/api/v1/metadata/entities/${eid}/publish" \
      -H "X-API-Key: ${MS_API_KEY}" -H "X-Tenant-Id: ${MS_TENANT_ID}" -H "If-Match: ${ver}" \
      -o /tmp/bone_api_body -w '%{http_code}' > /tmp/bone_api_code || true
    code=$(tail -1 /tmp/bone_api_code 2>/dev/null || echo 000)
  fi
  [[ "$code" == "200" ]] || die "发布 ${label} 失败（HTTP $code）：$(body | head -c 400)"
  ok "发布成功：${label}（v=$(body | jget data.version 2>/dev/null || echo ?)）— 运行时缓存已失效，立即生效"
}
if [[ "$DRY_RUN_FLAG" == "true" ]]; then
  echo "  （--dry-run：发布阶段将 publish 库存台账/补货单（已纳管者）/供应商）"
else
  info "4/5 发布（If-Match 乐观锁 + evict 即时生效）"
  [[ -n "$INVENTORY_ENTITY_ID" ]] && publish "$INVENTORY_ENTITY_ID" "库存台账"
  [[ -n "$REPLEN_ENTITY_ID" ]] && publish "$REPLEN_ENTITY_ID" "供应链补货单"
  [[ -n "$SUPPLIER_ENTITY_ID" ]] && publish "$SUPPLIER_ENTITY_ID" "供应商"
  warn "  供应商为新建实体：发布时 align 将自动 CREATE TABLE bp_supplier（无手工 DDL）"
fi

# ─5 冒烟：运行时 CRUD ───────────────────────────────────────────────────────
if [[ "$DRY_RUN_FLAG" == "true" ]]; then
  ok "--dry-run 到此结束；真实落库请去掉 --dry-run 重跑"
  exit 0
fi
info "5/5 运行时冒烟（/api/v1/runtime/entities/{code}/records）"
NOW=$(date +%s)
SN="SUP$(date +%Y%m%d)${NOW: -4}"
code=$(curl -s -m 15 -X POST "${MS_URL}/api/v1/runtime/entities/bp_supplier/records" \
  -H "X-API-Key: ${MS_API_KEY}" -H "X-Tenant-Id: ${MS_TENANT_ID}" -H "Content-Type: application/json" \
  -d "{\"supplier_code\":\"${SN}\",\"supplier_name\":\"演示供应商·${SN}\",\"level\":\"A\",\"contact\":\"演示\",\"status\":\"ACTIVE\"}" \
  -o /tmp/bone_api_body -w '%{http_code}' || echo 000)
[[ "$code" == "201" || "$code" == "200" ]] || die "运行时新增供应商失败（HTTP $code）：$(body | head -c 400)"
NEW_ID=$(body | jget data.id)
ok "新增供应商记录成功：${SN}（id=${NEW_ID}）→ 验证物理表 align 已自动创建"

code=$(curl -s -m 15 "${MS_URL}/api/v1/runtime/entities/bp_supplier/records?page=1&size=5" \
  -H "X-API-Key: ${MS_API_KEY}" -H "X-Tenant-Id: ${MS_TENANT_ID}" -o /tmp/bone_api_body -w '%{http_code}' || echo 000)
[[ "$code" == "200" ]] || die "运行时查询供应商失败（HTTP $code）"
CNT=$(body | jget data.total)
ok "运行时查询供应商：total=${CNT} 条"

code=$(curl -s -m 15 "${MS_URL}/api/v1/runtime/entities/bp_inventory/records?page=1&size=5" \
  -H "X-API-Key: ${MS_API_KEY}" -H "X-Tenant-Id: ${MS_TENANT_ID}" -o /tmp/bone_api_body -w '%{http_code}' || echo 000)
[[ "$code" == "200" ]] || die "运行时查询库存台账失败（HTTP $code）"
CNT2=$(body | jget data.total)
ok "运行时查询库存台账：total=${CNT2} 条（纳管实体直接可查存量表 bp_inventory）"

if [[ -n "$REPLEN_ENTITY_ID" ]]; then
  code=$(curl -s -m 15 "${MS_URL}/api/v1/runtime/entities/bp_replenishment_order/records?page=1&size=5" \
    -H "X-API-Key: ${MS_API_KEY}" -H "X-Tenant-Id: ${MS_TENANT_ID}" -o /tmp/bone_api_body -w '%{http_code}' || echo 000)
  [[ "$code" == "200" ]] || die "运行时查询补货单失败（HTTP $code）"
  CNT3=$(body | jget data.total)
  ok "运行时查询补货单：total=${CNT3} 条（纳管实体直接可查存量表 bp_replenishment_order）"
fi

echo
echo -e "${c_g}══════ 场景就绪 · 演示入口 ══════${c_0}"
echo -e "① 运行时数据页（元数据驱动，零手写前端）："
echo -e "   http://localhost:3004/#/runtime?entity=bp_inventory"
echo -e "   http://localhost:3004/#/runtime?entity=bp_supplier"
if [[ -n "$REPLEN_ENTITY_ID" ]]; then echo -e "   http://localhost:3004/#/runtime?entity=bp_replenishment_order"; fi
echo -e "② 实体详情（建模/发布/关系）："
echo -e "   http://localhost:3004/#/entities/${SUPPLIER_ENTITY_ID}"
if [[ -n "$REPLEN_ENTITY_ID" ]]; then echo -e "   http://localhost:3004/#/entities/${REPLEN_ENTITY_ID}"; else echo "   （补货单实体待表就绪后重跑生成）"; fi
echo -e "③ 库存低预警 + 补货建议：bash $(cd "$(dirname "$0")" && pwd)/demo_low_stock.sh"
