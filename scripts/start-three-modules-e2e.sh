#!/usr/bin/env bash
# 三模块浏览器联调（v2.0+）：启动后端 IAM(8081) + System(8083) + ExtensionStudio(8088) + 三个前端微应用
# v2.0 起 /api/v1/console/* 由 bone-system 提供（原 bone-iam 副本已删除）
#
# 用法: bash scripts/start-three-modules-e2e.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="${ROOT}/logs/e2e"
IAM_JAR="${ROOT}/bone-platform/bone-iam/target/bone-iam-1.0.0.jar"
SYSTEM_JAR="${ROOT}/bone-platform/bone-system/target/bone-system-1.0.0.jar"
STUDIO_JAR="${ROOT}/bone-engine/bone-extension-engine/bone-extension-studio/target/bone-extension-studio-1.0.0.jar"
mkdir -p "$LOG_DIR"

export BONE_DB_PASSWORD="${BONE_DB_PASSWORD:-mysql123}"

kill_port() {
  local p=$1
  if lsof -ti:"$p" >/dev/null 2>&1; then
    echo "    释放端口 $p"
    lsof -ti:"$p" | xargs kill -9 2>/dev/null || true
  fi
}

build_if_missing() {
  local jar=$1 module=$2
  if [ ! -f "$jar" ]; then
    echo "==> 编译 $module..."
    (cd "$ROOT" && mvn -q -pl "$module" -am package -DskipTests)
  fi
}

build_if_missing "$IAM_JAR" bone-platform/bone-iam
build_if_missing "$SYSTEM_JAR" bone-platform/bone-system
build_if_missing "$STUDIO_JAR" bone-engine/bone-extension-engine/bone-extension-studio

for port in 8081 8083 8088 3000 3003 3008; do
  kill_port "$port"
done

echo "==> 启动 bone-iam (java -jar) :8081"
nohup java -Xms256m -Xmx768m -jar "$IAM_JAR" \
  >"$LOG_DIR/bone-iam.log" 2>&1 &
echo $! >"$LOG_DIR/bone-iam.pid"

echo "==> 启动 bone-system (java -jar) :8083"
nohup java -Xms256m -Xmx512m -jar "$SYSTEM_JAR" \
  >"$LOG_DIR/bone-system.log" 2>&1 &
echo $! >"$LOG_DIR/bone-system.pid"

echo "==> 启动 bone-extension-studio (java -jar) :8088"
nohup java -Xms256m -Xmx512m -jar "$STUDIO_JAR" \
  >"$LOG_DIR/bone-extension-studio.log" 2>&1 &
echo $! >"$LOG_DIR/bone-extension-studio.pid"

wait_http() {
  local url=$1 name=$2 max=${3:-90}
  local i=0
  while [ "$i" -lt "$max" ]; do
    if curl -sf "$url" >/dev/null 2>&1; then
      echo "    OK $name"
      return 0
    fi
    sleep 2
    i=$((i + 2))
  done
  echo "    FAIL $name — tail -50 $LOG_DIR/${name}.log"
  return 1
}

echo "==> 等待后端就绪..."
wait_http "http://localhost:8081/actuator/health" "bone-iam" 120
# v2.0.1 起控制台需 JWT；启动探测用 Actuator，功能验证见 verify-three-modules-e2e.sh
wait_http "http://localhost:8083/actuator/health" "bone-system" 120
wait_http "http://localhost:8088/actuator/health" "bone-extension-studio" 120

echo "==> 安装/启动前端 (bone-frontend workspace)"
FE="$ROOT/bone-frontend"
if [ ! -d "$FE/node_modules" ]; then
  (cd "$FE" && npm install --silent)
fi
for app in bone-shell bone-iam-app bone-extension-app; do
  if [ ! -d "$FE/apps/$app/node_modules" ]; then
    (cd "$FE/apps/$app" && npm install --silent)
  fi
  (cd "$FE/apps/$app" && npx vite >"$LOG_DIR/$app.log" 2>&1 &)
done

sleep 6
echo ""
echo "=========================================="
echo " 浏览器: http://localhost:3000"
echo " 登录: admin / 123456（bone-init.sql 默认；控制台需 sys:console:read）"
echo " 验证:"
echo "   - 首页 — 控制台概览（/api/v1/console/overview · bone-system:8083）"
echo "   - 首页 — 快捷操作（/api/v1/console/quick-actions）"
echo "   - IAM 微应用 — /api/v1/iam/* "
echo "   - 扩展 — /api/v1/extension/* (bone-extension-studio:8088)"
echo " 自动检查: bash scripts/verify-three-modules-e2e.sh"
echo " 日志: $LOG_DIR"
echo "=========================================="
