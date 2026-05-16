#!/usr/bin/env bash
# 三模块浏览器联调：仅启动 bone-iam（控制台+扩展占位 API）+ 三个前端
# 用法: bash scripts/start-three-modules-e2e.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="${ROOT}/logs/e2e"
IAM_JAR="${ROOT}/bone-platform/bone-iam/target/bone-iam-1.0.0.jar"
mkdir -p "$LOG_DIR"

export BONE_DB_PASSWORD="${BONE_DB_PASSWORD:-mysql123}"

kill_port() {
  local p=$1
  if lsof -ti:"$p" >/dev/null 2>&1; then
    echo "    释放端口 $p"
    lsof -ti:"$p" | xargs kill -9 2>/dev/null || true
  fi
}

if [ ! -f "$IAM_JAR" ]; then
  echo "==> 编译 bone-iam..."
  (cd "$ROOT" && mvn -q -pl bone-platform/bone-iam -am package -DskipTests)
fi

kill_port 8081
kill_port 3000
kill_port 3003
kill_port 3008

echo "==> 启动 bone-iam (java -jar) :8081"
nohup java -Xms256m -Xmx768m -jar "$IAM_JAR" \
  >"$LOG_DIR/bone-iam.log" 2>&1 &
echo $! >"$LOG_DIR/bone-iam.pid"

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
  echo "    FAIL $name — tail -50 $LOG_DIR/bone-iam.log"
  return 1
}

echo "==> 等待 IAM..."
wait_http "http://localhost:8081/api/console/overview" "bone-iam" 120

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
echo " 登录: admin / admin123"
echo " 验证:"
echo "   - 首页 — 控制台概览、快捷操作"
echo "   - IAM 微应用 — 用户列表 /api/iam/users"
echo "   - 扩展 — /extension 扩展点与插件"
echo " 自动检查: bash scripts/verify-three-modules-e2e.sh"
echo " 日志: $LOG_DIR"
echo "=========================================="
