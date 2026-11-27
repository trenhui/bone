#!/usr/bin/env bash
# 案件签收自动建案（claim-sign-auto-case）— Verify 阶段真实最终检查。
# 由 comet guard claim-sign-auto-case verify --report <report> --apply -- bash <本文件> 调用。
# 原则：确定性、可离线重跑；不依赖运行中服务、不改活库、不改共享状态。
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

REPORT=docs/superpowers/reports/2026-11-26-claim-sign-auto-case-verify.md
CHANGE_DIR=openspec/changes/claim-sign-auto-case
export JAVA_HOME=${JAVA_HOME:-/Users/renhui.trh/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home}
export PATH="$JAVA_HOME/bin:$PATH"
MVN=${MVN:-/Users/renhui.trh/java/apache-maven-3.8.6/bin/mvn}

echo "[1/6] 后端全量测试（含架构规则）"
$MVN -o -q -pl bone-apps/synap-claim/synap-claim-app -am test

echo "[2/6] 前端 typecheck + 本次相关单测（签收/页面/shell 分组）"
(
  cd bone-frontend
  npm run typecheck -w bone-claim-app
  npx vitest run \
    apps/bone-claim-app/src/services/claimSign.test.ts \
    apps/bone-claim-app/src/pages/CaseSignPage.test.tsx \
    apps/bone-shell/src/utils/claimMenuGroups.test.ts
)

echo "[3/6] 门禁静态一致性（迁移 / init 覆盖 / DDL / 子域 / SDK SQL 模板）"
python3 scripts/check-migration-manifest.py
python3 scripts/check-migration-init-coverage.py
python3 scripts/check-ddl-doc-sync.py
python3 scripts/check-synapclaim-architecture.py
python3 scripts/check-sdk-contract-surface.py

echo "[4/6] 迁移 0145/0146 登记与 tasks 13/13"
python3 - "$CHANGE_DIR/tasks.md" <<'PY'
import json, pathlib, sys
d = json.load(open('scripts/migration/migration-manifest.json', encoding='utf-8'))
by = {e['file']: e for e in d['entries']}
expect = {
    '0145_claim_sign_auto_case.sql': 'ddl',
    '0146_claim_sign_case_status_backfill.sql': 'mixed',
}
for f, kind in expect.items():
    assert f in by, f'缺少迁移 {f}'
    assert by[f]['kind'] == kind, f'{f} kind={by[f]["kind"]} != {kind}'
t = pathlib.Path(sys.argv[1]).read_text(encoding='utf-8')
assert t.count('- [x] ') == 13, f'已勾选任务数 != 13: {t.count("- [x] ")}'
assert t.count('- [ ] ') == 0, '仍有未勾选任务'
print('  OK 0145 kind=ddl / 0146 kind=mixed；tasks 13/13 已勾选')
PY

echo "[5/6] Verify 报告关键证据完整性"
python3 - "$REPORT" <<'PY'
import pathlib, sys
r = pathlib.Path(sys.argv[1])
assert r.is_file() and r.stat().st_size > 4500, '验证报告缺失或过短'
text = r.read_text(encoding='utf-8')
needles = (
    'BUILD_CHECK_ALL_OK', 'AUTO_PRE_EXAMINING', 'AUTO_CASE_FAILED',
    'SignCaseDuplicateException', '0146', 'kept_new=11', '实名明文',
    '独立最终集成审查', '基线预存失败说明', 'IMPORTANT',
)
for needle in needles:
    assert needle in text, f'报告缺少关键证据: {needle}'
print('  OK 报告证据齐备')
PY

echo "[6/6] 关键交付物存在性"
for f in \
  scripts/migration/0145_claim_sign_auto_case.sql \
  scripts/migration/0146_claim_sign_case_status_backfill.sql \
  scripts/claim_sign_backfill/import_sign_batches.py \
  bone-init.sql \
  bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim/application/ClaimSignCasePipeline.java \
  bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim/application/ClaimSignCaseCreator.java \
  bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim/application/SignCaseDuplicateException.java \
  bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim/adapter/web/dto/response/SignCaseItemResp.java \
  bone-frontend/apps/bone-claim-app/src/pages/CaseSignPage.tsx \
  docs/superpowers/reports/2026-11-26-claim-sign-auto-case-verify.md \
  docs/superpowers/reports/2026-11-26-claim-sign-auto-case-build-check.sh \
  openspec/changes/claim-sign-auto-case/design.md \
  openspec/changes/claim-sign-auto-case/tasks.md; do
  test -s "$f" || { echo "缺少交付物: $f" >&2; exit 1; }
done
echo "VERIFY_SCRIPT_ALL_OK"
