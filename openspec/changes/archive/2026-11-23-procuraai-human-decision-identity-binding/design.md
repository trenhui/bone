---
comet_change: procuraai-human-decision-identity-binding
role: technical-design
canonical_spec: openspec
archived-with: 2026-11-23-procuraai-human-decision-identity-binding
status: final
---

# Design

## Context

现有领域层已经把“具名人工终决”写成不变量，但 HTTP 边界没有完全遵守：onboarding approve 从请求体读取 `tenantId/operator`，RFx 授标只有应用服务方法、没有 OpenAPI 已声明的 HTTP 入口。证照 OCR 复核已经建立了 `ReviewIdentityPort` + `SecurityContextReviewIdentityPort` 的可信身份解析模式，可作为 onboarding 的复用基线；RFx 授标应在本域定义端口，避免 qualification 与 sourcing 形成领域依赖。

## Goals / Non-Goals

**Goals:**

- 让 onboarding approve 与 RFx award 的租户/操作人只来自登录态。
- 提供与 OpenAPI 路径、权限码一致的 RFx 人工授标入口。
- 用可重复执行的正向/负向测试证明冒名、匿名、跨租户、AI 直接授标均失败且不落库。

**Non-Goals:**

- 不新增 DDL、表字段、迁移脚本或 CI 判据。
- 不新增权限码，复用 `procurement:rfx:approve` 与 `procurement:onboarding:approve`。
- 不实现完整 Bid/Award 聚合、真实模型接入或前端寻源工作台。
- 不执行生产数据库操作。

## Decisions

### 1. onboarding approve 复用 `ReviewIdentityPort`

复用 qualification 模块已有的登录态身份端口，控制器在调用应用服务前解析 `tenantId/reviewer`；请求体若带同名字段，只做一致性校验，不参与赋值。

备选方案：继续信任请求体。风险是任何能提供合法权限的调用方都能冒名终决，违反审计要求，故不采用。

### 2. sourcing 新增本域 `AwardIdentityPort`

在 sourcing 域定义只读身份端口，基础设施实现从认证上下文解析操作人。选择本域端口而不是直接依赖 qualification，是为了避免两个限界上下文通过身份实现产生编译期耦合；两端都遵守同一安全语义即可。

备选方案：直接复用 `ReviewIdentityPort`。虽然减少一个接口，但会把准入域概念泄漏到寻源域，长期维护成本高。

### 3. 授标端点以 `rfxNo` 为路径键

HTTP 路径采用 `POST /api/v1/procurement/rfx/{rfxNo}/award`，与 OpenAPI 已登记路径一致。`Rfx` 持有 `projectId`，但仓储端口当前只有 `findRfxByNo/findByProjectNo`，没有按 id 定位项目的方法。因此在 `SourcingProjectRepository` 端口新增只读 `findProjectByRfxNo(tenantId, rfxNo)`：Adapter 先 `findRfxByNo` 取 `projectId`，再以 tenant 锚定定位项目；`rfxNo` 不存在或不对应项目时返回 400，状态不变。

备选方案：使用 `projectNo`。虽然应用服务现成，但与公开契约不一致，会造成 A8 契约漂移，故不采用。

### 4. 请求体确认人字段仅作一致性校验，不作为权威输入

确认人只从登录态取值落库。请求体可携带 `confirmed_by`/`operator`/`tenantId`，但仅在与登录态不一致时用于拒绝（防误用与冒名），绝不参与赋值；字段缺省或留空不构成拒绝理由。OpenAPI 中“缺 confirmed_by”的描述同步改为“确认人来自登录态，冒名/匿名被拒”，不改变状态码集合：权限不足由框架 403，参数/身份不一致 400，状态冲突 409。

备选方案：把请求体字段作为权威确认人。任何有权限的调用方都能冒名终决，违反审计要求，故不采用。

### 5. 不增加数据库结构

所有状态写入继续使用现有 `SourcingProjectRepository` / `QualificationRepository`；只增加查询与状态迁移调用，不新增表或列，不触碰 `bone-init.sql` 的生产迁移边界。

## Risks / Trade-offs

- [风险] 现有 E2E/测试夹具可能使用匿名或请求体 operator，改造后会收到 400。→ 缓解：同步更新测试夹具为带真实登录态身份；负向测试保留冒名与匿名场景。
- [风险] `rfxNo` 到项目的映射若缺失会导致正确审批人无法授标。→ 缓解：仓储层增加按 `rfxNo` 只读定位测试，并覆盖不存在编号的 400 场景。
- [风险] OpenAPI 与设计口径不一致会触发契约门禁。→ 缓解：同一 change 内同步更新 `openapi.yaml` 的授标说明，不改判据集合。
- [权衡] 新增一个 sourcing 本域身份端口会增加少量接口代码，但保留限界上下文边界，优于跨域复用。

## Test Strategy

- 正向：具名登录态操作人可对有效 RFx 人工授标（项目进入 AWARDED 并记录登录态操作人）、可完成 onboarding 人工终决。
- 负向：请求体冒名、匿名、跨租户、空白操作人、`rfxNo` 不存在或不对应项目、AI 结论直接授标均被拒绝，且状态不变、无审计写入。
- 仓储只读定位：`findRfxByNo`→`projectId`→项目的 tenant 锚定映射单测，含不存在编号的 400 场景。
- 门禁：qualification 与 sourcing 模块全量测试、前端 tsc/vitest/eslint(json)、procuraAI 对齐/evalset/AsyncAPI/persistence 门禁。

## Migration Plan

无需数据库迁移或灰度开关。按以下顺序发布与回滚：

1. 先合入身份端口、控制器校验与 OpenAPI 文案同步。
2. 更新测试夹具与 E2E 身份上下文后执行模块测试和前端门禁。
3. 回滚时恢复控制器为旧行为会重新引入自报身份风险，因此不建议回滚到旧 HTTP 语义；如需紧急停用，应通过现有权限/路由配置关闭授标入口，而不是恢复请求体自报。

## Open Questions / 范围外已知缺口（转后续 change 或 owner）

- **截标状态推进缺口（已知、本 change 不处理）**：现有生产代码只有 `createProject`（DRAFT）与 `publishRfx`（仅改 `Rfx.status`），没有任何生产调用方把 `SourcingProject` 从 `DRAFT/PUBLISHED` 推进到 `BIDDING_CLOSED`；`closeBidding()` 仅在单测中出现。因此在真实链路上新建 RFx 后直接授标会命中状态机 409，spec 正向场景“具名审批人授标成功→AWARDED”只能在已处于 `BIDDING_CLOSED` 的项目上达到。按 LLD §寻源状态表，`due_reached`（系统时钟 ≥ due_at）触发截标属于“Bid/Award/Quotation/CAPA 聚合落库与契约冻结”范围，是本 change 明确 Non-Goal，也受 owner/L3 契约冻结约束，不以在 `awardByRfxNo` 内隐式推进状态的方式绕过。本 change 的身份绑定与 fail-closed 语义在该前置状态满足时完整成立。
- 调整权限码或新增前端寻源工作台会改变本 change 范围，需另行决策。
