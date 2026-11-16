#!/usr/bin/env bash
# change 自验证脚本：纯文档交付的独立 verify 检查（比 build 的单项门禁更强）。
# 由 comet guard <change> verify --apply -- bash <this> 调用；所有检查真实执行，失败即非零。
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
CHANGE=openspec/changes/synapclaim-workbench-assignment-review-quality
REPORT=docs/superpowers/reports/2026-11-13-synapclaim-workbench-assignment-review-quality-verify.md

echo "[1/5] 文档族全量门禁（19 项）"
python3 doc/ai-product/synapClaim/tools/check_all.py
echo "[2/5] AI-INDEX 一致性"
python3 doc/ai-product/synapClaim/tools/_gen_ai_index.py --check
echo "[3/5] 跨文档引用（xref）"
python3 doc/ai-product/synapClaim/tools/_check_xref.py
echo "[4/5] 报告与任务清单完整性"
python3 - "$REPORT" "$CHANGE/tasks.md" <<'PY'
import pathlib, sys
report, tasks = (pathlib.Path(p) for p in sys.argv[1:3])
assert report.is_file() and report.stat().st_size > 3000, '验证报告缺失或过短'
r = report.read_text(encoding='utf-8')
for needle in ('19/19 PASS', 'MAIN_REAL_RC=1', '6f83e67ef', 'scoped verification', '可接受偏差'):
    assert needle in r, f'报告缺少关键证据: {needle}'
t = tasks.read_text(encoding='utf-8')
assert t.count('- [x] ') == 17, f'已勾选任务数 != 17: {t.count("- [x] ")}'
assert t.count('- [ ] ') == 0, '仍有未勾选任务'
print('  OK 报告 5 项关键证据齐备；tasks 17/17 已勾选')
PY
echo "[5/5] 交付物存在性与漂移纠正"
python3 - "$CHANGE" <<'PY'
import json, pathlib, sys
root = pathlib.Path('doc/ai-product/synapClaim'); change = pathlib.Path(sys.argv[1])
for f in ('04-design/作业分配与代理交接设计.md', '04-design/独立复审阶段设计.md',
          '04-design/抽检打回闭环设计.md', '05-plan/智能理赔-易用性与角色工作台设计提案.md'):
    assert (root/f).is_file(), f'缺交付文档: {f}'
for f in ('proposal.md','design.md','tasks.md','verify.sh'):
    assert (change/f).is_file(), f'缺 change 产物: {f}'
plan = (root/'05-plan/智能理赔-易用性与角色工作台设计提案.md').read_text(encoding='utf-8')
assert '十三、v2.3 增补' in plan and 'v2.2 改判（本轮）' in plan, 'v2.3 增补或 v2.2 原文缺失'
m = json.load(open(root/'docs.manifest.json', encoding='utf-8'))
ids = {d['id'] for d in m['docs']}
for i in ('design.assign-agent','design.re-review','design.inspection-action'):
    assert i in ids, f'manifest 缺登记: {i}'
ddl = (root/'03-contract/工程详细设计-落地版.md').read_text(encoding='utf-8')
assert '作业组 = IAM `iam_dept`' in ddl or '作业组真源' in ddl or 'iam_dept' in ddl, 'org_id 纠正未回写'
print('  OK 4 份文档 / 4 个 change 产物 / 3 条 manifest 登记 / org_id 纠正均在位')
PY
echo "ALL CHECKS PASSED — workbench-assignment-review-quality verify"
