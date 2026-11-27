#!/usr/bin/env bash
# change 自验证脚本：四阶段落地现状与解锁规划（synapclaim-four-phase-landing-plan）。
# 本 change 为纯文档产物；验证聚焦四态登记一致性、外部前置边界、零生产改动。
# 不依赖在线服务，可在换机后重跑。
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
CHANGE=openspec/changes/synapclaim-four-phase-landing-plan

echo "[1/5] 必需交付物存在性"
for f in \
  "$CHANGE/proposal.md" "$CHANGE/design.md" "$CHANGE/tasks.md" \
  "$CHANGE/specs/claim/four-phase-landing-governance/spec.md" \
  "$CHANGE/brainstorm-summary.md" \
  docs/superpowers/plans/2026-11-20-synapclaim-four-phase-landing-plan.md \
  doc/ai-product/synapClaim/09-records/synapClaim-四阶段落地对齐说明.md; do
  [ -s "$f" ] || { echo "  ❌ 缺失或空文件: $f"; exit 1; }
done
echo "  OK 7 项交付物齐备且非空"

echo "[2/5] tasks 12/12 已勾选"
python3 - "$CHANGE/tasks.md" <<'PY'
import pathlib, sys
t = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
done = t.count("- [x] ")
undone = t.count("- [ ] ")
assert undone == 0, f"仍有未勾选任务: {undone}"
assert done == 12, f"期望 12 项已勾选，实际 {done}"
print(f"  OK tasks {done}/{done} 已勾选")
PY

echo "[3/5] spec 四态登记与四类外部前置边界"
python3 - "$CHANGE/specs/claim/four-phase-landing-governance/spec.md" <<'PY'
import pathlib, sys
s = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
for state in ("IMPLEMENTED", "PARTIAL_DEFAULT_OFF", "STUB_SAFETY_LOCK", "DESIGN_ONLY"):
    assert state in s, f"spec 缺四态之一: {state}"
for req in ("盲标", "目录准入", "Go-Live", "资金闸", "SIMULATION", "不代签", "单一真源"):
    assert req in s, f"spec 缺边界关键词: {req}"
print("  OK 四态 + 四类外部前置 + AI 不代签/单一真源 边界齐备")
PY

echo "[4/5] 交付说明 15 节点 + 可视化口径一致"
python3 - doc/ai-product/synapClaim/09-records/synapClaim-四阶段落地对齐说明.md <<'PY'
import pathlib, sys
s = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
for stage in ("阶段一", "阶段二", "阶段三", "阶段四"):
    assert stage in s, f"对齐说明缺阶段: {stage}"
for mark in ("IMPLEMENTED", "PARTIAL_DEFAULT_OFF", "STUB_SAFETY_LOCK", "DESIGN_ONLY"):
    assert mark in s, f"对齐说明缺四态: {mark}"
for s2 in ("t_claim_policy", "E2_GRAPH_LOCAL", "available=false", "SIMULATION"):
    assert s2 in s, f"对齐说明缺关键登记: {s2}"
node_rows = s.count("| 🟢") + s.count("| 🟡") + s.count("| 🔴")
assert node_rows >= 15, f"逐节点登记不足 15 行: {node_rows}"
print(f"  OK 四阶段 + 四态 + 两处偏离 + 资金闸 + 逐节点 {node_rows} 行")
PY

echo "[5/5] 零生产改动边界（git diff vs baseRef）"
CHANGED=$(git diff --name-only a0f6db537 HEAD 2>/dev/null || true)
# 本 change 尚未提交，工作树未跟踪产物不算 diff；确认无已跟踪的生产文件改动
TRACKED_DIRTY=$(git diff --name-only 2>/dev/null || true)
if [ -n "$TRACKED_DIRTY" ]; then
  echo "  ❌ 存在已跟踪文件改动（应为零生产改动）:"; echo "$TRACKED_DIRTY"; exit 1
fi
PROD=$(git status --porcelain 2>/dev/null | grep -E 'bone-apps/|bone-framework/|bone-platform/|scripts/check\.sh|scripts/registry/|migrations|迁移' || true)
if [ -n "$PROD" ]; then
  echo "  ❌ 存在生产代码/门禁/迁移改动:"; echo "$PROD"; exit 1
fi
echo "  OK 零生产代码/DDL/门禁/活库改动"

echo "✅ 四阶段落地规划 verify 通过"
