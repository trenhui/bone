#!/usr/bin/env bash
# 审核疲劳防控 change 自验证：真实单测 + 静态判据 + Comet Build 回执 + 诚实 gap。
# 由 comet guard <change> verify --apply -- bash <this> 调用；不写库、不依赖在线服务。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
cd "$ROOT"

CHANGE=openspec/changes/synapclaim-review-fatigue-controls
WORKTREE=.worktrees/_dev-merge-tmp
REPORT=docs/superpowers/reports/2026-11-17-synapclaim-review-fatigue-controls-verify.md

echo "[1/6] 审核疲劳定向单测（50/50）"
(cd "$WORKTREE" && mvn -o -q -pl bone-apps/synap-claim/synap-claim-app -am test \
  -Dtest=ReviewFatigueConfigTest,FatigueAwareEligibilityPortTest,ReviewFatigueGuardServiceTest,ReviewFatigueGuardControllerTest,FeatureTenantToggleServiceTest,ClaimTaskApplicationServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false)

echo "[2/6] 配置/闸门/端点/前端/E2E 静态锚点"
python3 - <<'PY'
from pathlib import Path

wt = Path('.worktrees/_dev-merge-tmp')
src = wt / 'bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim'
test = wt / 'bone-apps/synap-claim/synap-claim-app/src/test/java/com/bone/claim'
anchors = {
    src / 'domain/model/config/ReviewFatigueConfig.java': [
        'DEFAULT_MAX_PER_SHIFT = 120', 'DEFAULT_REST_PROMPT_MINUTES = 120',
        'DEFAULT_SHIFT_END_EXCLUSION_MINUTES = 30', 'DEFAULT_SENSITIVE_AMOUNT_YUAN = 10000L',
        '未知键，未采信', 'intValue(', 'longValue(',
    ],
    src / 'domain/model/config/FeatureToggle.java': ['FEATURE_REVIEW_FATIGUE_GUARD'],
    src / 'application/FeatureTenantToggleService.java': [
        'reviewFatigueConfig(long tenantId)', 'FEATURE_REVIEW_FATIGUE_GUARD', 'JSON_SENTINEL',
    ],
    src / 'domain/repository/ClaimTaskRepository.java': [
        'countFinishedTodayByAssignee', 'ClaimTaskStatus.DONE.name()', '.gte(ClaimTask::getFinishedAt',
    ],
    src / 'domain/service/FatigueAwareEligibilityPort.java': [
        'return done < config.maxPerShift();', 'counted == null || counted < 0',
        'catch (RuntimeException e)',
    ],
    src / 'application/ClaimTaskApplicationService.java': [
        'new FatigueAwareEligibilityPort(', 'utcDayStart(Instant.now())',
    ],
    src / 'application/ReviewFatigueGuardService.java': [
        'continuousMinutes >= config.restPromptMinutes()', 'sensitiveAmountReached(claimAmount)',
    ],
    src / 'adapter/web/controller/ReviewFatigueGuardController.java': [
        '@GetMapping("/evaluation")', 'claim:claims:read', 'CurrentOperator.requireId()',
    ],
    test / 'domain/model/config/ReviewFatigueConfigTest.java': ['zeroMaxFallsBack', 'unknownKeyRejected'],
    test / 'application/ClaimTaskApplicationServiceTest.java': [
        'openForClaim_fatigueOverLimit_poolsTask', 'openForClaim_fatigueCountFailure_poolsTask',
    ],
    wt / 'bone-frontend/apps/bone-claim-app/src/services/reviewFatigue.ts': ['/fatigue-guard/evaluation'],
    wt / 'bone-frontend/apps/bone-claim-app/src/pages/ReviewWorkbenchPage.tsx': [
        '审核疲劳提示（仅提示，不阻断提交）', 'continuousMinutes: sessionContinuousMinutes()',
    ],
    wt / 'scripts/e2e/synap-claim-review-fatigue-e2e.py': [
        '"/review/fatigue-guard/evaluation"', 'E5 生效阈值与库内配置读回一致',
    ],
    wt / 'doc/ai-product/synapClaim/09-records/AI技术方案-版本历史.md': ['|v2.11|2026-11-17|'],
}
for path, needles in anchors.items():
    text = path.read_text(encoding='utf-8')
    for needle in needles:
        assert needle in text, f'{path}: 缺锚点 {needle}'
print(f'  OK {len(anchors)} 个文件 / {sum(map(len, anchors.values()))} 个锚点齐备')
PY

echo "[3/6] 报告中的真实测试 / 门禁 / E2E 证据"
python3 - <<'PY'
from pathlib import Path
text = Path('.worktrees/_dev-merge-tmp/docs/superpowers/reports/2026-11-17-synapclaim-review-fatigue-controls-verify.md').read_text(encoding='utf-8')
for needle in ('50/50 PASS', '1520 run, 0 failures, 0 errors, 7 skipped',
               '[59/59]', '✅ 本地自检通过！', 'E2E_RC=0，5/5 PASS',
               'npm run typecheck -w bone-claim-app', '恢复源码后重跑'):
    assert needle in text, f'报告缺证据: {needle}'
print('  OK 报告含定向/全量/门禁/E2E/typecheck/变异证据')
PY

echo "[4/6] Comet Build check 权威回执与 12/12 任务"
python3 - <<'PY'
import json, pathlib, shutil, subprocess, tempfile
tmp = pathlib.Path(tempfile.mkdtemp(prefix='comet-fatigue-verify-'))
try:
    (tmp / 'inspect.json').write_text(
        '{"operation":"inspect","runId":"synapclaim-review-fatigue-controls"}', encoding='utf-8')
    raw = subprocess.check_output(
        ['comet', 'runtime', 'dispatch', '--application', 'classic-full',
         '--project-root', '.', '--request', str(tmp / 'inspect.json'), '--json', '--details'],
        text=True)
    run = json.loads(raw)['data']

    def find(node):
        if isinstance(node, dict):
            value = node.get('full.build.check')
            if isinstance(value, dict) and 'contentHash' in value:
                return value
            for child in node.values():
                found = find(child)
                if found is not None:
                    return found
        elif isinstance(node, list):
            for child in node:
                found = find(child)
                if found is not None:
                    return found
        return None

    receipt = find(run)
    assert receipt is not None, 'Runtime Run 历史缺 full.build.check 回执'
    expected = '0c99aad88a917daf712cf624e8bc36e733c3899cb87f7d55fb756cca4518d6b1'
    assert receipt.get('contentHash') == expected, f"Build check hash 漂移: {receipt.get('contentHash')}"
    assert receipt.get('exitCode') == 0, 'Build check exitCode 非 0'
    assert receipt.get('scope') == 'build' and receipt.get('tier') == 'full', 'Build check scope/tier 漂移'
    assert receipt.get('inputBefore') == receipt.get('inputAfter'), 'Build check 输入执行中变化'
finally:
    shutil.rmtree(tmp, ignore_errors=True)

tasks = pathlib.Path('openspec/changes/synapclaim-review-fatigue-controls/tasks.md').read_text(encoding='utf-8')
assert tasks.count('- [x] ') == 12, f'已勾选任务数 != 12: {tasks.count("- [x] ")}'
assert tasks.count('- [ ] ') == 0, '仍有未勾选任务'
print('  OK Build 回执 hash/exitCode/输入快照一致；tasks 12/12')
PY

echo "[5/6] 诚实边界：班末硬排除仍登记 gap"
python3 - <<'PY'
from pathlib import Path
report = Path('.worktrees/_dev-merge-tmp/docs/superpowers/reports/2026-11-17-synapclaim-review-fatigue-controls-verify.md').read_text(encoding='utf-8')
history = Path('.worktrees/_dev-merge-tmp/doc/ai-product/synapClaim/09-records/AI技术方案-版本历史.md').read_text(encoding='utf-8')
for needle in ('班末硬排除未实现', '验收登记 gap', '不宣称 §11.10 全项完成'):
    assert needle in report, f'报告缺 gap 声明: {needle}'
assert '班末硬排除仍未实现' in history, 'v2.11 未如实登记班末 gap'
print('  OK gap 在报告与版本历史中一致登记，未冒充已实现')
PY

echo "[6/6] 零 DDL / 零 SQL 模板"
python3 - <<'PY'
import subprocess
wt = '.worktrees/_dev-merge-tmp'
out = subprocess.check_output(['git', '-C', wt, 'diff', '--name-only', 'HEAD'], text=True).splitlines()
out += subprocess.check_output(['git', '-C', wt, 'ls-files', '--others', '--exclude-standard'], text=True).splitlines()
bad = [p for p in out if '/sql/' in p or p.endswith('.sql') or 'migration' in p]
assert not bad, f'本批出现 DDL/SQL 模板变更: {bad}'
print('  OK 未新增迁移 / DDL / SQL 模板')
PY

echo 'ALL CHECKS PASSED'
