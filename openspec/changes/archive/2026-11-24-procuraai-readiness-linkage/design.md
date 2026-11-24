---
comet_change: procuraai-readiness-linkage
role: technical-design
canonical_spec: openspec
archived-with: 2026-11-24-procuraai-readiness-linkage
status: final
---

# Design：procuraAI 就绪度加固与前后端联调

## Context

见 `proposal.md` 的 Why。`procuraai-production-hardening` 已把仓储切到 SDK 持久化并通过门禁，但运行期仍有四处断点：

- `AdvisoryController`（`:13-21`）无条件注册并强依赖 `AdvisoryApplicationService`，后者强依赖 `AiGovernanceApplicationService` 与 `SafetyApplicationService`（`AdvisoryApplicationService.java:20-27`）；这两个治理 Bean 受 `@ConditionalOnAiGovernanceEnabled`（`matchIfMissing=false`）控制。默认配置下控制器依赖的 Bean 是否存在，取决于治理开关是否打开，存在装配不确定。
- `ContractReviewPortAdapter.review()`（`ContractReviewPortAdapter.java:79-105`）已做注入/PII/JSON 护栏，但未识别 provider 身份；未配 `bone.ai.gateway.base-url` 时网关注入 `MockLlmClient`，其 `mock:true` 回显会被 `parseSuggestion` 当作正常结论解析。
- 没有任何只读端点把配置、总闸、能力注册、评测状态拼成可判定判词。
- `QualificationApplicationService.reviewOcrByHuman`（`:35-38`）直接抛 `UnsupportedOperationException`。
- 前端 `App.tsx` 只有 `/transactions`、`/suppliers`、`/fulfillment`、`/contract-review` 四条路由（`:26-32`），无可操作的人工终决/治理页。

现有规约：`决策` 三态 + `AiAvailability`、总闸即时生效、PII 禁入、双标识（显式 + 隐式）、只读证据引用（不回传 output 原文）、人工终决；诊断报告已认定 G1–G16/F1–F4 与 D-08/D-15/D-16/D-17 属 owner 前置，本轮不碰。

## Goals / Non-Goals

**Goals:**

- 默认配置与开启配置下都能明确判定装配状态，不出现"控制器在、依赖 Bean 不在"。
- Mock 身份被识别并降级为 `UNKNOWN` + 强制人工；未配置 provider 有显式提示。
- 以只读端点公示 AI 就绪度，判词可被前端诚实渲染。
- OCR 人工复核可具名、幂等、跨租户安全地写入并读回。
- 前端有可操作的就绪度/终决/治理页，并用真实浏览器完成一次端到端联调且以落库为第二证据。

**Non-Goals:**

- 不接真实模型、不改 evalset 下界与 `seededDraft` 状态、不冻结契约。
- 不新增 Outbox/Inbox/MQ、不执行真实生产库 DDL、不改 `bone-init.sql` 中现有表结构。
- 不实现 Award/Bid/Quotation/CAPA 聚合、不新增 Gx 能力卡片、不迁旧族 8 份文档。
- 不把"部署门禁通过"等同于授标/放款许可。

## Decisions

### D1：装配一致性用"同一条件 + 启动探针"，而不是放宽条件

在 `AdvisoryController` 和 `AdvisoryApplicationService` 上与治理 Bean 使用同一个 `@ConditionalOnAiGovernanceEnabled`，使三者同生共死；同时保留"关闭配置下 /ai-advisory 端点不注册"的行为。用 `ApplicationContextRunner` 做双态探针：默认关闭时 advisory 相关 Bean 均不存在；开启时恰好各一个且依赖可满足。

- 备选 A：把治理 Bean 改为无条件装配 → 破坏"配置关闭即无 AI"的合规约束，否决。
- 备选 B：在 advisory 内用 `ObjectProvider` 空依赖兜底 → 掩盖装配错误，否决。
- 取舍：端点在关闭时不可用是预期行为；就绪度端点始终可用，用于解释为什么不可用。

### D2：Mock 身份在合同审查端口内识别，复用现有降级通道

在 `ContractReviewPortAdapter.review()` 的 `gateway.invoke()` 之后、`parseSuggestion` 之前插入身份检查：`response.model()` 或结构化标识命中 mock（例如 `mock`、`mock-llm`、response 中 `mock:true`）时，追加 `ReviewTraceEntry`（原因码 `MOCK_PROVIDER_REJECTED`）并返回现有 `degrade()`（UNKNOWN + 强制人工），不新增异常类型、不入域、不计入参与率。同时把"provider 未配置"作为独立事实输入 readiness。

- 备选：在网关层全局拒绝 mock → 影响其他依赖 Mock 做本地联调的模块，否决。
- 取舍：只在采购高风险链路拒绝，保留 Mock 在其它模块的联调价值。

### D3：readiness 端点用只读聚合，事实来源现成且彼此独立

新增 `AiReadinessApplicationService`（只读），聚合四类事实：

1. provider 配置态：`bone.ai.gateway.base-url` 是否配置、当前客户端是否 mock。
2. 能力注册态：治理能力注册表中已启用能力数。
3. 总闸态：`SafetyApplicationService.globalAllowsAi()`。
4. 评测态：读取 `ai-decision-evalset.json` 的 `status` 与 `needsOwnerReview`（只读，不改），仅用于声明"未转正不得声称效果达标"。

判词规则：任一事实未知 → `UNKNOWN`；provider 未配或总闸关闭或评测未转正 → 至少 `DEGRADED` 且绝不 `AVAILABLE`；全部通过 → `AVAILABLE`。响应带 `deploymentGatesPassed` 与 `authorizationNotice` 明确"门禁通过 ≠ 授标/放款许可"。Controller 用 `@PreAuthorize("hasAuthority('procurement:ai-readiness:read')")` 与现有权限风格一致，错误走 `ApiResponse` 统一包装；未知判词原样透传。

### D4：OCR 人工复核走"实体自我状态迁移 + 现有仓储 save"

`Qualification` 实体新增 `reviewByHuman(reviewer, accurate, now)` 领域方法（幂等：已复核则复用原结论/版本，不产生新记录），`QualificationApplicationService.reviewOcrByHuman` 改为 `findById` → 领域方法 → `repository.save`，缺租户/越权 fail-closed。新增 Controller 端点（如 `POST /api/v1/procurement/qualifications/{id}/ocr-review`）与幂等键字段，配套持久化读回测试与跨租户负向测试。

- 备选：新建独立复核表 → 本轮 DDL 冻结且无必要，否决。
- 取舍：复用 `Qualification` 现有持久化，字段落在既有记录上，避免改 `bone-init.sql`。

### D5：前端新增三页，复用现有请求封装与反假绿约定

在 `bone-procurement-app` 新增 `AiReadiness`（只读卡片，判词/事实/提示）、`HumanReviewConsole`（待决列表 + 采纳/驳回 + 读回）、`GovernanceStatus`（总闸/能力态）三页并挂路由。沿用 `useMessage`、现有 services 的 baseURL/租户头；封装修订失败显示"无法确认"、未知码显示原始码、不把 `false` 显示成达标。AI 披露信息复用现有 `ContractReviewDisclosure` 的展示约定，不重复造组件。

### D6：验收证据分层，避免"2xx 即成功"假绿

- 机器层：`mvn -o -f <module>/pom.xml test`、前端 `tsc` + `vitest` + `eslint -f json` 解析 `errorCount`、主门禁与自检探针。
- 运行层：本地真实启动 `ProcurementApplication`（H2/内存或本地 MySQL 配置）与前端 dev server；用浏览器打开 readiness 页、触发一次合同审查建议、在终决工作台执行采纳/驳回并刷新。
- 数据层：对写操作以数据库直查或 `findById` 读回作第二证据；Mock/未配置 provider 场景下断言不出现 `AVAILABLE`。

## Risks / Trade-offs

- [默认关闭 advisory 后前端菜单入口不可达] → 就绪度页始终可用并解释原因；页面对关闭态显示"未启用/未配置"而非空白。
- [本地无 H2 profile、MySQL 需密码] → 联调优先用内存/H2 配置（若模块无 H2 依赖则新增 test profile，不改生产配置）；若必须用本地 MySQL，先确认可用的本地账号，不擅自改数据库用户或生产库。
- [readiness 读 evalset 文件耦合文档族路径] → 只读且失败即 `UNKNOWN`，不写文档族、不触发 A10 门禁；文件路径用可配置常量，缺省指向仓库内 evalset。
- [OCR 复核只改既有记录，无法保留复核历史] → 本轮以幂等 + 审计列（复核人/时间）为最小可交付；完整复核台账列入 backlog，不伪造。
- [新增端点需要权限点] → 使用与现有能力一致的 `procurement:*:read` 命名，IAM 权限点登记若需 owner 授权则在报告中列为遗留，不阻塞联调。

## Migration Plan

无数据迁移、不改生产 DDL。部署时：默认配置下 advisory 与治理保持关闭、readiness 只读端点可用；开启 AI 时按现有开关同时打开治理与合同 AI 并配置真实 provider。回滚：移除新增端点与页面即可，既有持久化数据不变。

## Open Questions

无影响规格/方案的问题。以下为刻意留给 owner、不阻塞本 change 的事项：D-08 法务结论、D-15 评测转正 200 例、D-16/D-09 契约冻结、D-17 真模型接入、D-05 跨域 ACL 与 Outbox/MQ。
