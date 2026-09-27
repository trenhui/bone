#!/usr/bin/env bash
#
# repair-entity-names.sh — 存量 custom_entity_name 驼峰收敛（运维一次性修复）
#
# 对应后端端点：POST /api/v1/generator/admin/repair-entity-names
#   - 默认 execute=false 仅预览，不写库
#   - 带 {"execute":true} 才真正写入
#   - 幂等、可重复执行；仅修正 customEntityName == originalTableName 的存量行
#   - 用户已自定义的行不受影响
#
# 依赖：curl、python3（用于解析 JSON，无需 jq）
#
# 用法：
#   ./repair-entity-names.sh                 # 预览 → 人工确认 → 执行 → 复核
#   ./repair-entity-names.sh -y              # 预览后跳过确认直接执行（非交互环境）
#   ./repair-entity-names.sh -d ds-abc       # 仅针对单个数据源
#   ./repair-entity-names.sh -h 10.0.0.5 -p 8086
#
# 退出码：0 成功；1 缺少依赖(curl/python3)；2 预览为 0 无需修复；3 用户取消；4 复核未归零

set -euo pipefail

# ---------- 参数 ----------
HOST="${HOST:-localhost}"
PORT="${PORT:-8086}"
DS="${DS:-}"
AUTO_CONFIRM=0

while getopts ":h:p:d:y" opt; do
  case "$opt" in
    h) HOST="$OPTARG" ;;
    p) PORT="$OPTARG" ;;
    d) DS="$OPTARG" ;;
    y) AUTO_CONFIRM=1 ;;
    *) echo "未知参数: -$OPTARG" >&2; exit 1 ;;
  esac
done

BASE_URL="http://${HOST}:${PORT}/api/v1/generator/admin"
ENDPOINT="${BASE_URL}/repair-entity-names"

# ---------- 颜色 ----------
if [ -t 1 ]; then
  C_RESET=$'\033[0m'; C_RED=$'\033[31m'; C_GREEN=$'\033[32m'
  C_YELLOW=$'\033[33m'; C_CYAN=$'\033[36m'; C_BOLD=$'\033[1m'
else
  C_RESET=""; C_RED=""; C_GREEN=""; C_YELLOW=""; C_CYAN=""; C_BOLD=""
fi

info()  { echo "${C_CYAN}[i]${C_RESET} $*"; }
ok()    { echo "${C_GREEN}[✓]${C_RESET} $*"; }
warn()  { echo "${C_YELLOW}[!]${C_RESET} $*"; }
err()   { echo "${C_RED}[✗]${C_RESET} $*" >&2; }
ask()   { echo "${C_BOLD}$*${C_RESET}"; }

# ---------- 依赖检查 ----------
for bin in curl python3; do
  if ! command -v "$bin" >/dev/null 2>&1; then
    err "缺少依赖: $bin，请先安装"
    exit 1
  fi
done

# ---------- JSON 解析（python3，无需 jq） ----------
get_field() { # $1=response, $2=python 取值表达式
  printf '%s' "$1" | python3 -c "import sys,json; print(json.load(sys.stdin)$2)"
}

print_examples() {
  printf '%s' "$1" | python3 -c '
import sys, json
ex = json.load(sys.stdin)["data"]["examples"][:30]
print("  id   dataSourceId        originalTable        oldEntityName  ->  newEntityName")
print("  --------------------------------------------------------------------------------")
for e in ex:
    print("  {id}  {ds}  {ot}  {old}  ->  {new}".format(
        id=str(e.get("id", "-")),
        ds=str(e.get("dataSourceId", "-")),
        ot=str(e.get("originalTableName", "-")),
        old=str(e.get("oldEntityName", "-")),
        new=str(e.get("newEntityName", "-"))))
'
}

# ---------- 构造请求体 ----------
build_body() {
  local execute="$1"
  if [ -n "$DS" ]; then
    if [ "$execute" = "true" ]; then
      echo "{\"execute\":true,\"dataSourceId\":\"${DS}\"}"
    else
      echo "{\"dataSourceId\":\"${DS}\"}"
    fi
  else
    if [ "$execute" = "true" ]; then
      echo "{\"execute\":true}"
    else
      echo "{}"
    fi
  fi
}

call() {
  curl -s -X POST "$ENDPOINT" \
    -H 'Content-Type: application/json' \
    -d "$1"
}

# ---------- 步骤 1：预览 ----------
info "预览模式（不写库）请求 ${ENDPOINT}"
PREVIEW_BODY="$(build_body false)"
PREVIEW_RESP="$(call "$PREVIEW_BODY")"

CODE="$(get_field "$PREVIEW_RESP" '["code"]')"
AFFECTED="$(get_field "$PREVIEW_RESP" '["data"]["affectedCount"]')"

if [ "$CODE" != "0" ]; then
  err "端点返回非成功状态码: code=$CODE"
  printf '%s\n' "$PREVIEW_RESP"
  exit 1
fi

echo
ask "将修复 ${C_BOLD}${AFFECTED}${C_RESET} 行 custom_entity_name（仅 customEntityName == originalTableName 的存量行）："
print_examples "$PREVIEW_RESP"
echo

if [ "$AFFECTED" = "0" ] || [ "$AFFECTED" = "null" ] || [ -z "$AFFECTED" ]; then
  warn "affectedCount=${AFFECTED}，没有需要修复的行，结束。"
  exit 2
fi

# ---------- 步骤 2：确认 ----------
if [ "$AUTO_CONFIRM" -eq 1 ]; then
  ok "已指定 -y，跳过交互确认，直接执行。"
else
  ask "确认执行以上修复？[y/N]"
  read -r REPLY
  if [ "${REPLY,,}" != "y" ] && [ "${REPLY,,}" != "yes" ]; then
    warn "已取消，未做任何修改。"
    exit 3
  fi
fi

# ---------- 步骤 3：执行 ----------
info "执行修复（写入库）..."
EXEC_BODY="$(build_body true)"
EXEC_RESP="$(call "$EXEC_BODY")"
EXEC_AFFECTED="$(get_field "$EXEC_RESP" '["data"]["affectedCount"]')"
EXECUTED="$(get_field "$EXEC_RESP" '["data"]["executed"]')"

if [ "$EXECUTED" != "True" ] && [ "$EXECUTED" != "true" ]; then
  err "执行未生效（executed=false），请检查日志。"
  printf '%s\n' "$EXEC_RESP"
  exit 1
fi
ok "已修复 ${EXEC_AFFECTED} 行。"

# ---------- 步骤 4：复核 ----------
info "复核：重新预览，确认幂等归零..."
REVIEW_RESP="$(call "$PREVIEW_BODY")"
REVIEW_AFFECTED="$(get_field "$REVIEW_RESP" '["data"]["affectedCount"]')"

if [ "$REVIEW_AFFECTED" = "0" ]; then
  ok "复核通过：剩余待修复行 = 0，操作幂等且完整。"
  exit 0
else
  err "复核异常：仍有 ${REVIEW_AFFECTED} 行未修复（预期 0），请检查日志。"
  exit 4
fi
