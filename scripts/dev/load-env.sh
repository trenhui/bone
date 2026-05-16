#!/usr/bin/env bash
# 从仓库根目录 .env 加载 BONE_* 环境变量（不覆盖已 export 的变量）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-${0}}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ENV_FILE="${BONE_ENV_FILE:-${REPO_ROOT}/.env}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "[load-env] 未找到 ${ENV_FILE}" >&2
  echo "[load-env] 请执行: cp .env.example .env 并填写 BONE_DB_PASSWORD" >&2
  return 1 2>/dev/null || exit 1
fi

# shellcheck disable=SC1090
set -a
source "${ENV_FILE}"
set +a

# 未显式设置 BONE_DB_URL 时由 HOST/PORT/NAME 拼装
if [[ -z "${BONE_DB_URL:-}" && -n "${BONE_DB_HOST:-}" ]]; then
  export BONE_DB_URL="jdbc:mysql://${BONE_DB_HOST}:${BONE_DB_PORT:-3306}/${BONE_DB_NAME:-bone}?useSSL=false&autoReconnect=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
fi

echo "[load-env] 已加载 ${ENV_FILE}"
