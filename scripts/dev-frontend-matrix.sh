#!/usr/bin/env bash
# =============================================================================
# dev-frontend-matrix.sh — 本地启动 bone 前端微应用矩阵（qiankun 主应用 + 9 个子应用）
#
# 背景（2026-10-25 联调）：前端此前只验过单点构建，未整体起过；联调需要
# 「shell :3000 能加载子应用、子应用能经 vite proxy 打到网关 :8888」这条真实链路。
#
# 设计
# ----------------------
# · 端口真源 = 各 app 的 vite.config.ts `createQiankunViteConfig(name, port)`，
#   本脚本不另写一份端口表，启动后以实际监听端口回报，避免双轨漂移；
# · 绑定 127.0.0.1（不用 0.0.0.0）：本机联调无需暴露到局域网；
# · 子进程必须由本脚本 `wait` 持有：沙箱/CI 会在父进程退出时回收孤儿进程，
#   故不能用 `nohup ... &` 自 detach（会被杀掉，表现为端口全空）；
# · 日志一应用一份，落在 logs/fe/<app>.log，便于单应用排障。
#
# 用法:
#   bash scripts/dev-frontend-matrix.sh            # 前台守护（Ctrl-C 全部退出）
#   bash scripts/dev-frontend-matrix.sh --check     # 只探测各端口是否已就绪（CI 用）
# =============================================================================
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FE_DIR="$REPO_ROOT/bone-frontend"
LOG_DIR="$REPO_ROOT/logs/fe"

# 主应用在前：shell 需要先拿到子应用入口才能注册 qiankun
APPS=(
  bone-shell
  bone-iam-app
  bone-metadata-app
  bone-masterdata-app
  bone-integration-app
  bone-system-app
  bone-extension-app
  bone-generator-app
  bone-commerce-app
  bone-claim-app
)

# 从 app 自己的 vite.config.ts 读端口（唯一真源），读不到则跳过并报错。
# 两种写法并存：子应用用 createQiankunViteConfig(name, port, ...)，主应用 bone-shell
# 是普通 defineConfig（server.port 直写），两者都要认。
port_of() {
  local app="$1"
  local cfg="$FE_DIR/apps/$app/vite.config.ts"
  local p=""
  [ -f "$cfg" ] || {
    echo ""
    return
  }
  p="$(grep -oE "createQiankunViteConfig\('[^']+', *[0-9]{4}" "$cfg" | grep -oE "[0-9]{4}$" | head -1)"
  if [ -z "$p" ]; then
    p="$(grep -oE "^[[:space:]]*port:[[:space:]]*[0-9]{4}" "$cfg" | grep -oE "[0-9]{4}" | head -1)"
  fi
  printf '%s' "$p"
}

check_ready() {
  local fail=0
  printf "%-22s %-6s %s\n" "APP" "PORT" "HTTP"
  for app in "${APPS[@]}"; do
    local port code
    port="$(port_of "$app")"
    if [ -z "$port" ]; then
      printf "%-22s %-6s %s\n" "$app" "-" "无法从 vite.config.ts 取端口"
      fail=$((fail + 1))
      continue
    fi
    code="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$port/" 2>/dev/null)"
    printf "%-22s %-6s %s\n" "$app" "$port" "$code"
    [ "$code" = "200" ] || fail=$((fail + 1))
  done
  return "$fail"
}

if [ "${1:-}" = "--check" ]; then
  check_ready
  exit $?
fi

mkdir -p "$LOG_DIR"
pids=()
for app in "${APPS[@]}"; do
  port="$(port_of "$app")"
  if [ -z "$port" ]; then
    echo "[skip] ${app}: vite.config.ts 未取到端口" >&2
    continue
  fi
  ( cd "$FE_DIR" && exec npm run dev --workspace="$app" -- --host 127.0.0.1 >"$LOG_DIR/$app.log" 2>&1 ) &
  pids+=("$!")
  echo "[start] $app :$port (pid $!)"
done

cleanup() {
  echo ""
  echo "[stop] 关闭 ${#pids[@]} 个前端进程"
  for pid in "${pids[@]}"; do kill "$pid" 2>/dev/null || true; done
}
trap cleanup INT TERM EXIT

# 等待全部就绪（最多 90s），再进入 wait 保活
for _ in $(seq 1 45); do
  ready=0
  for app in "${APPS[@]}"; do
    port="$(port_of "$app")"
    [ -z "$port" ] && continue
    [ "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$port/" 2>/dev/null)" = "200" ] && ready=$((ready + 1))
  done
  [ "$ready" -ge "${#APPS[@]}" ] && break
  sleep 2
done
echo "[ready] $(check_ready | tail -n +2 | grep -c ' 200$')/${#APPS[@]} 个应用返回 200"

wait
