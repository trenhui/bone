#!/usr/bin/env bash
# Procura 文档族 + AI 评测集治理门禁「本地一键入口」。
#
# 状态（2026-11-10 复诊 P0-③ / D6 已闭合）：
#   - 真正的 ship-ready 判据是 .github/workflows/ci.yml 的三条 ProcuraAI* (blocking) 步骤，
#     以及 scripts/check.sh 的 G0-procura 快反馈块；本脚本**不再是 CI 的调用入口**。
#   - 本脚本保留给本地开发 / 评审前自查，命令与 CI 逐条对齐（含注入式负向反证）。
#   - 边界仍然诚实：全绿只代表「文档族一致 + 评测集治理骨架未退化」，
#     不代表采购模块已落地、SLO 已压测、模型效果已达标；评测集 status 仍为 seededDraft。
set -euo pipefail

# 定位仓库根（脚本位于 doc/ai-product/procuraAI/scripts/，上溯 4 层到仓库根 bone/）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
cd "$REPO_ROOT"

echo "[procura-gate] A1-A10 文档一致性门禁（分期真源 / InvoiceMatched 全族 / 合规清单 / 能力账本）"
python3 tools/check_procura_alignment.py

echo "[procura-gate] 注入式负向反证（防判据假绿：全部探针须转红且命中预期 key）"
python3 tools/check_procura_alignment.py --self-test

echo "[procura-gate] AsyncAPI 零漂移（附录 B 注册表 ↔ 契约工件）"
python3 doc/ai-product/procuraAI/detailed-design/contracts/generate_asyncapi.py --check

echo "[procura-gate] AI 评测集治理骨架（能力码双向追溯 / G15 偏见 / 关键负向）"
python3 doc/ai-product/procuraAI/scripts/check_procura_evalset.py

echo "[procura-gate] AI 评测集注入式负向反证"
python3 doc/ai-product/procuraAI/scripts/check_procura_evalset.py --self-test

echo "[procura-gate] 本地全绿；CI blocking 结果以 ci.yml 三条 ProcuraAI* 步骤为准（CI 绿 ≠ 可上线）"
