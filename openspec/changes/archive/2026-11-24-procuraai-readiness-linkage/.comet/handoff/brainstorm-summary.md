# Brainstorm Summary — procuraai-readiness-linkage

## 已确认（来自用户与 Open 批准）

- 范围：就绪度加固 + 前后端联调（P0-1/P0-2/P0-6/P1-3/P1-4）。
- 验收强度：真实启动 + 浏览器 + E2E，写操作以落库为第二证据。
- 非目标：契约冻结、评测转正、Outbox/MQ、真模型、生产 DDL、业务聚合；这些有 owner/L3 前置。
- 工作区：当前 `dev`，isolation=current，不建分支。

## 候选方案（本次 design 提出，待确认）

1. 装配一致性：`AdvisoryController` + `AdvisoryApplicationService` 与治理 Bean 使用同一 `@ConditionalOnAiGovernanceEnabled`；双态 `ApplicationContextRunner` 探针。否决"无条件装配治理 Bean"与"ObjectProvider 兜底"，因为前者破坏配置关闭即无 AI、后者掩盖错误。
2. Mock 降级：在 `ContractReviewPortAdapter.review()` 的 `gateway.invoke()` 之后识别 mock 身份，追加 `MOCK_PROVIDER_REJECTED` trace 并复用 `degrade()`；不在网关全局拒绝（会破坏其他模块 Mock 联调）。
3. readiness 端点：新增只读 `AiReadinessApplicationService` 聚合 provider 配置 / 能力注册 / 总闸 / 评测四态；判词 AVAILABLE/DEGRADED/UNKNOWN，未知 fail-closed；分离"部署门禁通过"与"授标/放款许可"。
4. OCR 人工复核：在 `Qualification` 实体加幂等 `reviewByHuman`，服务层 findById→领域方法→save，替换 `UnsupportedOperationException`；新增 Controller 端点；不改 DDL。完整复核台账留 backlog。
5. 前端三页：AiReadiness / HumanReviewConsole / GovernanceStatus，复用现有 services/useMessage；未知码显示原始码、失败显示"无法确认"、AI 建议不渲染为已生效。
6. 验收三层：机器（mvn/tsc/vitest/eslint-json/门禁探针）、运行（真实启动后端+前端、浏览器操作）、数据（DB 直查/读回）。

## 风险与取舍

- 默认关闭 advisory 后前端入口不可达 → readiness 页常驻并解释原因。
- 本地 MySQL 需密码、无 H2 profile → 优先内存/H2 test profile，不擅改生产库或数据库账号。
- readiness 读 evalset 属跨目录只读 → 失败即 UNKNOWN，不写文档族、不触发 A10。
- OCR 复核不保留历史台账 → 以审计列 + 幂等为最小交付，完整台账留 backlog。
- 新增权限点若需 owner 登记 → 报告列遗留，不阻塞联调。

## 无 Spec Patch

行为需求已由 Open 阶段两份 delta spec 覆盖，design 不引入新的对外行为，无需改 delta spec。
