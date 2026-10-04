#!/usr/bin/env bash
# ============================================================================
# DRAFT — MySQL 8.0.33 → 8.4 LTS 升级脚本（L4，禁止 AI / 自动化执行）
# ----------------------------------------------------------------------------
# 适用：生产/测试库从 MySQL 8.0.33 升级到 8.4 LTS（Oracle 扩展支持 ~2032）。
# 风险：涉及生产数据目录，必须先全量备份、具备可回滚预案，并在受控变更窗口执行。
# 执行人：仅限 DBA / 架构师。AI Agent 不得调用本脚本（违反 L4 边界）。
# 状态：草案，未经评审前请勿使用。
# ============================================================================
set -euo pipefail

# ---- 0. 安全护栏：未显式确认不得运行 ----
if [[ "${1:-}" != "--i-am-dba-and-accept-risk" ]]; then
  echo "DRAFT 脚本：禁止自动执行。如确需运行，须由 DBA 显式调用并附带参数 --i-am-dba-and-accept-risk" >&2
  exit 2
fi

# ---- 1. 可调参数 ----
OLD_IMAGE="mysql:8.0.33"
NEW_IMAGE="mysql:8.4"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
MYSQL_SERVICE="${MYSQL_SERVICE:-mysql}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/mysql-$(date +%Y%m%d-%H%M%S)}"
INIT_SQL="${INIT_SQL:-doc/sql/bone-init.sql}"

echo "==> [0] 校验前置：compose 文件存在"
[[ -f "$COMPOSE_FILE" ]] || { echo "ERROR: $COMPOSE_FILE 不存在" >&2; exit 1; }

# ---- 2. 全量逻辑备份（可回滚的基石）----
echo "==> [1/6] 逻辑备份到 $BACKUP_DIR"
mkdir -p "$BACKUP_DIR"
docker compose -f "$COMPOSE_FILE" exec -T "$MYSQL_SERVICE" \
  mysqldump --single-transaction --routines --events --triggers --all-databases \
  > "$BACKUP_DIR/all-databases.sql"
# 同时备份数据目录（物理），路径按实际 volume 调整
docker compose -f "$COMPOSE_FILE" inspect "$MYSQL_SERVICE" > "$BACKUP_DIR/volume-inspect.json"
echo "    备份完成：$BACKUP_DIR/all-databases.sql + volume-inspect.json"

# ---- 3. 停应用写入，避免升级期间写入 ----
echo "==> [2/6] 停止 bone-* 应用写入"
docker compose -f "$COMPOSE_FILE" stop bone-system bone-iam bone-gateway bone-integration bone-blueprint 2>/dev/null || true

# ---- 4. 修改镜像并启动 8.4（数据字典就地升级）----
echo "==> [3/6] 将 $COMPOSE_FILE 中 mysql image: $OLD_IMAGE -> $NEW_IMAGE"
# 实际变更由人工编辑 compose（此处仅占位示意，避免脚本误改）
# sed -i.bak "s|image: *$OLD_IMAGE|image: $NEW_IMAGE|" "$COMPOSE_FILE"
echo "    （请人工确认 compose 中 image 已改为 $NEW_IMAGE 后再继续）"
read -r -p "    确认已修改 image 并回车继续 [y/N]: " _ack
[[ "$_ack" == "y" || "$_ack" == "Y" ]] || { echo "已中止"; exit 3; }

echo "==> [4/6] 启动 8.4 并执行数据字典升级"
docker compose -f "$COMPOSE_FILE" up -d "$MYSQL_SERVICE"
docker compose -f "$COMPOSE_FILE" exec -T "$MYSQL_SERVICE" mysql_upgrade --force

# ---- 5. 校验 bone 元数据 ----
echo "==> [5/6] 校验初始化 SQL 兼容性：$INIT_SQL"
if [[ -f "$INIT_SQL" ]]; then
  docker compose -f "$COMPOSE_FILE" exec -T "$MYSQL_SERVICE" \
    mysql -e "SOURCE /$INIT_SQL;" && echo "    bone-init.sql 应用成功" \
    || echo "    WARN: bone-init.sql 需人工核对（表结构/字符集/权限）"
else
  echo "    WARN: 未找到 $INIT_SQL，跳过元数据校验"
fi

# ---- 6. 冒烟 + 回滚预案 ----
echo "==> [6/6] 启动应用并冒烟"
docker compose -f "$COMPOSE_FILE" up -d
echo "完成。请人工核对：慢查询/复制延迟/连接池/权限；备份保留 >=30 天。"
echo "回滚预案：docker compose down && 恢复 $BACKUP_DIR 物理卷 + 逻辑备份。"
