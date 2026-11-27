# Comet Design Handoff

- Change: procuraai-procurement-ops-console
- Phase: design
- Mode: compact
- Context hash: fbad28322252fb227815d54ed6f029a0819078b251830e3f00be6cf8311e7ac6

Generated-by: comet-handoff.sh
Task hash policy: task-content-v1. Read tasks.md for live completion; excerpts are design-time context.

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/procuraai-procurement-ops-console/proposal.md

- Source: openspec/changes/procuraai-procurement-ops-console/proposal.md
- Lines: 1-31
- SHA256: fd86989627862a2eed6c47d2d63ea8c7a84f8088d4edfa6039f7f9c416fe162e

```md
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
- **后端**：只消费现有端点与 `procuraai-durable-business-aggregates-rebaseline` 已交付的只读/具名人工端点（`/api/v1/procurement/business/*`）；本 change 不改 Java 代码、DDL 或 CI 判据集合。
- **依赖**：`procuraai-durable-business-aggregates-rebaseline` 已交付投标/报价/授标/CAPA 端点并 push；寻源页面按其真实响应形状实现，取数失败走 fail-closed 降级，不默认显示成功。A 方案 `procuraai-business-aggregates-completion` 保留草稿，不作依赖。
- **非目标**：不做真实模型效果展示，不改评测/契约冻结状态，不执行生产库操作，不扩大 CI 三入口判据集合。

```

## openspec/changes/procuraai-procurement-ops-console/design.md

- Source: openspec/changes/procuraai-procurement-ops-console/design.md
- Lines: 1-57
- SHA256: 29383ffa39279f59778e6457c8cf0c4110de3b224eff8a4aa813d746426275c2

```md
# Design：procuraAI 采购运营工作台补齐

## Context

见 `proposal.md`。采购前端已有交易、履约、供应商主数据、合同审查/HITL、AI 就绪度、建议、人工复核与治理态势路由（`App.tsx`），但寻源、准入、风险绩效没有可操作页面。现有模式是 `pages/*.tsx` + 纯映射 `*.view.ts` + `services/*Api.ts`，以 `ApiResponse.success` 而非 HTTP 2xx 判定结果，未知码显示原始码，失败显示“无法确认”。本设计沿用这些模式，只消费后端端点，不改 Java 代码。

## Goals / Non-Goals

**Goals:**

- 让寻源—报价比较—人工授标与准入—风险处置—绩效查看在浏览器里可操作、可追溯。
- 在前端维持 fail-closed：冒名/匿名/未知/取数失败均不默认显示成功或全部正常。
- 为后续人工终决耗时与申诉回流提供可观察入口，但不伪造指标。

**Non-Goals:**

- 不新增后端端点、DDL、权限种子或 CI 判据集合。
- 不把 `rules_only` 建议、AI 推荐排序或模型版本串包装成“AI 已达标”。
- 不改合同 HITL、人工复核、AI 就绪度与治理态势的既有规格。
- 不执行生产库操作或接生产 MQ。

## Decisions

1. **新增三条路由而非改造现有供应商页**：`/sourcing`、`/supplier-onboarding`、`/supplier-risk`。
   - 理由：寻源/准入/风险是不同决策路径，混进 `SupplierManagement` 会模糊权限与状态语义。
   - 备选：把所有内容塞进现有供应商页。放弃，会导致列重叠与权限点混乱。

2. **纯映射层与页面组件分离**：每个页面配套一个 `*.view.ts` 把后端响应映射为展示模型，未知码保留原始码，失败统一为“无法确认”；组件只渲染展示模型。
   - 理由：与 `governancePostureView` / `readinessView` 既有模式一致，便于单测覆盖边界。
   - 备选：在组件里直接判断状态码。放弃，会产生不可测的分散分支。

3. **API 客户端按后端真源判定结果**：`services/sourcingApi.ts` 与 `services/supplierRiskApi.ts` 读取顶层 `success`，403/404/网络失败分别映射为权限不足、能力未启用、无法确认；不因 HTTP 2xx 自动成功。
   - 理由：采购域已有“2xx ≠ 成功”的红线。
   - 备选：依赖 axios 默认 throw。放弃，无法覆盖 `success=false` 的 2xx 响应。

4. **前端不提供身份输入**：授标、OCR 复核、预警处置请求不发送前端自报的 `tenantId` / `operator` / `confirmedBy`；只发送业务标识与理由/结论，身份一律由后端登录态裁决。
   - 理由：身份冒名已在后端负向测试中封禁，前端再传值只会制造歧义。
   - 备选：从本地存储填身份。放弃，属于高风险伪造来源。

5. **导航复用而非状态复制**：新增共享导航组件链接现有 `/contract-hitl`、`/human-review`、`/ai-readiness`、`/governance`；不缓存这些页面的判词，不在工作台重复渲染全局结论。
   - 理由：避免“态势聚合”与既有治理页面口径分叉。

## Risks / Trade-offs

- **依赖后端已交付端点**：寻源/授标页面消费 `procuraai-durable-business-aggregates-rebaseline` 已落地的 `/api/v1/procurement/business/*`（GET award、POST bids/quotations、GET quotations）；A 方案 `procuraai-business-aggregates-completion` 保留为草稿，不作依赖。→ 取数失败/能力未启用一律走 fail-closed 降级，不默认显示成功。
- **内存仓储不跨重启**：页面展示的数据可能随后端重启清空。→ 页面只呈现“当前服务实例内的事实”，不写“持久/生产可用”文案。
- **PII 泄露**：证照响应可能意外携带证件号。→ 展示模型字段白名单，测试断言 DOM 可见文本与属性不含证件号/电话。
- **权限码不匹配**：复用既有权限码可能与实际部署不一致。→ 403 显示无权限提示，不隐藏整个应用也不自行放行。
- **全仓 vitest 假红基线**：不限定 app 的测试会扫到无关模块失败。→ 以采购 app 范围 vitest + tsc + eslint(JSON errorCount) 为权威判据。

## Migration Plan

无数据库迁移。按 API 客户端 → 展示映射 → 页面组件 → 路由与导航 → 测试的顺序实现；回滚时撤销本 change 的前端文件与路由注册即可。不触碰后端契约与数据。

## Open Questions

（无。依赖端点已由 `procuraai-durable-business-aggregates-rebaseline` 交付并在 `BusinessController` 落地；前端按真实响应形状实现，若与之不符，按实现遗漏在当前范围内修正，不扩需求。）

```

## openspec/changes/procuraai-procurement-ops-console/tasks.md

- Source: openspec/changes/procuraai-procurement-ops-console/tasks.md
- Lines: 1-24
- SHA256: 8a5f655bdc47e0369a475f42aea6fd64ad7709f1fbcbc47906d70c2c2631463c

```md
# Tasks

## 1. API 客户端

- [ ] 1.1 新建 `services/sourcingApi.ts` 提供 RFx/投标/报价/授标记录的列表、详情与具名授标调用，按顶层 `success` 判定结果；`sourcingApi.test.ts` 断言请求方法/路径正确且 body 不含前端自报身份字段，测试通过 <!-- comet-task:4823367f-ac44-46d0-be16-732bd29dd269 -->
- [ ] 1.2 新建 `services/supplierRiskApi.ts` 提供证照复核、准入终决、预警处置与绩效读取，并把 403/404/网络失败/`success=false` 映射为明确降级原因；`supplierRiskApi.test.ts` 覆盖四类失败，测试通过 <!-- comet-task:53871774-24e3-4a15-a462-bae3e8677594 -->

## 2. 寻源工作台页面

- [ ] 2.1 实现 `sourcingView.ts` 纯映射：未知状态保留原始码、不可比报价带原因、AI 建议只作建议字段；`sourcingView.test.ts` 覆盖未知码与不可比场景，测试通过 <!-- comet-task:70ef0207-3545-4c19-bf09-d38f54364253 -->
- [ ] 2.2 实现 `SourcingConsole.tsx` 的 RFx/投标列表、报价比较与授标操作区；`SourcingConsole.test.tsx` 验证冒名/匿名不显示成功、404 显示能力未启用，测试通过 <!-- comet-task:5278db3f-5d57-4a35-8189-867941d0415d -->

## 3. 准入与风险页面

- [ ] 3.1 实现 `supplierRiskView.ts` 纯映射：未知严重度保留原始码、绩效缺失显示“无法确认”、PII 字段白名单；`supplierRiskView.test.ts` 覆盖未知码、缺失值与 PII 剔除，测试通过 <!-- comet-task:de172cb5-60b7-468b-b2cc-d980227c8b8f -->
- [ ] 3.2 实现 `SupplierOnboarding.tsx` 的证照 OCR 复核与准入终决入口；`SupplierOnboarding.test.tsx` 验证前端不发送身份字段、过期证照不呈现复核通过，测试通过 <!-- comet-task:839249e8-3161-48b5-9760-f32653e6d535 -->
- [ ] 3.3 实现 `SupplierRisk.tsx` 的预警处置与绩效只读看板；`SupplierRisk.test.tsx` 验证未知严重度转人工提示、取数失败显示“无法确认”且不显示“无风险”，测试通过 <!-- comet-task:2947cc2e-25f8-4029-b417-112dbb3de894 -->

## 4. 导航与集成验收

- [ ] 4.1 实现共享导航组件串接合同 HITL、人工复核、AI 就绪度与治理态势入口；`ProcurementWorkbenchNav.test.tsx` 验证 403/404 显示降级提示，测试通过 <!-- comet-task:73a5d41d-ef63-4f8c-96f7-2d56fc7ec2bd -->
- [ ] 4.2 在 `App.tsx` 注册 `/sourcing`、`/supplier-onboarding`、`/supplier-risk` 并保留既有路由；路由渲染测试通过 <!-- comet-task:3d301afd-bb3b-42ab-8f0f-7e296e3115c0 -->
- [ ] 4.3 运行采购前端 vitest（限定 app）、`tsc --noEmit` 与 eslint JSON errorCount 解析，三项全绿并记录结果 <!-- comet-task:0fe2440d-8cdf-43e5-bf61-b182ef102af5 -->
- [ ] 4.4 独立审查身份边界、失败态与 PII 白名单，修复全部 CRITICAL/IMPORTANT 后复测通过 <!-- comet-task:4a2ac000-62c1-40a8-923e-6bb4e6fd3001 -->

```

## openspec/changes/procuraai-procurement-ops-console/.openspec.yaml

- Source: openspec/changes/procuraai-procurement-ops-console/.openspec.yaml
- Lines: 1-2
- SHA256: 4492318684c90914e715dee55afe9b23f60f7465350ada31d1d9437964f126f8

```md
schema: spec-driven
created: 2026-11-26

```

## openspec/changes/procuraai-procurement-ops-console/specs/procurement/sourcing-console/spec.md

- Source: openspec/changes/procuraai-procurement-ops-console/specs/procurement/sourcing-console/spec.md
- Lines: 1-59
- SHA256: 26dc29a8118fb893fe1e03b7a595cffd107db12d180fdfaee50edd5159bcaa09

```md
# Spec Delta

## Purpose

寻源工作台让采购人员在浏览器中按登录态租户查看 RFx、投标、报价比较与授标记录，并在后端守卫下完成具名人工授标；页面只呈现后端事实与失败原因，不自行推导业务状态或替用户作决定。

## ADDED Requirements

### Requirement: 寻源与投标只读呈现

系统 SHALL 提供 RFx、投标与授标记录的列表/详情视图，按后端响应的顶层成功标志判定结果，不得仅凭 HTTP 2xx 显示成功；未知状态码 MUST 原样显示，不得改写为“正常”或既有态。

#### Scenario: 顶层失败不误报成功
- **WHEN** 列表请求返回 HTTP 200 但顶层成功标志为 false
- **THEN** 页面显示后端失败原因，不渲染为空列表或成功态

#### Scenario: 未知投标状态原样显示
- **WHEN** 投标详情返回页面未预置的状态码
- **THEN** 页面显示原始状态码，不改写为“有效”“正常”或已提交态

#### Scenario: 跨租户详情被拒
- **WHEN** 页面请求不属于登录态租户的投标或授标详情
- **THEN** 页面显示无法查看或空态，不回显他方业务字段

### Requirement: 报价比较视图

系统 SHALL 以只读方式展示报价明细、币种、有效期与不可比原因；报价被标记不可比时 MUST NOT 参与有效候选排序。AI 评分与推荐排序 MUST 仅显示为建议字段，不得触发授标或入围状态迁移。

#### Scenario: 不可比报价被标注
- **WHEN** 报价币种不一致或有效期已过
- **THEN** 页面显示不可比原因，该报价不作为有效候选参与排序

#### Scenario: AI 建议不触发授标
- **WHEN** 页面展示 AI 推荐排序或高分建议
- **THEN** 页面只呈现建议文本，不自动调用授标端点，也不显示项目已进入 AWARDED

### Requirement: 授标人工操作身份边界

系统 SHALL 只在后端确认登录态具名身份后呈现授标操作结果；请求体中的租户或确认人与登录态不一致时 MUST 显示拒绝原因且不得本地推进状态。匿名、空白确认人或 AI 结论 MUST NOT 使页面显示授标成功。

#### Scenario: 请求体冒名被拒
- **WHEN** 用户提交的请求体中确认人与登录态不一致
- **THEN** 页面显示后端拒绝原因，授标记录与项目状态保持原值

#### Scenario: 匿名或 AI 结论不授标
- **WHEN** 未登录或仅存在 AI 推荐结论时尝试授标
- **THEN** 页面不显示成功态，不发起后续状态刷新为 AWARDED

### Requirement: 工作台导航与可观察降级

系统 SHALL 在寻源工作台提供合同 HITL、人工复核、AI 就绪度与治理态势入口；当这些能力返回 403 或端点未注册（404）时 MUST 给出可理解的降级提示，不得把 fail-closed 显示成空白页或全部正常。

#### Scenario: 能力未启用提示
- **WHEN** 点击进入的治理或 HITL 端点返回 404
- **THEN** 页面显示能力未启用提示，不渲染为空态或默认正常态

#### Scenario: 权限不足不绕过
- **WHEN** 工作台任一入口返回 403
- **THEN** 页面显示无权限提示，且不发起任何后续写调用

```

## openspec/changes/procuraai-procurement-ops-console/specs/procurement/supplier-risk-console/spec.md

- Source: openspec/changes/procuraai-procurement-ops-console/specs/procurement/supplier-risk-console/spec.md
- Lines: 1-59
- SHA256: 286a9b86765cbc42b314a208314f040bc617eb182a5a07ea8f8a299d6b398a28

```md
# Spec Delta

## Purpose

供应商准入与风险页面让采购人员在浏览器中完成证照 OCR 具名人工复核、准入终决、风险预警处置与绩效只读查看；页面只展示后端权威事实和失败原因，不替用户作准入、授信或淘汰决定。

## ADDED Requirements

### Requirement: 证照 OCR 具名人工复核

系统 SHALL 提供证照列表与 OCR 复核入口，复核动作由登录态提供租户与操作人；请求体自报身份 MUST NOT 成为权威值，成功与失败 MUST 按响应顶层成功标志判定。过期证照 MUST NOT 被页面呈现为可直接复核通过。

#### Scenario: 冒名复核被拒
- **WHEN** 请求体中的复核人与登录态不一致
- **THEN** 页面显示拒绝原因，证照状态保持不变，不显示复核通过

#### Scenario: 过期证照不可通过
- **WHEN** 证照已过期时用户尝试复核通过
- **THEN** 页面显示后端拒绝原因，核验状态不呈现为 VERIFIED

#### Scenario: 顶层失败不误报成功
- **WHEN** HTTP 2xx 但响应顶层成功标志为 false
- **THEN** 页面显示失败原因，不刷新为通过态

### Requirement: 准入终决与风险处置

系统 SHALL 只呈现后端允许的准入终决与预警处置事件；后端拒绝时 MUST NOT 本地推进状态。预警严重度未知或不可判定 MUST 原样显示并标注需人工处理，不得自动压成 LOW 或放行。

#### Scenario: 未知严重度原样显示
- **WHEN** 风险预警返回页面未预置的严重度码
- **THEN** 页面显示原始码并标注需人工处理，不作为低风险自动放行

#### Scenario: 后端拒绝终决时保持状态
- **WHEN** 准入终决或预警处置被后端拒绝
- **THEN** 页面显示失败原因，重新拉取的状态与操作前一致

### Requirement: 绩效只读看板

系统 SHALL 以只读方式呈现绩效评分与趋势；评分高低 MUST NOT 在页面触发自动准入、授信、淘汰或供应商去留结论。未知评分或缺失数据 MUST 显示原始值或“无法确认”，不得补零为正常。

#### Scenario: 高分不产生自动决定
- **WHEN** 绩效评分处于高分区间
- **THEN** 页面只展示评分事实，不显示自动授信、自动淘汰或准入已通过

#### Scenario: 缺失评分显示无法确认
- **WHEN** 绩效接口返回空值或未知评分码
- **THEN** 页面显示“无法确认”或原始码，不显示 0 分或正常态

### Requirement: 失败态与 PII 防护

系统 SHALL 在取数失败、权限不足或能力未启用时给出可理解提示；证件号、电话、法人姓名等 PII MUST NOT 出现在页面可见文本或 DOM 属性中。

#### Scenario: 取数失败显示无法确认
- **WHEN** 准入或风险列表请求失败
- **THEN** 页面显示“无法确认”，不呈现空列表为“无风险”或“全部正常”

#### Scenario: PII 不落地到 DOM
- **WHEN** 后端响应意外携带证件号或电话字段
- **THEN** 页面可见文本与 DOM 属性均不出现这些字段值，只展示非敏感状态与摘要

```
