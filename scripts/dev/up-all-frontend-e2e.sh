#!/bin/bash
# 启动 bone 前端全部微应用（qiankun）：shell + 8 个子应用。
#
# 与后端 up-all-services-e2e.sh 的分工：那个起Spring Boot，这个起 Vite。
# 两者的共同纪律（都是踩过的坑）：
#   1. 严格串行 —— 启动上一个并确认就绪，再起下一个；并发会互相抢内存且日志串在一起无法定位。
#   2. 探测一律 --noproxy '*' —— shell 设有 HTTP_PROXY 就会把"未启动"报成 502。
#   3. 判"是否就绪"只看该端口自己的 HTTP 码；不要经后端网关探测（会撞熔断器，把熔断读成进程挂）。
#
# 用法：bash scripts/dev/up-all-frontend-e2e.sh
# 日志：/tmp/fe-logs/<app>.log

set -u
CURL=/usr/bin/curl
NODE_BIN="/Users/renhui.trh/.workbuddy/binaries/node/versions/22.22.2-3/bin"
export PATH="$NODE_BIN:$PATH"
LOG_DIR=/tmp/fe-logs
FE=/Users/renhui.trh/wps/bone/bone-frontend
mkdir -p "$LOG_DIR"
cd "$FE" || exit 1

# shell 优先：它是用户唯一访问入口，先起它才能在巡检时观察子应用加载
APPS=(
  "bone-shell:3000"
  "bone-iam-app:3003"
  "bone-metadata-app:3004"
  "bone-masterdata-app:3005"
  "bone-integration-app:3006"
  "bone-system-app:3007"
  "bone-extension-app:3008"
  "bone-generator-app:3009"
  "bone-commerce-app:3012"
)

# 等待某端口自己返回 HTTP（任何非 000 都算起来了：Vite 首屏 200，登录态下 302 也算）
wait_port() {
  local port=$1 tries=${2:-40}
  for _ in $(seq 1 "$tries"); do
    if [ "$($CURL -s -m3 --noproxy '*' -o /dev/null -w '%{http_code}' "http://127.0.0.1:$port/" 2>/dev/null)" != "000" ]; then
      return 0
    fi
    sleep 2
  done
  return 1
}

for entry in "${APPS[@]}"; do
  app="${entry%%:*}"
  port="${entry##*:}"
  # 已在跑就跳过（重复执行本脚本时友好）
  if [ "$($CURL -s -m3 --noproxy '*' -o /dev/null -w '%{http_code}' "http://127.0.0.1:$port/" 2>/dev/null)" != "000" ]; then
    echo "⏭  $app (:$port) 已在运行，跳过"
    continue
  fi
  echo "▶  启动 $app (:$port) ..."
  nohup npm run dev --workspace="$app" > "$LOG_DIR/$app.log" 2>&1 < /dev/null &
  disown
  if wait_port "$port" 40; then
    echo "✅ $app (:$port) 就绪"
  else
    echo "❌ $app (:$port) 启动超时，日志尾部："
    tail -12 "$LOG_DIR/$app.log" 2>/dev/null | sed 's/^/     /'
  fi
done

echo ""
echo "===== 前端汇总 ====="
for entry in "${APPS[@]}"; do
  app="${entry%%:*}"; port="${entry##*:}"
  code="$($CURL -s -m3 --noproxy '*' -o /dev/null -w '%{http_code}' "http://127.0.0.1:$port/" 2>/dev/null)"
  case "$code" in
    000) echo "  ❌ $app (:$port) 不可达" ;;
    *)   echo "  ✅ $app (:$port) $code" ;;
  esac
done
echo ""
echo "用户访问入口： http://localhost:3000"
echo "CORS 白名单只认 localhost:3000（换端口登录会 403 GW_UNAUTHORIZED）"
