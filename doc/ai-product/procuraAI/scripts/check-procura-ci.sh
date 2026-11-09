#!/usr/bin/env bash
# Procura 文档族机器门禁 CI 入口（续诊 O3 落地片段）。
# 作用：把「门禁接 CI」从口头待决变成可一键执行的可复制产物。
# 边界：本脚本只是片段，**不修改仓库 .github/workflows/ 与 check.sh**；
#       是否由平台组评审后合入 CI、是否设为 blocking，由平台组（D6）决定。
#       续诊 R-3 仍属 owner 边界——此处仅落地片段 + 诚实标注，不替平台组拍板。
set -euo pipefail

# 定位仓库根（脚本位于 doc/ai-product/procuraAI/scripts/，上溯 4 层到仓库根 bone/）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
cd "$REPO_ROOT"

echo "[procura-gate] A1-A10 文档一致性门禁"
python3 tools/check_procura_alignment.py

echo "[procura-gate] 注入式负向反证（防假绿：50+ 条反证须全部转红且命中预期 key）"
python3 tools/check_procura_alignment.py --self-test

echo "[procura-gate] AsyncAPI 零漂移（附录 B 注册表 ↔ 契约工件）"
python3 doc/ai-product/procuraAI/detailed-design/contracts/generate_asyncapi.py --check

echo "[procura-gate] 全部通过（本地实测双绿；接入 CI 后才是 ship-ready 判据）"
