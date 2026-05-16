#!/usr/bin/env bash
# 使用 bone-init.sql 初始化/重建 bone 库（含 DROP TABLE，仅开发环境）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=load-env.sh
source "${SCRIPT_DIR}/load-env.sh"

INIT_SQL="${REPO_ROOT}/bone-init.sql"
if [[ ! -f "${INIT_SQL}" ]]; then
  echo "[db-init] 未找到 ${INIT_SQL}" >&2
  exit 1
fi

echo "[db-init] 警告: 将执行 ${INIT_SQL}，其中包含 DROP TABLE，仅用于本地开发。"
read -r -p "继续? [y/N] " confirm
if [[ "${confirm}" != "y" && "${confirm}" != "Y" ]]; then
  echo "[db-init] 已取消"
  exit 0
fi

MYSQL_BIN="${MYSQL_BIN:-mysql}"
for candidate in /usr/local/mysql/bin/mysql /opt/homebrew/bin/mysql mysql; do
  if [[ -x "${candidate}" ]]; then MYSQL_BIN="${candidate}"; break; fi
done

HOST="${BONE_DB_HOST:-127.0.0.1}"
PORT="${BONE_DB_PORT:-3306}"
USER="${BONE_DB_USERNAME:-root}"
export MYSQL_PWD="${BONE_DB_PASSWORD:-}"

"${MYSQL_BIN}" -h"${HOST}" -P"${PORT}" -u"${USER}" < "${INIT_SQL}"
unset MYSQL_PWD
echo "[db-init] 完成，请运行 ./scripts/dev/db-verify.sh"
