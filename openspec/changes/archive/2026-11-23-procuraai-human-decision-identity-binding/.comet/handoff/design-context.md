# Comet Design Handoff

- Change: procuraai-human-decision-identity-binding
- Phase: design
- Mode: compact
- Context hash: 3f4e3d38b4ddde00eac3c04356efa7650fe3dbff36f693d2a5be37c02bedd5e7

Generated-by: comet-handoff.sh
Task hash policy: task-content-v1. Read tasks.md for live completion; excerpts are design-time context.

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/procuraai-human-decision-identity-binding/proposal.md

- Source: openspec/changes/procuraai-human-decision-identity-binding/proposal.md
- Lines: 1-33
- SHA256: 6590da182934432926611ecaa1ab2294ece1297252015493617c94a045a05fc0

```md
# Proposal

## Why

`procurement/business-module-skeleton` 已规定“授标/准入必须具名人工终决”，但 HTTP 层存在自报身份：`POST /qualifications/onboardings/{no}/approve` 从请求体接收 `tenantId/operator`，`awardByHuman` 只有应用服务方法、没有真实 HTTP 入口。这样即使领域不变量存在，也无法证明人工终决由登录态操作人作出，审计链可被请求体伪造。需按生产就绪基线把两处终决绑定到登录态，并补齐与 OpenAPI 一致的授标端点。

## What Changes

- 修改 `QualificationController` 的 onboarding approve：租户与操作人统一从 `ReviewIdentityPort`/登录态解析；请求体自报值与登录态不一致即拒绝；匿名、缺租户、空白操作人一律 fail-closed。
- 在 `SourcingController` 新增与 OpenAPI 对齐的 `POST /api/v1/procurement/rfx/{rfxNo}/award`，权限使用既有 `procurement:rfx:approve`，不接受请求体 `confirmed_by`，由登录态提供具名操作人。
- 调整 `SourcingApplicationService.awardByHuman`：支持以可信的 `rfxNo` 定位项目并保留既有状态机/终态/空白操作人不变量；AI 结论仍不得直接置 AWARDED。
- 补正向与负向行为测试：具名登录态可终决；跨租户、冒名、匿名、空白操作人、非 RFx 对应项目均被拒绝且不落库。
- 前端仅在已有采购工作台需要时补最小联动，不改变人工终决“由人确认”的交互语义。

非目标：不改 DDL/CI 三入口；不接真实模型；不改评测集状态；不执行生产数据库操作；不扩展 Bid/Award/Quotation/CAPA 完整聚合。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `procurement/business-module-skeleton`: 把“具名人工终决”从领域方法约束提升为 HTTP 边界可验证要求；onboarding approve 与 RFx award 必须绑定登录态租户/操作人，禁止请求体自报。

## Impact

- 代码：`bone-procurement-qualification`、`bone-procurement-sourcing`、可能的 `bone-procurement-app` 安全适配器与对应测试。
- API：新增 `POST /api/v1/procurement/rfx/{rfxNo}/award`；onboarding approve 的 `tenantId/operator` 不再作为权威输入。
- 权限：复用已有 `procurement:rfx:approve`，不新增权限码或迁移脚本。
- 数据：不新增表、不改 DDL；写操作仍以持久化读回作为第二证据。
- 风险：若登录态缺少租户或操作人，相关人工终决端点必须返回明确拒绝，不得回退到请求体或默认租户。

```

## openspec/changes/procuraai-human-decision-identity-binding/design.md

- Source: openspec/changes/procuraai-human-decision-identity-binding/design.md
- Lines: 1-83
- SHA256: 77cc955d4d3901d4177d8747ed738ea29c8cecce9cb1526fad102949ac6a2982

[TRUNCATED]

```md
---
comet_change: procuraai-human-decision-identity-binding
role: technical-design
canonical_spec: openspec
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

```

Full source: openspec/changes/procuraai-human-decision-identity-binding/design.md

## openspec/changes/procuraai-human-decision-identity-binding/tasks.md

- Source: openspec/changes/procuraai-human-decision-identity-binding/tasks.md
- Lines: 1-23
- SHA256: f137d1f0d0c7e2ea97f7bf89638ba2bed74281b64b9e1811e6efa245b155f471

```md
# Tasks

## 1. 登录态身份端口与准入终决

- [x] 1.1 在 sourcing 域新增只读授标身份端口并在 infrastructure 实现认证上下文解析；用匿名/已认证两组单测验证返回 null 与具名操作人。 <!-- comet-task:864f4bcf-8752-4406-bf4b-1a03bf026b39 -->
- [x] 1.2 将 `POST /onboardings/{no}/approve` 改为从 `ReviewIdentityPort` 获取租户与操作人，请求体同名字段只做一致性校验；用跨租户、冒名、匿名三类 Web 层测试验证拒绝且不落库。 <!-- comet-task:d9c5d39e-c6a5-49ac-aece-12aaa635cdb1 -->

## 2. RFx 人工授标 HTTP 入口

- [x] 2.1 增加按 `rfxNo` 只读定位寻源项目的仓储查询，并让 `awardByHuman` 支持可信租户/操作人参数；用存在、不存在、空白操作人三类服务单测验证状态与不变量。 <!-- comet-task:fd36e285-917e-4c5a-9a6a-fd80dab94784 -->
- [x] 2.2 新增 `POST /api/v1/procurement/rfx/{rfxNo}/award`，复用 `procurement:rfx:approve`，确认人只取登录态；用具名成功、无权限/匿名、请求体冒名、AI 直接授标四类 Web 层测试验证 200/403/400 与持久化状态。 <!-- comet-task:049c3659-4076-452f-b24b-131a3b5057b5 -->
- [x] 2.3 同步 `openapi.yaml` 的授标端点说明为“确认人来自登录态”，删除权威 `confirmed_by` 语义；用现有契约/对齐门禁验证零漂移。 <!-- comet-task:c767ca7e-f854-438c-8434-5aadc10424b6 -->

## 3. 集成验收

- [x] 3.1 运行 qualification 与 sourcing 模块全量测试，验证无失败、无跳过回归，并确认所有写操作均有持久化读回或拒绝后状态不变的断言。 <!-- comet-task:49853118-45ea-45c2-b8ac-c5b53f0a1e10 -->
- [x] 3.2 运行采购前端 TypeScript、vitest、ESLint JSON 门禁，验证本 change 未破坏前端构建与行为测试。 <!-- comet-task:b9bf88be-83fc-45cc-885f-69ccbbde99c1 -->
- [x] 3.3 运行 procuraAI 对齐、evalset、AsyncAPI、persistence 门禁，验证 OpenAPI 文案同步未触发 A8/A10 契约漂移。 <!-- comet-task:b5b76d5b-7e13-4112-895a-41f010ea1a74 -->

## Workflow follow-up

- 完成 Comet Verify 的唯一最终集成审查后再归档。
- 仅在用户明确授权时执行提交与推送。

```

## openspec/changes/procuraai-human-decision-identity-binding/.openspec.yaml

- Source: openspec/changes/procuraai-human-decision-identity-binding/.openspec.yaml
- Lines: 1-2
- SHA256: 01c3365f21ad2129d7e1db5577bc59c382883cdcbfece60900a8ac86293e3398

```md
schema: spec-driven
created: 2026-11-23

```

## openspec/changes/procuraai-human-decision-identity-binding/specs/procurement/business-module-skeleton/spec.md

- Source: openspec/changes/procuraai-human-decision-identity-binding/specs/procurement/business-module-skeleton/spec.md
- Lines: 1-33
- SHA256: 8f4a146445e63d15800270972f1d5909a70bc1bd3308e2f0d54e9be6574acfcc

```md
# Spec Delta

## ADDED Requirements

### Requirement: 人工终决身份必须来自登录态
系统在 HTTP 边界执行准入人工终决或人工授标时，SHALL 只接受经过认证的主体提供的租户与操作人身份。请求体自报的租户、操作人或确认人 MUST NOT 成为权威值；与登录态不一致时 MUST 拒绝且不得产生任何状态写入。

#### Scenario: 请求体冒名准入终决被拒
- **WHEN** 已登录用户提交 onboarding approve，请求体中的 `operator` 或 `tenantId` 与登录态不一致
- **THEN** 请求被拒绝，准入单状态与审计记录保持不变

#### Scenario: 匿名或缺少租户时准入终决被拒
- **WHEN** 未认证主体或无法解析租户/操作人的主体提交 onboarding approve
- **THEN** 请求被拒绝，系统不得使用默认租户或请求体身份继续处理

#### Scenario: 请求体冒名授标被拒
- **WHEN** 已登录用户请求 RFx 人工授标，请求体中的确认人与登录态不一致
- **THEN** 请求被拒绝，项目状态保持非 AWARDED，不记录该冒名操作人

### Requirement: 人工授标必须具备可审计的 HTTP 入口
系统 SHALL 提供 RFx 人工授标的 HTTP 入口，并要求调用方具有既有 RFx 审批权限。入口 MUST 使用登录态操作人作为授标确认人；AI 结论、空操作人和非授权主体 MUST NOT 使项目进入 AWARDED。

#### Scenario: 具名审批人授标成功
- **WHEN** 具有 RFx 审批权限的登录态操作人对有效 RFx 请求人工授标
- **THEN** 对应项目进入 AWARDED，并记录登录态操作人为确认人

#### Scenario: 无权限或匿名授标被拒
- **WHEN** 无 RFx 审批权限的主体或匿名主体请求人工授标
- **THEN** 请求被拒绝，项目状态不变，不产生授标审计记录

#### Scenario: AI 结论不得直接授标
- **WHEN** 系统仅依据 AI 建议且没有具名人工确认
- **THEN** 项目不得进入 AWARDED，并返回明确拒绝原因

```
