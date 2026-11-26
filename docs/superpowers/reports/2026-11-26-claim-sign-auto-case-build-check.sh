#!/usr/bin/env bash
# 案件签收自动建案（claim-sign-auto-case）— Build 阶段真实构建/测试检查。
# 由 comet guard claim-sign-auto-case build --apply -- bash <本文件> 调用。
# 原则：确定性、可离线重跑；不依赖运行中服务、不改活库、不改共享状态。
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

CHANGE_DIR=openspec/changes/claim-sign-auto-case
export JAVA_HOME=${JAVA_HOME:-/Users/renhui.trh/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home}
export PATH="$JAVA_HOME/bin:$PATH"
MVN=${MVN:-/Users/renhui.trh/java/apache-maven-3.8.6/bin/mvn}

echo "[1/5] 后端自动建案领域/应用测试 + 签收回归 + 架构规则"
$MVN -o -q -pl bone-apps/synap-claim/synap-claim-app -am test \
  -Dtest='SignBatchItemTest,ClaimSignCasePipelineTest,ClaimSignCaseCreatorTest,ClaimSignBatchTest,ClaimSignApplicationServiceTest,*ArchitectureTest' \
  -Dsurefire.failIfNoSpecifiedTests=false

echo "[2/5] 前端 typecheck + 签收页面/service 单测"
(
  cd bone-frontend
  npm run typecheck -w bone-claim-app
  npx vitest run \
    apps/bone-claim-app/src/services/claimSign.test.ts \
    apps/bone-claim-app/src/pages/CaseSignPage.test.tsx
)

echo "[3/5] 门禁静态一致性（迁移 / init 覆盖 / DDL / 子域 / SDK SQL 模板）"
python3 scripts/check-migration-manifest.py
python3 scripts/check-migration-init-coverage.py
python3 scripts/check-ddl-doc-sync.py
python3 scripts/check-synapclaim-architecture.py
python3 scripts/check-sdk-contract-surface.py

echo "[4/5] 迁移 0145 登记与 tasks 13/13"
python3 - "$CHANGE_DIR/tasks.md" <<'PY'
import json, pathlib, sys
d = json.load(open('scripts/migration/migration-manifest.json', encoding='utf-8'))
by = {e['file']: e for e in d['entries']}
f = '0145_claim_sign_auto_case.sql'
assert f in by, f'缺少迁移 {f}'
assert by[f]['kind'] == 'ddl', f'{f} kind={by[f]["kind"]}'
t = pathlib.Path(sys.argv[1]).read_text(encoding='utf-8')
assert t.count('- [x] ') == 13, f'已勾选任务数 != 13: {t.count("- [x] ")}'
assert t.count('- [ ] ') == 0, '仍有未勾选任务'
print('  OK 0145 kind=ddl；tasks 13/13 已勾选')
PY

echo "[5/5] 关键交付物存在性"
for f in \
  scripts/migration/0145_claim_sign_auto_case.sql \
  bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim/domain/model/sign/SignBatchItem.java \
  bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim/application/ClaimSignCasePipeline.java \
  bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim/application/ClaimSignCaseCreator.java \
  bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim/adapter/web/controller/ClaimSignController.java \
  bone-apps/synap-claim/synap-claim-app/src/main/resources/sql/com/bone/claim/domain/repository/ClaimSignBatchRepository/findItemsByBatchId.sql \
  bone-apps/synap-claim/synap-claim-app/src/main/resources/sql/com/bone/claim/domain/repository/ClaimSignBatchRepository/linkCaseForItem.sql \
  bone-apps/synap-claim/synap-claim-app/src/main/resources/sql/com/bone/claim/domain/repository/ClaimSignBatchRepository/markCaseForItem.sql \
  bone-frontend/apps/bone-claim-app/src/services/claimSign.ts \
  bone-frontend/apps/bone-claim-app/src/pages/CaseSignPage.tsx \
  openspec/changes/claim-sign-auto-case/design.md \
  openspec/changes/claim-sign-auto-case/tasks.md; do
  test -s "$f" || { echo "缺少交付物: $f" >&2; exit 1; }
done
echo "BUILD_CHECK_ALL_OK"
