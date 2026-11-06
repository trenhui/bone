#!/bin/bash
# synapClaim 前后端 E2E · 前端（qiankun shell :3000 + 理赔子应用 :3013）
#
# 用法：bash bone-frontend/e2e/start-frontend.sh &
# 浏览器必须访问 http://localhost:3000（网关 CORS 不放行 127.0.0.1）。
set -u
cd "$(dirname "$0")/.." || exit 1
LOGDIR="${TMPDIR:-/tmp}"

npm run dev --workspace=bone-shell > "${LOGDIR}/bone-claim-e2e-shell.log" 2>&1 &
npm run dev --workspace=bone-claim-app > "${LOGDIR}/bone-claim-e2e-claim-app.log" 2>&1 &

echo "frontend starting: shell=3000 claim-app=3013 (logs in ${LOGDIR}/bone-claim-e2e-*.log)"
wait
