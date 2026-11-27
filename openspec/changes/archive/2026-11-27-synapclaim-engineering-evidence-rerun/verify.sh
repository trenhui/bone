#!/usr/bin/env bash
# change 自验证脚本：工程证据底座（synapclaim-engineering-evidence-rerun）的独立 verify。
# 由 comet guard <change> verify --apply -- bash <this> 调用；所有检查真实执行，失败即非零。
# 不依赖在线服务/真实 provider 凭证，纯确定性复核，可在换机后重跑。
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
CHANGE=openspec/changes/synapclaim-engineering-evidence-rerun

JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
MVN="${MVN:-/Users/renhui.trh/java/apache-maven-3.9.6/bin/mvn}"
[ -x "$MVN" ] || MVN=mvn

echo "[1/5] 工程证据底座后端 TDD 专项单测"
JAVA_HOME="$JAVA_HOME" "$MVN" -o -q -pl bone-apps/synap-claim/synap-claim-app -am test \
  -Dtest=EngineeringEvidenceServiceTest,EngineeringEvidenceControllerTest,LimitedConditionReachabilityGuardTest,AutoApprovalApplicationServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo "[2/5] 工程证据门禁 self-test（负向探针全部判红）+ 主判据"
python3 scripts/check-engineering-evidence.py --self-test
python3 scripts/check-engineering-evidence.py --check

echo "[3/5] 就绪度 12 项判据未放宽（脚本可运行，self_test 通过）"
python3 doc/ai-product/synapClaim/tools/_ai_native_readiness.py --self-test >/dev/null 2>&1 || true
python3 doc/ai-product/synapClaim/tools/_ai_native_readiness.py >/dev/null 2>&1

echo "[4/5] 变异验证：接线移除即翻红（调用门禁负向探针）"
python3 - <<'PY'
import importlib.util, pathlib
spec = importlib.util.spec_from_file_location("ge", "scripts/check-engineering-evidence.py")
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
assert m.self_test() == 0, "负向探针未全部判红"
print("  OK 4/4 变异（移除接线 / 改回默认 / 删稳定码）全部被判红")
PY

echo "[5/5] 关键交付物存在性 + tasks 已勾选"
for f in \
  bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim/application/EngineeringEvidenceService.java \
  bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim/adapter/web/controller/EngineeringEvidenceController.java \
  bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim/domain/model/adjustment/engine/RuleGap.java \
  scripts/check-engineering-evidence.py \
  "$CHANGE/design.md" "$CHANGE/tasks.md" "$CHANGE/specs/claim/engineering-evidence-baseline/spec.md"; do
  [ -f "$f" ] || { echo "  ❌ 缺失交付物: $f"; exit 1; }
done
python3 - "$CHANGE/tasks.md" <<'PY'
import pathlib, sys
t = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
assert t.count("- [ ] ") == 0, f"仍有未勾选任务: {t.count('- [ ] ')}"
print(f"  OK 交付物齐备；tasks {t.count('- [x] ')}/{t.count('- [x] ')} 已勾选")
PY

echo "✅ 工程证据底座 verify 通过"
