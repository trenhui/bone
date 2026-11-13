#!/usr/bin/env bash
# 本 change（synapclaim-case360-object-cohesion）的构建期真实校验。
# 仅验证本 change 交付物：shell 分组渲染 + 类型、claim-app 内容组件/URL 契约、文档族门禁。
# 不跑全仓 monorepo 测试，避免并行会话在途改动（extension-app/metadata-app）造成的假性失败。
set -o pipefail
cd "$(dirname "$0")/.." || exit 1
ROOT="$PWD"
fail=0
log() { echo "[verify] $*"; }

log "1) shell tsc --noEmit"
( cd bone-frontend/apps/bone-shell && npx tsc --noEmit ) >/tmp/verify-shell-tsc.log 2>&1 || { fail=1; tail -n 15 /tmp/verify-shell-tsc.log; }

log "2) shell 分组映射单测"
( cd bone-frontend/apps/bone-shell && npx vitest run --root ../.. src/utils/claimMenuGroups.test.ts ) >/tmp/verify-groups.log 2>&1 || { fail=1; tail -n 12 /tmp/verify-groups.log; }

log "3) claim-app 内容组件 + URL 契约单测"
( cd bone-frontend/apps/bone-claim-app && npx vitest run --root ../.. src/cases/caseSections.test.tsx src/workbench/workbench.test.ts ) >/tmp/verify-claim-tests.log 2>&1 || { fail=1; tail -n 12 /tmp/verify-claim-tests.log; }

log "4) 文档族门禁 19/19"
python3 doc/ai-product/synapClaim/tools/check_all.py >/tmp/verify-doc.log 2>&1 || { fail=1; tail -n 12 /tmp/verify-doc.log; }

if [ "$fail" -eq 0 ]; then
  log "ALL CHECKS PASSED"
  exit 0
else
  log "SOME CHECKS FAILED"
  exit 1
fi
