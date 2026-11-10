# 技术设计（tweak · 文档层）

## 基线

- 输入基线：`doc/architecture/Bone-AI原生工程基础设施改造方案.md` **v2（300 行）**。该版本在主工作树为 staged 未提交，已在 Open 前同步到本 worktree（依 Dirty Worktree 协议：归属明确，作为需求/设计输入纳入，不重做）。
- 唯一改动对象：该方案文档，以及 `doc/architecture/README.md` 的登记说明。
- 工作区：独立 worktree `.worktrees/ai-native-platform-plan-review`，分支 `comet/ai-native-platform-plan-review`，base `11f7e17af`。

## 方法

| 步骤 | 做法 | 约束 |
|---|---|---|
| 复核 | 逐条对照 OpenAI Harness / Anthropic Context Engineering / GitHub 分层指令 / SDD / 变更影响分析，输出判定表 | 基准固定：Bone 规范真源 > 业界实践 > 个人口味；不得覆盖已登记裁定 |
| 优化 | 5 项均为文档内增补，不新增工程资产 | 不建第二真源：D3 裁定、`verify.md`、`check_all.py` 既有定位一律复用 |
| 验收 | 复用现有只读门禁脚本 + 数字/引用复核 | 不为此新增 `check-*.py`；不接 CI |

## 关键取舍

- **不新增评测集文件**：只在方案中写「复用 `check-eval-baseline.py` 的 skeleton 态骨架」这一设计意图，避免把骨架误写成已落地能力（呼应 `_ai_native_readiness.py` 的既有教训）。
- **不生成路径级规则文件**：T7 只保留 `06 §12.2` 表内增列这一 L4 文档动作，避开既有「不入库」裁定。
- **不改 PR 模板**：归因勾选项仅作为方案建议，需另行 L3 审批才落地。
- **不缩减回归**：任何影响面提示仍是 report-only，高风险变更扩大回归。
- **不重复上轮审查**：v2 已三轮独立审查 Approve；本 change 做业界实践对照的增量复核，不重跑同等规模的独立审查。

## 风险与回退

| 风险 | 处理 |
|---|---|
| 文档数字 / 引用漂移 | 写后全文引用复核，重跑两个文档门禁并记录真实 RC |
| 与主工作树 staged 的 v2 分叉 | 本 worktree 独立分支；归档时只提交本 change 文件，主树改动保持不动 |
| 越权触碰 L3/L4 | 所有涉及 L3/L4 的内容只写成方案内的「建议 + 审批级别」，本 change 不落地 |
