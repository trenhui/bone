#!/usr/bin/env bash
# 案件签收 Claim Secure Signing — Verify 阶段聚焦复核脚本。
# 由 comet guard claim-secure-signing verify --apply -- bash <本文件> 调用。
# 原则：只做确定性、可离线重跑的检查；不依赖运行中服务、不改活库。
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

REPORT=docs/superpowers/reports/2026-11-26-claim-secure-signing-verify.md
CHANGE_DIR=openspec/changes/claim-secure-signing
export JAVA_HOME=${JAVA_HOME:-/Users/renhui.trh/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home}
export PATH="$JAVA_HOME/bin:$PATH"
MVN=${MVN:-/Users/renhui.trh/java/apache-maven-3.8.6/bin/mvn}

echo "[1/6] 后端签收领域/应用/契约/架构测试"
$MVN -o -q -pl bone-apps/synap-claim/synap-claim-app -am test \
  -Dtest='ClaimSignBatchTest,ClaimSignApplicationServiceTest,ClaimCaseApplicationServiceTest,*ArchitectureTest' \
  -Dsurefire.failIfNoSpecifiedTests=false

echo "[2/6] 前端 typecheck + 签收/分组单测"
(
  cd bone-frontend
  npm run typecheck -w bone-claim-app
  npx vitest run \
    apps/bone-claim-app/src/services/claimSign.test.ts \
    apps/bone-claim-app/src/pages/CaseSignPage.test.tsx \
    apps/bone-shell/src/utils/claimMenuGroups.test.ts
)

echo "[3/6] 门禁静态一致性（迁移 / 错误码 / 子域 / DDL / 菜单权限 / 造数影响）"
python3 scripts/check-migration-manifest.py
python3 scripts/check-i18n-sync.py
python3 scripts/check-synapclaim-architecture.py
python3 scripts/check-ddl-doc-sync.py
python3 scripts/check-seed-menu-permission.py
python3 scripts/check-feature-data-impact.py --staged --strict

echo "[4/6] 迁移清单含 0142/0143/0144 且 kind 正确"
python3 - <<'PY'
import json
d = json.load(open('scripts/migration/migration-manifest.json', encoding='utf-8'))
by = {e['file']: e for e in d['entries']}
expect = {
    '0142_claim_secure_signing.sql': 'ddl',
    '0143_seed_claim_sign_menu.sql': 'mixed',
    '0144_fix_claim_sign_tenant_id.sql': 'ddl',
}
for f, kind in expect.items():
    assert f in by, f'缺少迁移 {f}'
    assert by[f]['kind'] == kind, f'{f} kind={by[f]["kind"]} != {kind}'
print('  OK 三个迁移条目齐备')
PY

echo "[5/6] Verify 报告关键证据与任务清单完整性"
python3 - "$REPORT" "$CHANGE_DIR/tasks.md" <<'PY'
import pathlib, sys
report, tasks = (pathlib.Path(p) for p in sys.argv[1:3])
assert report.is_file() and report.stat().st_size > 2500, '验证报告缺失或过短'
r = report.read_text(encoding='utf-8')
needles = (
    '143/143',
    '277',
    '284',
    '1711',
    'SC_CLAIM_SIGN_STATUS_CONFLICT',
    '0144',
    'ClassCastException',
    'Idempotency-Key',
)
for needle in needles:
    assert needle in r, f'报告缺少关键证据: {needle}'
t = tasks.read_text(encoding='utf-8')
assert t.count('- [x] ') == 16, f'已勾选任务数 != 16: {t.count("- [x] ")}'
assert t.count('- [ ] ') == 0, '仍有未勾选任务'
print('  OK 报告证据齐备；tasks 16/16 已勾选')
PY

echo "[6/6] 关键交付物存在性"
for f in \
  scripts/migration/0142_claim_secure_signing.sql \
  scripts/migration/0143_seed_claim_sign_menu.sql \
  scripts/migration/0144_fix_claim_sign_tenant_id.sql \
  bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim/domain/model/sign/ClaimSignBatch.java \
  bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim/application/ClaimSignApplicationService.java \
  bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim/adapter/web/controller/ClaimSignController.java \
  bone-frontend/apps/bone-claim-app/src/services/claimSign.ts \
  bone-frontend/apps/bone-claim-app/src/pages/CaseSignPage.tsx \
  bone-frontend/apps/bone-shell/src/utils/claimMenuGroups.ts \
  openspec/changes/claim-secure-signing/design.md; do
  test -s "$f" || { echo "缺少交付物: $f" >&2; exit 1; }
done
echo "  OK 关键交付物齐备"
echo "VERIFY_SCRIPT_ALL_OK"
