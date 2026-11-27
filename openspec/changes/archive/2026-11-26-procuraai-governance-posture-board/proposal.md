# 提案：procuraAI 治理态势聚合看板

## Why

procuraAI 的 AI 治理后端已相当完整：全域 kill switch、能力三态 fail-closed 准入、具名责任人、一键停用/降级、人工终决与 override 回流、就绪度判词、漂移信号三态、监督有效性三态、合同审查 AI 参与披露。但这些信号分散在不同模块与端点，前端治理页 `GovernanceStatus.tsx` 仍只展示就绪度一项，形成「后端有护栏、运维与审查者看不到」的可观测断点。监管《关于银行业保险业人工智能安全开发应用的指导意见》§15 应用清单、§16 高风险审批与 §21 标识告知留存都要求治理状态可被持续查看与复核；NIST AI RMF 的 MEASURE/MANAGE 与 ISO 42001 也要求治理信号可聚合、可追溯。本轮把已就绪的只读信号统一聚合呈现，不新增任何自动决策能力，不改变人工终决红线。

## What Changes

- 新增只读聚合端点 `GET /api/v1/procurement/ai-governance/posture`，在 ai-governance 模块内聚合「能力清单 + 每能力裁决 + 漂移三态 + 监督三态 + 全域 kill switch + 汇总态势判词」；需要 `procurement:ai-governance:read` 权限，治理开关关闭时端点不注册（fail-closed）。
- 新增领域聚合判词 `PostureVerdict`（`UNDETERMINED` / `ALL_CLEAR` / `ATTENTION_REQUIRED`）与聚合视图 `GovernancePostureView`：任一分项不可判定即整体不可判定，任一能力 `REVIEW_REQUIRED` 即整体需关注；阈值仍只由 owner 服务端配置提供，不接受客户端注入。
- 前端 `GovernanceStatus.tsx` 由单卡片升级为治理态势看板：整体判词卡 + 能力明细表（状态/裁决/漂移/监督四列三态 Tag）+ 全域总闸 + 就绪度 + 合同审查披露入口；未知判词显示原始码，取数失败显示「无法确认」，不压成「正常」。
- 新增前端 `governanceApi.ts` 与契约类型，配套单测、后端领域/应用/契约测试与静态门禁复验。
- **边界（诚实呈现）**：合同审查 AI 参与披露是 per-review 端点，聚合看板不虚构全局披露状态；仅提供到已有披露页的入口。本轮不接真实模型、不声明模型效果达标、不改 owner 冻结的阈值与评测集下界、不改任何 CI 三入口判据集合。

## Capabilities

### New Capabilities

- `procurement/ai-governance-posture`: 只读聚合治理态势的领域判词、后端聚合端点契约与前端诚实渲染要求（fail-closed、三态、无客户端阈值注入）。

### Modified Capabilities

- 无。现有 `ai-readiness` / `ai-drift-metrics` / `ai-oversight-effectiveness` / `ai-decision-governance` / `human-review` 等行为不变，本轮只新增聚合视图，不修改其需求。

## Impact

- 后端：`bone-apps/bone-procurement/bone-procurement-ai-governance`（新增领域聚合判词/视图、应用聚合方法、控制器端点、权限与开关复用）+ 对应测试。
- 前端：`bone-frontend/apps/bone-procurement-app`（GovernanceStatus 页改造、governanceApi 与类型、单测）。
- 规格：`openspec/specs/procurement/ai-governance-posture/spec.md`（归档后新增主 spec）。
- 依赖：仅复用已有 `AiGovernanceApplicationService` 的 capability/decide/drift/oversight 能力与 `readinessApi`；不新增跨模块服务依赖、不改 DDL、不改 CI 判据集合。
