#!/usr/bin/env bash
# BONE 对外 UAT / 客户演示环境 —— 一键「清理 → 灌数 → 校验」编排器
#
# 用法:
#   BONE_ENV=uat ./load.sh                # 完整流程（默认 medium）
#   BONE_ENV=uat ./load.sh --scale large  # 放大体量
#   BONE_ENV=uat ./load.sh --skip-clean   # 跳过清理，仅重建演示数据
#   BONE_ENV=uat ./load.sh --validate-only
#
# 护栏：
#   * 必须显式声明 BONE_ENV，且禁止对 prod / production 执行
#   * 清理前必须存在全库逻辑备份（由 90_clean.py 自行校验）
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCALE="medium"
SKIP_CLEAN=0
VALIDATE_ONLY=0
for arg in "$@"; do
  case "$arg" in
    --scale)      shift_arg=1 ;;
    --skip-clean) SKIP_CLEAN=1 ;;
    --validate-only) VALIDATE_ONLY=1 ;;
    medium|large) SCALE="$arg" ;;
  esac
done
# 兼容 `--scale large`
if [[ "${1:-}" == "--scale" && -n "${2:-}" ]]; then SCALE="$2"; fi

echo "=============================================================="
echo " BONE UAT 演示数据编排器"
echo " 目录: $HERE   体量: $SCALE"
echo "=============================================================="

# ---------- 0. 环境护栏 ----------
if [[ -z "${BONE_ENV:-}" ]]; then
  echo "!! 拒绝执行：请先声明环境变量 BONE_ENV（uat / local）"
  echo "   例：BONE_ENV=uat ./load.sh"
  exit 2
fi
case "${BONE_ENV}" in
  prod|production|PRD)
    echo "!! 拒绝执行：严禁对生产环境执行清理/灌数"
    exit 2 ;;
esac
echo "[0] 环境守卫通过：BONE_ENV=${BONE_ENV}"

if [[ "$VALIDATE_ONLY" == "1" ]]; then
  python3 "$HERE/95_validate.py" --json "$HERE/../../outputs/uat/validate-report.json"
  exit $?
fi

# ---------- 1. 全库备份 ----------
BACKUP="$HERE/../../outputs/uat/bone_backup_$(date +%Y%m%d_%H%M%S).sql"
mkdir -p "$(dirname "$BACKUP")"
echo "[1] 备份当前库 -> $BACKUP"
python3 "$HERE/_dump.py" "$BACKUP"

# ---------- 2. 清理测试残留 ----------
if [[ "$SKIP_CLEAN" == "1" ]]; then
  echo "[2] 跳过清理（--skip-clean）"
else
  echo "[2] 清理测试残留（垃圾表 / E2E 租户 / 噪声账号）"
  python3 "$HERE/90_clean.py" --apply --purge-demo-tenant
fi

# ---------- 3. 生成并灌入演示数据 ----------
echo "[3] 生成确定性演示数据并灌库（scale=$SCALE）"
python3 "$HERE/gen.py" --scale "$SCALE" --apply

# ---------- 4. 校验门禁 ----------
echo "[4] 校验门禁（8 类断言）"
python3 "$HERE/95_validate.py" --json "$HERE/../../outputs/uat/validate-report.json"

echo "=============================================================="
echo " 完成。演示登录：tenant_admin / 123456（租户：鼎晖智造集团股份有限公司）"
echo "=============================================================="
