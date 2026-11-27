# Proposal

## Why

procuraAI 的 AI 决策治理骨架已落地（能力登记、HITL 人工终决、override 回流、kill switch、披露双标识），但金监总局《关于银行业保险业人工智能安全开发应用的指导意见》§6/§9 要求的「漂移监测闭环」与业界 STP/override 率指标仍只有计数输入（`countOverrides`），没有受控判词与只读观测面。没有判词就无法在样本不足或阈值未配置时 fail-closed，容易被误读成"模型健康"。

## What Changes

- 新增**漂移信号**领域值对象与纯函数裁决：三态判词（UNDETERMINED / NO_ACTIONABLE_SIGNAL / REVIEW_REQUIRED），基于人工终决台账与追溯链的真实计数。
- 阈值未配置、样本不足、计数非法一律判 UNDETERMINED；触阈仅表示"需 owner 复核/评测扩样"，**任何判词都不声明模型效果达标**。
- 新增只读治理指标投影与 HTTP 端点（read 权限），来自现有台账/追溯的真实计数，不引入新 DDL、不落库、不改能力状态。
- 新增领域单测、应用服务负向测试、HTTP 契约测试与内部模拟 POC 验证报告。

## Capabilities

### New Capabilities

- `procurement/ai-drift-metrics`: AI 决策漂移信号与治理指标的只读观测能力——定义三态判词、fail-closed 规则与只读指标的对外行为。

### Modified Capabilities

（无）

## Impact

- 代码：`bone-procurement-ai-governance` 模块新增领域值对象/领域服务、应用只读方法、Controller 只读端点及测试。
- 数据：仅读取现有 `t_proc_human_decision` / `t_proc_governance_trace`（经端口），不新增表、不改 `bone-init.sql`。
- 门禁：不改 A10.17 三入口判据集合、不改 evalset 状态、不改 CI 配置。CI 绿只证明结构与 fail-closed 不变量成立，不代表模型效果达标。
- 权限：新端点使用现有 `procurement:ai-governance:read` 权限码。
