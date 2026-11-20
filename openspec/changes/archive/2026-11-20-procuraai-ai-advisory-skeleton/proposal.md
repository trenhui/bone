# 提案：procuraAI 业务 AI 建议骨架（批次5 纯工程）+ 红线 backlog 固化

## Why

前四批已交付合同审查、AI 决策治理、F3/F4/G15 安全护栏、业务模块骨架 + 核心 DDL，但 M1/M5/M7/G10 等业务 AI 仍是零实现。源码实证：03 卷 §207 明确 AI 契约真源「待 D3-v2 冻结」，`AI接入判定与AI原生内核补充设计.md` 声明「不宣称任何能力已实现」，evalset 仍 27 例 seededDraft（扩容需 owner 双人标注 / Kappa≥0.8 / 通过线冻结）。接真实模型并声明效果会越过 owner 红线。故本批只落地**纯工程建议态骨架**，并把 owner/L3 项固化进决策登记册。

## What Changes

- **纯工程 AI 建议模块 bone-procurement-ai-advisory**：8 个能力码（D3-v2 §1.1 登记口径）+ 三态 fail-closed（未登记/停用/降级/总闸关闭一律 UNKNOWN + rules_only）+ 双标识披露 + 证据只给引用不给 output；2 个 REST 端点（能力清单 / 建议态裁决），17 例测试（含 8 条架构规则）。**不注入 LLM 网关、不产生模型输出、不声明效果达标**。
- **复用治理护栏**：建议态裁决委托 governance 的 `decide()` + 全域 kill switch `globalAllowsAi()`，不另建待冻结的 `ProcurementAiOrchestrationPort`。
- **契约登记**：openapi.yaml 新增 ai-advisory tag + 2 端点，A10 双向豁免（六卷 §6 待 owner 对齐），负向探针验证豁免有效。
- **权限种子 0131**：`procurement:ai-advisory:read`（960 号段），清单 order 135。
- **backlog 固化**：DECISION-REGISTER 新增 D-15（evalset 27→200 转正）/ D-16（D3-v2 契约冻结）/ D-17（真模型接入与效果声明）/ D-18（自动放款/授标红线确认），明确 owner 依赖，不工程实现、不伪造证据。

**非目标**：真实模型调用、效果达标声明、evalset 扩容、D3-v2 契约冻结、六卷 §6 端点对齐——均在 DECISION-REGISTER 保持 `Proposed` 等 owner。

## Capabilities

### New Capabilities

- `procurement/ai-advisory-skeleton`: 业务 AI 建议态的可观察行为——三态 fail-closed、双标识披露、rules_only 降级、全域总闸联动、AI 仅建议人工终决，且明确不接模型不声明效果。

### Modified Capabilities

- （无。backlog 固化只更新决策登记册，不改已归档的治理/安全 spec。）
