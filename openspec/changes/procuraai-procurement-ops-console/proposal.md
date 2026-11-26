# Proposal：procuraAI 采购运营工作台补齐

## Why

procuraAI 的交易、履约、合同审查、AI 就绪度、人工复核与治理态势已有页面，但寻源、准入、风险绩效仍没有可操作的浏览器入口；S2P 业界实践强调从寻源到授标、从供应商证照到风险处置的连续可追溯操作。前端缺位使“100% 人工终决”只能靠 API 调用，无法形成可审计的操作轨迹，也无法实测人工确认耗时。

## What Changes

- 新增寻源工作台页面：RFx / 投标 / 报价比较 / 授标记录只读呈现，人工授标操作只携带身份一致性校验字段，不自行裁决。
- 新增供应商准入与风险页面：证照 OCR 人工复核、准入终决、风险预警处置、绩效只读看板。
- 把现有 `/contract-hitl`、`/human-review`、`/ai-readiness`、`/governance` 入口串联为统一导航；复用既有后端端点与登录态身份，不新增前端旁路。
- 统一 fail-closed 呈现：`success=false`、403、404、未知状态码与取数失败均不默认显示“正常/全部通过”；未知码显示原始码，失败显示“无法确认”。
- 用前端自动化测试覆盖请求形状、权限降级、未知码与失败态，不把 HTTP 2xx 当成功。

## Capabilities

### New Capabilities

- `procurement/sourcing-console`: 寻源、投标、报价比较与授标记录的浏览器操作与可观察降级行为。
- `procurement/supplier-risk-console`: 证照 OCR 复核、准入终决、风险预警处置与绩效只读看板的浏览器操作行为。

### Modified Capabilities

- （无。合同 HITL、人工复核、AI 就绪度与治理态势页面只做入口复用，不改变其规格要求。）

## Impact

- **前端**：在 `bone-frontend/apps/bone-procurement-app` 增加路由、页面、API 客户端与组件测试；沿用 `@bone/ui` 与现有权限点映射，不新增权限种子。
- **后端**：只消费现有端点与 `procuraai-business-aggregates-completion` 提供的只读/具名人工端点；本 change 不改 Java 代码、DDL 或 CI 判据集合。
- **依赖**：`procuraai-business-aggregates-completion` 提供投标/报价/授标/CAPA 的端点；寻源页面在依赖完成前可先完成静态结构，但端到端验收以依赖到位为准。
- **非目标**：不做真实模型效果展示，不改评测/契约冻结状态，不执行生产库操作，不扩大 CI 三入口判据集合。
