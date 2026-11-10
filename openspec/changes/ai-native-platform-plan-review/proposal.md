# 提案：AI 原生工程基础设施改造方案的业界实践对照复核与优化

## Why

《Bone AI 原生工程基础设施改造方案》v2 已通过三轮独立审查（3 → 2 → 2 只全新子 Agent，最终 Approve），方向与 Bone 规范真源一致。但按 OpenAI Harness Engineering、Anthropic Context Engineering、GitHub 分层指令、SDD 与变更影响分析等业界实践做增量对照后，仍发现 5 处「方向对、但缺可复算落点」的缺口。用户要求复核并对合理建议做优化，故启动本 change。

目标是把「评审认为合理」升级为「可复算、可验收、可交接」的文档结论；不重新设计架构、不改工程代码。

## What Changes

- 复核 v2 的 9 项改造（T1–T9）与不改造清单，在方案内新增「业界实践对照复核」章节，给出「合理 / 可优化 / 不采纳」判定及依据。
- 补 5 项业界实践对齐（**均为文档层增补，不新增工程资产**）：
  1. **上下文质量验收**：复用 `scripts/check-eval-baseline.py` 的 E1–E5 骨架，给 T2 / T6 补「检索命中率」评测集的 skeleton 态设计意图，明确不编造实测指标。
  2. **运行时证据字段**：T4 影响面报告补「运行时 Trace / 事件订阅」占位与确认口径，引用 `doc/architecture/Bone-可观测性规范.md` §3 / §4 / §5。
  3. **AI 产出归因**：T6 给出最小可复算数据源（`.github/PULL_REQUEST_TEMPLATE.md` 已有「AI 辅助产出」勾选项旁增加 2 个可选勾选项的**建议**），并标注需 L3 审批才落地，本 change 不改 PR 模板。
  4. **路径级约束的零成本部分**：把 T7 拆出「在 `doc/agents/06 §12.2` 路由表增列『必查约束』」这一 L4 文档内动作；不生成 `.github/instructions`、不触碰「多平台 skills/rules 本机生成、不入库」的既有裁定。
  5. **验收命令清单**：把各 T 验收汇总为复用现有只读门禁脚本的一次性检查输入，不新增 `check-*.py`、不接 CI。
- 附录同步 v3 修订记录；`doc/architecture/README.md` 登记说明同步。

**不变更**：任何工程代码、`scripts/check-*.py`、`.github/workflows/ci.yml`、`AGENTS.md`、`doc/agents/06`、`.gitignore`、`.claude/settings.json`；不改产品行为、API、schema、数据库；不缩减回归测试集；不改变 T3 / T7 的既有裁定与 L4 边界；不复制 HC 表或门禁状态。

## Capabilities

本 change 为纯文档复核与优化，无 spec 级行为变更；已在本 change 的 `.openspec.yaml` 设置 `skip_specs: true`。

### New Capabilities
- 无

### Modified Capabilities
- 无

## Impact

- 预计影响 2 个文件，均为 `doc/` 下 Markdown：`doc/architecture/Bone-AI原生工程基础设施改造方案.md`、`doc/architecture/README.md`（仅登记说明）。
- 不影响运行时行为、构建、CI 门禁结果、数据库、API 契约、多租户与安全边界。
- 验收：`python3 scripts/check-doc-code-symbols.py` 与 `python3 scripts/check-ddd-doc-drift.py` 实跑 RC=0；方案内数字与引用复核无漂移；`openspec validate` 通过。
