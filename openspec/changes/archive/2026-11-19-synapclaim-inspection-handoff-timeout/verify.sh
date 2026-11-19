#!/usr/bin/env bash
# change 自验证脚本：批4 抽检处置与交接闭环的独立 verify 检查。
# 由 comet guard <change> verify --apply -- bash <this> 调用；不写库、不依赖在线服务。
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
CHANGE=openspec/changes/synapclaim-inspection-handoff-timeout
REPORT=docs/superpowers/reports/2026-11-16-synapclaim-inspection-handoff-timeout-verify.md
# 批4 Build 配置提交；其后的提交才是本次交付，避免把历史 Controller 一起算进影响面。
BASE=2c13eb348

echo "[1/6] 后端 TDD 专项单测（批4新增服务 / Job / 架构）"
mvn -o -q -pl bone-apps/synap-claim/synap-claim-app -am test \
  -Dtest=InspectionDispositionServiceTest,UserAgentServiceTest,TaskRequeueServiceTest,FeatureTenantToggleServiceTest,InspectionSamplingJobTest,ArchitectureTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo "[2/6] 前端 bone-claim-app typecheck"
(cd bone-frontend && npm run typecheck -w bone-claim-app)

echo "[3/6] E2E 端点影响门禁（本次变更 6/6 覆盖）+ E2E lint"
python3 scripts/e2e/impact.py --rev "$BASE..HEAD" --strict
bash scripts/e2e/lint.sh

echo "[4/6] 迁移清单与 SQL 列覆盖静态门禁"
python3 scripts/check-migration-manifest.py
python3 scripts/check-sql-entity-column-coverage.py

echo "[5/6] 验证报告关键证据与 13/13 任务清单"
python3 - "$REPORT" "$CHANGE/tasks.md" <<'PY'
import pathlib, sys
report, tasks = (pathlib.Path(p) for p in sys.argv[1:3])
assert report.is_file() and report.stat().st_size > 5000, '验证报告缺失或过短'
r = report.read_text(encoding='utf-8')
needles = (
    '[59/59]', '1490 run', '8/8 PASS', '6/6 端点', 'E3c', '变异验证',
    'SC_CLAIM_REVERSAL_REQUIRED', 'SC_CLAIM_AGENT_CONFLICT',
    'SC_CLAIM_INSPECTION_ACTION_INVALID',
)
for needle in needles:
    assert needle in r, f'报告缺少关键证据: {needle}'
t = tasks.read_text(encoding='utf-8')
assert t.count('- [x] ') == 13, f'已勾选任务数 != 13: {t.count("- [x] ")}'
assert t.count('- [ ] ') == 0, '仍有未勾选任务'
print('  OK 报告 9 项关键证据齐备；tasks 13/13 已勾选')
PY

echo "[6/6] 关键交付物存在性"
for f in \
  scripts/migration/0126_synap_claim_inspection_handoff.sql \
  scripts/migration/migration-manifest.json \
  bone-init.sql \
  bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim/application/InspectionDispositionService.java \
  bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim/application/UserAgentService.java \
  bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim/application/TaskRequeueService.java \
  bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim/adapter/web/controller/InspectionHandoffController.java \
  bone-frontend/apps/bone-claim-app/src/pages/InspectionWorkbenchPage.tsx \
  scripts/e2e/synap-claim-inspection-handoff-e2e.py \
  scripts/e2e/impact-map.json \
  scripts/registry/data-assets.json \
  "$REPORT"; do
  test -f "$f" || { echo "缺交付物: $f"; exit 1; }
done
echo "  OK 12 份关键交付物在位"

echo "ALL CHECKS PASSED — synapclaim-inspection-handoff-timeout verify"
