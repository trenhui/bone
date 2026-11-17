#!/usr/bin/env bash
# change 自验证脚本：synapclaim-risk-fraud-dimensions 的独立 verify 检查。
# 由 comet guard <change> verify --apply -- bash <this> 调用；不写库、不依赖在线服务。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
cd "$ROOT"

CHANGE=openspec/changes/synapclaim-risk-fraud-dimensions
REPORT=docs/superpowers/reports/2026-11-17-synapclaim-risk-fraud-dimensions-verify.md
WORKTREE=.worktrees/_dev-merge-tmp

echo "[1/6] 风控富化定向单测（6/6）"
mvn -o -q -pl bone-apps/synap-claim/synap-claim-app -am test \
  -Dtest=QlExpressRuleEnginePortAdapterEnrichmentTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo "[2/6] 当前规则资产与富化/仓储/测试源码静态断言"
python3 - <<'PY'
import json
import re
from pathlib import Path

root = Path('.')
rules_path = root / 'bone-apps/synap-claim/synap-claim-app/src/main/resources/claim-ruleset.json'
repo_path = root / 'bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim/domain/repository/ClaimCaseRepository.java'
adapter_path = root / 'bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim/infrastructure/rule/QlExpressRuleEnginePortAdapter.java'
test_path = root / 'bone-apps/synap-claim/synap-claim-app/src/test/java/com/bone/claim/infrastructure/rule/QlExpressRuleEnginePortAdapterEnrichmentTest.java'

data = json.loads(rules_path.read_text(encoding='utf-8'))
version = data['version']
match = re.fullmatch(r'claim-ruleset-v(\d+)', version)
assert match is not None, f'规则集版本格式异常: {version}'
assert int(match.group(1)) >= 4, f'规则集版本低于本批 v4 基线: {version}'
rules = {item['code']: item for item in data['rules']}
expected = {
    'R-FRAUD-22': {
        'category': 'FRAUD', 'priority': 27,
        'expression': 'priorClaimCount >= 1',
        'conclusion': 'PRIOR_CLAIM_DUPLICATE', 'risk': True,
    },
    'R-ANOM-03': {
        'category': 'ANOMALY', 'priority': 54,
        'expression': 'recentClaims90d >= 3',
        'conclusion': 'FREQUENCY_ANOMALY', 'risk': False,
    },
}
for code, fields in expected.items():
    assert code in rules, f'规则缺失: {code}'
    for key, value in fields.items():
        assert rules[code].get(key) == value, f'{code}.{key} 漂移: {rules[code].get(key)!r} != {value!r}'

repo = repo_path.read_text(encoding='utf-8')
assert 'findByInsuredIdHash(Long tenantId, String insuredIdHash)' in repo, '仓储方法签名漂移'
adapter = adapter_path.read_text(encoding='utf-8')
for needle in (
    'enrichPriorClaimFacts',
    'findByInsuredIdHash(claim.getTenantId(), claim.getInsuredIdHash())',
    'facts.put("priorClaimCount"',
    'facts.put("recentClaims90d"',
):
    assert needle in adapter, f'富化实现缺锚点: {needle}'
tests = test_path.read_text(encoding='utf-8')
for needle in (
    'crossCaseDuplicateTriggersFraudRule',
    'frequencyAnomalyTriggersAuditRule',
    'noPriorClaimsNoFalsePositive',
    'excludesCurrentClaimFromHistory',
):
    assert needle in tests, f'测试缺正向钉子: {needle}'
print(f'  OK 当前规则集 {version}，两条规则/仓储/富化/四个新用例锚点齐备')
PY

echo "[3/6] Comet Build check 权威回执与 15/15 任务清单"
python3 - <<'PY'
import json
import pathlib
import shutil
import subprocess
import tempfile

tmp = pathlib.Path(tempfile.mkdtemp(prefix='comet-riskfraud-verify-'))
try:
    request = tmp / 'inspect.json'
    request.write_text(
        '{"operation":"inspect","runId":"synapclaim-risk-fraud-dimensions"}',
        encoding='utf-8')
    raw = subprocess.check_output(
        ['comet', 'runtime', 'dispatch', '--application', 'classic-full',
         '--project-root', '.', '--request', str(request), '--json', '--details'],
        text=True)
    run = json.loads(raw)['data']

    def find_build_output(node):
        if isinstance(node, dict):
            value = node.get('full.build.check')
            if isinstance(value, dict) and 'exitCode' in value:
                return value
            for child in node.values():
                found = find_build_output(child)
                if found is not None:
                    return found
        elif isinstance(node, list):
            for child in node:
                found = find_build_output(child)
                if found is not None:
                    return found
        return None

    receipt = find_build_output(run.get('actions', []))
    assert receipt is not None, 'Runtime Run 历史缺 full.build.check 输出'
    assert receipt.get('exitCode') == 0, f"Build check exitCode 非 0: {receipt.get('exitCode')}"
    expected_hash = 'b124c37d265f4eb646e89b51f2166e18252b45604db5774a84b11dadb9752263'
    assert receipt.get('contentHash') == expected_hash, f"Build check hash 漂移: {receipt.get('contentHash')}"
    assert receipt.get('inputBefore') == receipt.get('inputAfter'), 'Build check 输入在执行中变化'
    assert receipt.get('scope') == 'build' and receipt.get('tier') == 'full', 'Build check scope/tier 漂移'
finally:
    shutil.rmtree(tmp, ignore_errors=True)

tasks = pathlib.Path('openspec/changes/synapclaim-risk-fraud-dimensions/tasks.md').read_text(encoding='utf-8')
checked = tasks.count('- [x] ')
unchecked = tasks.count('- [ ] ')
assert checked == 15, f'已勾选任务数 != 15: {checked}'
assert unchecked == 0, f'仍有未勾选任务: {unchecked}'
print('  OK Runtime Build 回执 exitCode=0/hash 一致；tasks 15/15 已勾选')
PY

echo "[4/6] 历史版本 bump v3→v4 与文档 gap 证据"
python3 - <<'PY'
import json
import subprocess
from pathlib import Path

worktree = '.worktrees/_dev-merge-tmp'
path = 'bone-apps/synap-claim/src/main/resources/claim-ruleset.json'

def show(rev):
    raw = subprocess.check_output(
        ['git', '-C', worktree, 'show', f'{rev}:{path}'], text=True)
    return json.loads(raw)

parent_version = show('320573ffa^')['version']
commit_version = show('320573ffa')['version']
assert parent_version == 'claim-ruleset-v3', f'父提交版本非 v3: {parent_version}'
assert commit_version == 'claim-ruleset-v4', f'本批提交版本非 v4: {commit_version}'
assert 'claim-ruleset-v3→v4' in Path(
    'docs/superpowers/reports/2026-11-17-synapclaim-risk-fraud-dimensions-verify.md'
).read_text(encoding='utf-8'), '报告缺版本 bump 证据'

history = Path('doc/ai-product/synapClaim/09-records/AI技术方案-版本历史.md').read_text(encoding='utf-8')
for needle in ('v2.5', 'R-FRAUD-22', 'R-ANOM-03', '不实装、不伪造', 'L3'):
    assert needle in history, f'版本历史缺锚点: {needle}'
rulebook = Path(
    'doc/ai-product/synapClaim/07-delivery/04-业务规则书（Rulebook）-门诊小额快赔MVP全集.md'
).read_text(encoding='utf-8')
for needle in ('R-FRAUD-22', 'R-ANOM-03', 'priorClaimCount >= 1', 'recentClaims90d >= 3'):
    assert needle in rulebook, f'Rulebook 缺锚点: {needle}'
print('  OK 历史 v3→v4 可证；v2.5 与 Rulebook 登记齐备')
PY

echo "[5/6] Verify 报告关键证据"
python3 - "$REPORT" <<'PY'
import pathlib
import sys

report = pathlib.Path(sys.argv[1]).read_text(encoding='utf-8')
needles = (
    '320573ffa', '15/15', '1490 run', 'TARGETED_RC=0', 'CHECK_RC=0',
    'COMET_BUILD_CHECK_RC=0', 'M1', 'M2', 'claim-ruleset-v3→v4',
    '不实装、不伪造', '零 DDL', '[59/59]',
)
for needle in needles:
    assert needle in report, f'报告缺关键证据: {needle}'
print(f'  OK 报告 {len(needles)} 项关键证据齐备')
PY

echo "[6/6] 关键交付物存在性"
for f in \
  "$CHANGE/proposal.md" \
  "$CHANGE/design.md" \
  "$CHANGE/tasks.md" \
  "$CHANGE/specs/claim/rule-engine/spec.md" \
  "$CHANGE/verify.sh" \
  docs/superpowers/plans/2026-11-17-synapclaim-risk-fraud-dimensions.md \
  "$REPORT" \
  bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim/domain/repository/ClaimCaseRepository.java \
  bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim/infrastructure/rule/QlExpressRuleEnginePortAdapter.java \
  bone-apps/synap-claim/synap-claim-app/src/main/resources/claim-ruleset.json \
  bone-apps/synap-claim/synap-claim-app/src/test/java/com/bone/claim/infrastructure/rule/QlExpressRuleEnginePortAdapterEnrichmentTest.java \
  doc/ai-product/synapClaim/09-records/AI技术方案-版本历史.md \
  "doc/ai-product/synapClaim/07-delivery/04-业务规则书（Rulebook）-门诊小额快赔MVP全集.md"; do
  test -f "$f" || { echo "缺交付物: $f"; exit 1; }
done
echo "  OK 13 份关键交付物在位"

echo "ALL CHECKS PASSED — synapclaim-risk-fraud-dimensions verify"
