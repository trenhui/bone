#!/usr/bin/env bash
# 校验 MySQL 连通性并对比 bone-init.sql 中声明的表
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=load-env.sh
source "${SCRIPT_DIR}/load-env.sh"

MYSQL_BIN="${MYSQL_BIN:-mysql}"
if ! command -v "${MYSQL_BIN}" >/dev/null 2>&1; then
  for candidate in /usr/local/mysql/bin/mysql /opt/homebrew/bin/mysql mysql; do
    if [[ -x "${candidate}" ]]; then
      MYSQL_BIN="${candidate}"
      break
    fi
  done
fi

if ! command -v "${MYSQL_BIN}" >/dev/null 2>&1; then
  echo "[db-verify] 未找到 mysql 客户端，请设置 MYSQL_BIN" >&2
  exit 1
fi

HOST="${BONE_DB_HOST:-127.0.0.1}"
PORT="${BONE_DB_PORT:-3306}"
USER="${BONE_DB_USERNAME:-root}"
PASS="${BONE_DB_PASSWORD:-}"
DB="${BONE_DB_NAME:-bone}"

export MYSQL_PWD="${PASS}"

echo "[db-verify] 连接 ${USER}@${HOST}:${PORT}/${DB} ..."
"${MYSQL_BIN}" -h"${HOST}" -P"${PORT}" -u"${USER}" -e "SELECT VERSION() AS version;" 2>/dev/null

TABLE_COUNT=$("${MYSQL_BIN}" -h"${HOST}" -P"${PORT}" -u"${USER}" -N -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB}';" 2>/dev/null)
echo "[db-verify] 库 ${DB} 当前表数量: ${TABLE_COUNT}"

INIT_SQL="${REPO_ROOT}/bone-init.sql"
if [[ -f "${INIT_SQL}" ]]; then
  EXPECTED=$(grep -c '^CREATE TABLE' "${INIT_SQL}" || true)
  echo "[db-verify] bone-init.sql 声明 CREATE TABLE 数量: ${EXPECTED}"
  echo "[db-verify] 说明: 本地库可能含历史表（如 iam_account），与 init 脚本不完全一致时以业务模块为准。"
fi

echo "[db-verify] 表列表:"
"${MYSQL_BIN}" -h"${HOST}" -P"${PORT}" -u"${USER}" -D"${DB}" -e "SHOW TABLES;" 2>/dev/null

unset MYSQL_PWD
echo "[db-verify] 完成"
