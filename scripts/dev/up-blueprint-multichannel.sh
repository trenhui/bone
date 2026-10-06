#!/usr/bin/env bash
# 多渠道交易域联调：单独拉起 bone-blueprint（含全部多渠道扩展实现）。
#
# 三条硬约束（2026-10-05 实测，踩过才写在这里）：
#   1. IDE 会注入 SERVER__PORT=59538，劫持所有服务端口 —— 日志会骗人地打印
#      "Started ... in 2.9 seconds" 但端口实际起在 59538。必须 env -u SERVER__PORT。
#   2. 数据源口令来自 BONE_DB_PASSWORD，.env 里含 '&' 不能直接 source（zsh parse error），
#      这里显式导出，避免「Access denied for user 'root'@'localhost'」这种与代码无关的 500。
#   3. 本机 Nacos 的账号与默认配置不一致（ErrCode:403 User not found）且
#      discovery.failFast=true —— 注册失败会直接拖垮整个上下文。网关是按显式 URI 转发的，
#      不依赖服务发现，故这里直接关掉注册。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
JAR="$ROOT/bone-blueprint/target/bone-blueprint-1.0.0.jar"

if [[ ! -f "$JAR" ]]; then
  echo "[up-blueprint] 缺少 $JAR，请先 mvn -o -pl bone-blueprint package" >&2
  exit 1
fi

pkill -f "bone-blueprint-1.0.0.jar" >/dev/null 2>&1 || true
sleep 2

cd "$ROOT"
env -u SERVER__PORT -u SERVER__HOST \
  BONE_DB_PASSWORD="${BONE_DB_PASSWORD:-mysql123}" \
  BONE_JWT_SECRET="${BONE_JWT_SECRET:-dev-only-secret-key-minimum-32-bytes-long}" \
  nohup java -Xms256m -Xmx700m -jar "$JAR" \
    --server.port=8082 \
    --spring.cloud.nacos.discovery.enabled=false \
    --spring.cloud.nacos.discovery.register-enabled=false \
    --spring.cloud.discovery.enabled=false \
    > /tmp/blueprint-mc.log 2>&1 &

echo "[up-blueprint] started, log=/tmp/blueprint-mc.log"
