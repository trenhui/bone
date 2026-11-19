#!/usr/bin/env bash
# change 自验证脚本：批2 AI 配置治理的独立 verify 检查（比 build 全量门禁更聚焦本 change）。
# 由 comet guard <change> verify --apply -- bash <this> 调用；所有检查真实执行，失败即非零。
# 不依赖在线服务、不写库，纯确定性复核，可在换机后重跑。
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
CHANGE=openspec/changes/synapclaim-ai-config-governance-api
REPORT=docs/superpowers/reports/2026-11-16-synapclaim-ai-config-governance-verify.md

echo "[1/5] 后端 TDD 专项单测（服务 / 契约 / 架构）"
mvn -o -q -pl bone-apps/synap-claim/synap-claim-app -am test \
  -Dtest=AiConfigGovernanceServiceTest,AiConfigGovernanceControllerTest,ArchitectureTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo "[2/5] 前端 typecheck / lint / vitest（bone-claim-app）"
(
  cd bone-frontend
  npm run typecheck -w bone-claim-app
  npm run lint -w bone-claim-app
  npm run test -w bone-claim-app
)

echo "[3/5] E2E 影响分析严格门禁 + E2E lint"
python3 scripts/e2e/impact.py --strict
bash scripts/e2e/lint.sh

echo "[4/5] 验证报告关键证据与任务清单完整性"
python3 - "$REPORT" "$CHANGE/tasks.md" <<'PY'
import pathlib, sys
report, tasks = (pathlib.Path(p) for p in sys.argv[1:3])
assert report.is_file() and report.stat().st_size > 3000, '验证报告缺失或过短'
r = report.read_text(encoding='utf-8')
needles = (
    'SC_CLAIM_CONFIG_RATCHET_VIOLATION',
    'SC_CLAIM_CONFIG_WRITE_DISABLED',
    'SC_CLAIM_AI_FEEDBACK_INVALID',
    '59/59',
    '变异验证',
    '137 tests',
    '零残留',
)
for needle in needles:
    assert needle in r, f'报告缺少关键证据: {needle}'
t = tasks.read_text(encoding='utf-8')
assert t.count('- [x] ') == 10, f'已勾选任务数 != 10: {t.count("- [x] ")}'
assert t.count('- [ ] ') == 0, '仍有未勾选任务'
print('  OK 报告 7 项关键证据齐备；tasks 10/10 已勾选')
PY

echo "[5/5] 关键交付物存在性"
for f in \
  bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim/application/AiConfigGovernanceService.java \
  bone-apps/synap-claim/synap-claim-app/src/test/java/com/bone/claim/application/AiConfigGovernanceServiceTest.java \
  bone-apps/synap-claim/synap-claim-app/src/test/java/com/bone/claim/adapter/web/controller/AiConfigGovernanceControllerTest.java \
  bone-frontend/apps/bone-claim-app/src/services/aiGovernance.ts \
  bone-frontend/apps/bone-claim-app/src/services/aiGovernance.test.ts \
  bone-frontend/apps/bone-claim-app/src/pages/AiGovernancePage.tsx \
  scripts/e2e/synap-claim-ai-config-governance-e2e.py \
  scripts/e2e/impact-map.json \
  doc/architecture/Bone-错误码登记.md; do
  test -f "$f" || { echo "缺交付物: $f"; exit 1; }
done
echo "  OK 9 份关键交付物在位"

echo "ALL CHECKS PASSED — synapclaim-ai-config-governance-api verify"
