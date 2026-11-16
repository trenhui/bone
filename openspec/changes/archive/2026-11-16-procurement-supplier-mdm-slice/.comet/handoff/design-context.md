# Comet Design Handoff

- Change: procurement-supplier-mdm-slice
- Phase: design
- Mode: compact
- Context hash: e2ddd28ccc090b0c8683c281038ecbc1f4fd123c8d94403703c97f7c46e050b4

Generated-by: comet-handoff.sh
Task hash policy: task-content-v1. Read tasks.md for live completion; excerpts are design-time context.

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/procurement-supplier-mdm-slice/proposal.md

- Source: openspec/changes/procurement-supplier-mdm-slice/proposal.md
- Lines: 1-34
- SHA256: fcaf8ea98864ac502fd905bb4bdc92fa0220bf880c6af5c93dc2969e88c966a4

```md
# Proposal — 采购供应商主数据模块（最小可验证切片）

## Why

procuraAI 采购域目前是「设计态完整、代码态为零」：LLD 规划了 9 个 `bone-procurement-*` 模块，实际工程 0 个模块、0 条 `t_proc_*` DDL 落库、0 个 AI handler。平台侧 `bone-masterdata` 已存在完整的 Golden Record 去重内核（字段血缘、survivorship、身份映射、审计回滚、幂等分发，251 例单测），但采购域自身的供应商聚合、生命周期状态机、准入闸门尚无任何代码落地。

本 change 在不触碰 L3/L4 红线的前提下，先端到端落地**一个可运行、可验证**的采购域模块切片，用真实代码证明「LLD 01 卷 → DDD 分层 → 可跑通的门禁」这条落地路径可行，为后续 8 个模块提供可复制的骨架与验收范式。

## What Changes

- 在 `bone-masterdata`（复用现有 Golden Record 内核，不新建 Maven 模块、不落库）新增采购域**供应商主数据**领域模型：Supplier / Vendor 角色分离、LegalEntity、准入状态机、供应商资质与供应商-物料供货关系。
- 新增对应 application 层用例（语义化 `*ApplicationService`）与 domain 端口；adapter 层提供只读/治理 HTTP 端点，遵循 `ApiResponse<T>` / `PageResult<T>` 统一响应与权限点约定。
- 领域内核复用既有 `GoldenRecordMerger` / `EntityKeyMappingRegistry` / survivorship 策略，**不另建第二份 Golden Record、不自建 DedupService**（终局分工）。
- 全部能力以 `bone.procurement.supplier-mdm.enabled` 开关装配（默认关闭），生产端口未就绪时不暴露「能调用但落不了库」的假能力。
- 配套领域单测 + 应用服务测试 + HTTP 契约测试，以及负向探针（跨租户、身份冲突、无 lineage、转人工零副作用）。
- **不写 `bone-init.sql`、不新增 `t_proc_*` DDL、不碰 CI 脚本、不碰生产库/密钥/发布**；持久化经领域端口 + 内存适配实现可运行闭环，真实 DDL 落库作为 R-2 前置留 owner/架构师审批。

## Capabilities

### New Capabilities

- `procurement/supplier-mdm`: 采购域供应商主数据的领域语义与对外可观察行为——Supplier/Vendor 角色分离、身份唯一性与准入状态机、字段级可溯源合并、租户隔离与 fail-closed、只读查询与治理端点的权限契约。

### Modified Capabilities

（无。本切片不修改任何现有 capability 的规格，仅新增采购域供应商主数据能力；`ddd-architecture-governance` 作为约束被遵循，不发生规格级变更。）

## Impact

- **代码**：`bone-platform/bone-masterdata` 新增 `domain/model/supplier`、`application`、`adapter/web` 下的采购域供应商主数据类；复用既有 goldenrecord / identity 包，不改动其内容。
- **API**：新增 `/api/v1/masterdata/suppliers` 系列只读与治理端点（受开关与权限点控制）。
- **依赖**：无新增第三方依赖；无 Maven 模块新增；无 DDL/数据库变更。
- **门禁**：`masterdata` 模块 `clean test` 全绿；procuraAI 文档族门禁保持既有绿态（本 change 不改文档族真源与门禁判据）。
- **红线声明**：DDL 落库（R-2/L3）、CI 三入口、生产库迁移、密钥、发布打 tag 均不在本 change 范围，留 owner/架构师审批。

```

## openspec/changes/procurement-supplier-mdm-slice/design.md

- Source: openspec/changes/procurement-supplier-mdm-slice/design.md
- Lines: 1-142
- SHA256: 1385fe6cb581fc8a758db72df333af23e6bbfc77216475ebae2c888f733efb1b

[TRUNCATED]

```md
---
comet_change: procurement-supplier-mdm-slice
role: technical-design
canonical_spec: openspec
---

# Design — 采购供应商主数据模块（最小可验证切片）

## Context

- `bone-masterdata` 已存在完整 Golden Record 去重内核：`domain/model/goldenrecord`（`GoldenRecordMerger`、`FieldProvenance`、`FieldSurvivorshipRule`、`GoldenRecord`、`MergeAudit`）、`domain/model/identity`（`EntityKeyMappingRegistry`，含 `register/resolve/localIdsOf`）、`application/GoldenRecordApplicationService`、`domain/gateway/GoldenRecordPort`，以及 `adapter/web/controller/GoldenRecordController`（251 例单测）。
- 该内核的终局分工明确：**平台侧是身份 + General data 的唯一 system of entry，业务域只消费 partyId 与自有视图列，不得另建 Golden Record / DedupService**。
- 采购域 LLD 已裁决 Supplier 与 Vendor 的语义与两套状态机（`01-供应商主数据模块详细设计.md:22`、`采购领域架构设计方案.md:109,223`），但代码为零。
- 平台约束（根 `AGENTS.md`）：分层 `adapter → application → domain ← infrastructure`；持久化只用 `bone-metadata-sdk`；Controller 返回 `ApiResponse<T>` / `PageResult<T>`；业务/应用层禁 `SELECT ... FOR UPDATE`；能力开关采用 `@ConditionalOnProperty`。
- 红线：DDL 落库（R-2/L3）、CI 三入口、生产库迁移/密钥/发布均不动；本切片用领域端口 + 内存适配达成可运行闭环。

## Goals / Non-Goals

**Goals:**
- 在 `bone-masterdata` 内以 DDD 分层落地采购域**两套角色 + 两套状态机**，端到端可运行、可测试。
- 复用既有 Golden Record 内核完成合并/血缘/身份解析，验证「业务域接平台内核」的集成方式。
- 建立可复制的模块骨架与验收范式（领域单测 + 应用服务测试 + HTTP 契约测试 + 负向探针），供后续 8 个模块复用。

**Non-Goals:**
- 不新建 `bone-procurement-*` Maven 模块（保持当前模块边界，降低 L2 依赖变更风险）。
- 不写 `bone-init.sql` / 任何 `t_proc_*` DDL；不实现真实 JDBC 持久化 adapter（R-2 留 owner/架构师）。
- 不实现寻源/准入审批的 AI 能力、合同风险、交易成本、履约协同等其余 8 个模块。
- 不改变既有 Golden Record 内核代码与语义（只调用、不修改）。
- 不接入真实 Finance / PO / 合同上游（跨域事实由端口 + 内存夹具提供，属 owner 前置）。
- 不触碰 CI 脚本、门禁判据集合、生产库/密钥/发布。

## Decisions

### D1 — 落地宿主：`bone-masterdata` 内新增采购域包，而非新 Maven 模块

**决策**：在 `com.bone.masterdata` 下新增 `domain/model/supplier`、`application`（复用并对齐现有包）、`adapter/web/controller` 中的采购域供应商类，不新建 Maven 模块、不改根 pom。

**理由**：Golden Record 内核已在 `bone-masterdata`，采购域供应商主数据需在同进程内直接复用 `GoldenRecordMerger` / `EntityKeyMappingRegistry`；新建模块会引入跨模块依赖与构建配置变更（L2），放大风险且无法复用内核。最小可验证切片优先证明领域语义与集成方式，模块物理拆分属后续架构决策。

**备选**：新建 `bone-procurement-supplier-mdm` 模块并对 `bone-masterdata` 增加 Maven 依赖——被否（依赖变更 + 无法快速验证内核复用，且同样绕不开 DDL 落库红线）。

### D2 — 角色语义严格对齐 LLD 真源（Design 阶段修正）

**决策**：
- `Supplier` = **合作资格**：寻源、准入、绩效、风险、退出。
- `Vendor` = **交易与付款资格**：财务侧 Company Code data（银行、税务、付款条件、付款冻结）。
- `LegalEntity` 持有统一社会信用代码/税号与统一 `partyId`，是身份的载体。
- `SupplyRelationship` 为**独立聚合根**，`SupplierItem` 为其下供货条目，二者属 Purchasing Org data（Owner = 采购）。
- `Supplier` 与 `Vendor` 为同一 LegalEntity 下两个独立聚合，各自持有**互不覆盖**的状态机。

**理由**：LLD 明文裁决「合作资格（Supplier）与交易/付款资格（Vendor）两套互不覆盖的状态机」（`01-供应商主数据模块详细设计.md:22`）、「Vendor 承载财务侧 Company Code data，Owner = 财务；SupplyRelationship / SupplierItem 承载采购侧 Purchasing Org data，Owner = 采购」（`采购领域架构设计方案.md:109`）、「两套状态机并行、互不覆盖」（同文件 `:223`）。Open 阶段的 delta spec 曾把二者语义写反（Supplier 记作「合同与付款对象」），若照此实现会把付款资格挂到 Supplier，与该真源及财务侧分工相反，后续接财务发票校验与 `VendorActivation` 闸门必然返工。本阶段已按用户裁决修正 spec。

**备选**：单一 Supplier 实体 + 类型枚举——被否（无法表达同一法人双角色并存、各自独立状态与不同 Owner）。保持 Open 时旧语义——被否（与 LLD 真源相反，见上述反向后果）。

### D3 — 身份与合并：复用 Golden Record 内核，不自建

**决策**：合并/血缘/身份解析统一调用既有 `GoldenRecordMerger`、`FieldSurvivorshipRule`、`EntityKeyMappingRegistry`；采购域只提供「供应商视图」的装配与状态机，不新增任何 dedup/merge 领域服务。

**理由**：终局分工要求业务域不另建黄金记录；复用保证跨租户、身份冲突、lineage 完整性、具名裁决、回滚防覆盖五条硬不变量在采购域一致生效。`EntityKeyMappingRegistry` 现有三个方法（`register/resolve/localIdsOf`）已满足本地编码 → partyId 解析。

**备选**：采购域自建简化版 SupplierDedupService——被否（违背终局分工，产生第二份权威与语义漂移）。

### D4 — 持久化与开关：领域端口 + 内存适配，能力默认关闭

**决策**：新增 `SupplierRepositoryPort`（领域出站端口，支持按 `partyId` 查询、按租户分页列表、保存）与内存实现（测试/演示装配）；应用服务与 HTTP 端点受 `bone.procurement.supplier-mdm.enabled` 开关控制，默认关闭。生产 JDBC adapter 与 `t_proc_*` 表属 R-2，不在本切片。

**理由**：能力开关是终局部署态治理构件（与 Golden Record 既有开关同构）；不暴露「能调用但落不了库」的假能力，符合 fail-closed。内存适配让领域与应用逻辑可真实跑通并被测试，同时不触碰 DDL 红线。

**备选**：直接写 JDBC adapter + DDL——被否（L3/L4 红线，须架构师审批）。

### D5 — 两套状态机均用「枚举 + 显式迁移表」，守卫集中在 domain

**决策**：`SupplierLifecycle` 与 `VendorLifecycle` 各实现一套 `Map<State, Map<Event, Transition>>` 迁移表；迁移表未列出即拒绝（fail-closed）；非法迁移抛 `DomainException` 并携带 `current_state / attempted / allowed[]`；终态由 domain 守卫保证不可回写。

**Supplier 状态机**（LLD `01 卷 §5`）

| From | Event | Guard | To |
|---|---|---|---|
| `PROSPECT` | `onboarding_submitted` | 背调 + 资质齐备 | `ONBOARDING` |
| `ONBOARDING` | `onboarding_approved` | 变更请求全段批准 + 至少 1 个 `ACTIVE_VENDOR` | `ACTIVE` |

```

Full source: openspec/changes/procurement-supplier-mdm-slice/design.md

## openspec/changes/procurement-supplier-mdm-slice/tasks.md

- Source: openspec/changes/procurement-supplier-mdm-slice/tasks.md
- Lines: 1-38
- SHA256: fb2544fccf93a9ba75ff83a5297a99d26b55581d6b1e3b3efff67c404e123009

```md
# Tasks — 采购供应商主数据模块（最小可验证切片）

## 1. 领域模型 — 供应商角色与身份

- [ ] 1.1 新增 `LegalEntity`（统一社会信用代码/税号 + partyId）与 `Supplier`（合作资格）/ `Vendor`（交易与付款资格）角色聚合，二者各自持有**互不覆盖**的生命周期状态；验证：`LegalEntityTest` 覆盖双角色关联、角色不可穿透、双状态机互不覆盖 <!-- comet-task:378bcf63-9f1b-4477-9b61-aff4ed103bf9 -->
- [ ] 1.2 新增 `SupplyRelationship` 聚合根与 `SupplierItem` 供货条目（均属 Purchasing Org data，Owner = 采购）；验证：领域单测覆盖关系约束与不可变语义 <!-- comet-task:d9bc9167-3e52-45a5-87ad-b265a3171c3b -->
- [ ] 1.3 新增身份解析：复用既有 `EntityKeyMappingRegistry` 将本地编码解析为统一 partyId，不自建 dedup；验证：`SupplierIdentityResolverTest` 覆盖身份冲突转人工、跨租户 fail-closed <!-- comet-task:6bc3c1fd-989a-4a57-afdb-bbd4ed98a40b -->

## 2. 领域模型 — 合并血缘与双状态机

- [ ] 2.1 新增供应商合并装配：调用既有 `GoldenRecordMerger` + `FieldSurvivorshipRule`，将合并结果映射为供应商视图 DTO，不修改内核；验证：应用服务测试覆盖 lineage 完整才产出、缺 lineage 整笔拒绝 <!-- comet-task:5c0701a1-6bda-43d4-ae12-eba999c3f66a -->
- [ ] 2.2 新增 **Supplier 合作资格状态机**（`PROSPECT→ONBOARDING→ACTIVE⇄SUSPENDED/CORRECTIVE→EXITED`）：枚举 + 显式迁移表，迁移表未列出即拒绝，非法迁移抛 `DomainException`（携带 `current_state/attempted/allowed[]`）；`EXITED` 终态不可回写；退出前置经 `SupplierExitGuard` 端口裁决；验证：`SupplierLifecycleTest` 覆盖合法路径 + 非法迁移 + 退出前置拒绝 + 终态回写拒绝 <!-- comet-task:16ea2af2-fd25-4f34-a769-8a1b4392d32b -->
- [ ] 2.3 新增 **Vendor 交易与付款资格状态机**（`PENDING_VENDOR→ACTIVE_VENDOR⇄PAYMENT_BLOCKED→INACTIVE_VENDOR`）：开通经 `VendorActivationGuard` 端口全项通过、未经闸门不得开通、域内不得自行解除 `PAYMENT_BLOCKED`、停用不得留下进行中 PO；验证：`VendorLifecycleTest` 覆盖四类守卫 <!-- comet-task:e6b6f634-7fe9-4bad-8055-7b9332ae59b1 -->
- [ ] 2.4 负向探针：具名裁决缺失（`STEWARD_OVERRIDE`/`MANUAL_REVIEW`）整笔转人工、零副作用；验证：单测断言无落库无事件 <!-- comet-task:795f60e9-859d-4045-9c5e-23b7f4f7f926 -->

## 3. 应用服务与端口

- [ ] 3.1 新增 `SupplierRepositoryPort` 领域出站端口与内存实现（测试/演示装配），含按 partyId/租户查询与保存；验证：端口契约单测 <!-- comet-task:c43f9557-28af-49ca-9e05-b71d4432934d -->
- [ ] 3.2 新增语义化 `SupplierApplicationService`：合并解析、Supplier 合作资格迁移、Vendor 交易付款资格迁移、当前状态查询四个用例，租户与登录态一致校验 fail-closed；验证：应用服务测试覆盖四个用例 + 租户不一致拒绝 <!-- comet-task:33fe065b-9a87-488f-ae6c-df0f7fa3cee9 -->
- [ ] 3.3 能力开关：应用服务与端点受 `bone.procurement.supplier-mdm.enabled` 控制（默认关闭），遵循 tenant 从登录态取值不接自报；验证：上下文测试断言关闭时不装配、开启时装配 <!-- comet-task:467ccb12-9aac-4877-a801-5fe9957823e9 -->

## 4. Adapter 与 HTTP 契约

- [ ] 4.1 新增 `SupplierController`，端点返回 `ApiResponse<T>` / `PageResult<T>`，不裸返领域对象；写操作 `masterdata:governance:write`、读操作 `masterdata:governance:read`；验证：HTTP 契约测试覆盖响应包装与权限点 <!-- comet-task:2b945214-f392-45ef-9790-767cdc23c9e4 -->
- [ ] 4.2 治理端点只返回裁决/版本/摘要，不回传字段原值或 PII；验证：契约测试响应体不含身份原值 <!-- comet-task:fde61e9a-4e4a-468c-9aa2-7f29c0dbdef0 -->
- [ ] 4.3 开关关闭时端点不注册；验证：`@SpringBootTest` 关闭态断言路由 404/不存在 <!-- comet-task:ae06ef26-86f1-4cb7-974b-581532afb295 -->

## 5. 集成验证与门禁回归

- [ ] 5.1 `mvn -o -f bone-platform/bone-masterdata/pom.xml clean test` 全绿（新增测试全部通过，既有 251 例不退化）；先 `spotless:apply` <!-- comet-task:aa155423-ca7f-43bc-a33b-99c9675b8c73 -->
- [ ] 5.2 负向探针注入必红 + 还原必绿：跨租户 / 身份冲突 / 缺 lineage / 具名裁决缺失 / 未结 PO 退出 / 域内自行解除付款冻结 六类探针各验证一次转红，还原后复绿，记录命令与结果 <!-- comet-task:6eaa57cf-a90d-45bb-9177-496c9cccac48 -->
- [ ] 5.3 文档族门禁保持绿态：`bash doc/ai-product/procuraAI/scripts/check-procura-ci.sh` 主门禁 10/10、自检 146/146 不退化（本 change 不改文档族真源） <!-- comet-task:e4cc7283-e28a-44e8-811e-0cd9197d8a03 -->

## Workflow follow-up（归档/交付，非 tracked 任务）

- Verify 阶段：真实构建门禁 + 独立只读最终集成审查，报告落 `docs/superpowers/reports/`
- Archive 阶段：确认归档；交付方式（local / push / pr）由用户明确授权后执行，不自行建分支、不 push、不 force
- 红线外事项（DDL 落库 R-2 / CI / 生产库 / 密钥 / 发布 / 法务结论）登记为 owner 前置，不在本 change 交付

```

## openspec/changes/procurement-supplier-mdm-slice/.openspec.yaml

- Source: openspec/changes/procurement-supplier-mdm-slice/.openspec.yaml
- Lines: 1-2
- SHA256: 95a9a7d17f5cbd573f5ec85bba1fef77465d4658dc49a60203cbef4f4af5f8df

```md
schema: spec-driven
created: 2026-11-15

```

## openspec/changes/procurement-supplier-mdm-slice/specs/procurement/supplier-mdm/spec.md

- Source: openspec/changes/procurement-supplier-mdm-slice/specs/procurement/supplier-mdm/spec.md
- Lines: 1-111
- SHA256: a1e34f3096ac4406f9f2b538449a10f610a78314cdced55faacf3190e2eecc5c

[TRUNCATED]

```md
# Spec Delta — 采购供应商主数据（supplier-mdm）

## Purpose

采购域供应商主数据能力提供 Supplier（合作资格）与 Vendor（交易与付款资格）角色分离的建模、统一身份解析、字段级可溯源的合并、两套互不覆盖的生命周期状态机，以及租户隔离与 fail-closed 的治理契约；它是采购域 9 个模块的领域地基，业务域只持有 partyId 与自有视图列，不另建第二份黄金记录。

## ADDED Requirements

### Requirement: 供应商与供应商主体的角色分离

系统 SHALL 将 Supplier（合作资格：寻源、准入、绩效、风险、退出）与 Vendor（交易与付款资格：财务侧 Company Code data，含银行、税务、付款条件与付款冻结）建模为同一 LegalEntity 下的两个独立角色，二者不得互相替代或合并为单一实体，且各自持有互不覆盖的生命周期状态机。

#### Scenario: 同一法人下的双角色
- **WHEN** 一个 LegalEntity 同时具备合作资格与交易付款资格被登记
- **THEN** 系统建立 1 个 LegalEntity、1 个 Supplier 角色与 1 个 Vendor 角色，三者通过统一 partyId 关联，且 Supplier 与 Vendor 各自持有独立的生命周期状态

#### Scenario: 角色不可互换
- **WHEN** 以 Vendor 角色查询合作资格状态
- **THEN** 系统不返回 Supplier 角色数据，角色边界不被静默穿透

#### Scenario: 双状态机互不覆盖
- **WHEN** Supplier 因风险事件进入 `SUSPENDED`，而其 Vendor 角色仍处于 `ACTIVE_VENDOR`
- **THEN** 系统保留两个独立状态，不以任一状态机的结果推导或覆盖另一状态机

### Requirement: 身份唯一性与禁止自动合并

系统 SHALL 以统一社会信用代码/税号作为身份主键字段；这些字段值冲突时 MUST 转人工复核，禁止自动合并，且不得静默丢弃跨租户候选记录。

#### Scenario: 身份主键冲突转人工
- **WHEN** 两条候选供应商记录的统一社会信用代码不同但其余字段高度相似
- **THEN** 系统拒绝自动合并，返回 `MANUAL_REVIEW` 裁决且不产生任何落库或分发副作用

#### Scenario: 跨租户候选记录
- **WHEN** 合并命令中出现租户号与命令租户不一致的候选源记录
- **THEN** 系统 fail-closed 拒绝整笔合并并给出明确原因，不静默过滤该记录

### Requirement: 字段级血缘与整笔拒绝

系统 SHALL 要求黄金记录中每个有值字段都携带完整字段级 lineage（来源记录、来源系统、胜出规则、观测时间）；缺任一条 lineage 时 MUST 整笔拒绝合并，不允许「部分字段可溯源」的记录进入分发链路。

#### Scenario: 缺失 lineage
- **WHEN** 某胜出字段缺少来源系统或胜出规则
- **THEN** 整笔合并被拒绝，不产出可分发记录

#### Scenario: 具名裁决完整性
- **WHEN** 字段胜出策略为 `STEWARD_OVERRIDE` 或 `MANUAL_REVIEW` 但无具名数据管家裁决记录
- **THEN** 整笔转人工，不部分应用任何字段胜出结果

### Requirement: Supplier 合作资格生命周期状态机

系统 SHALL 为 Supplier 角色维护合作资格生命周期状态机（`PROSPECT` → `ONBOARDING` → `ACTIVE` ⇄ `SUSPENDED` / `CORRECTIVE` → `EXITED`）；状态迁移 MUST 由显式事件驱动、经迁移表裁决，迁移表未列出即拒绝（fail-closed）。

#### Scenario: 合法准入路径
- **WHEN** 处于 `ONBOARDING` 的 Supplier 收到 `onboarding_approved` 事件，且变更请求全段已批准、至少存在 1 个 `ACTIVE_VENDOR`
- **THEN** 状态迁移为 `ACTIVE`，并记录迁移时间、操作人与依据

#### Scenario: 非法迁移被拒
- **WHEN** 处于 `PROSPECT` 的 Supplier 直接收到 `onboarding_approved` 事件
- **THEN** 系统拒绝迁移并保持原状态，返回非法迁移原因及当前允许的事件集合

#### Scenario: 退出前置校验
- **WHEN** 处于 `ACTIVE` 的 Supplier 收到 `exit_approved`，但存在未结 PO、未结合同、未清应付或仍有未关闭的 Vendor 角色
- **THEN** 系统拒绝迁移至 `EXITED` 并保持原状态，不产生任何副作用

#### Scenario: 终态不可回写
- **WHEN** 处于 `EXITED` 的 Supplier 收到任一迁移事件
- **THEN** 系统一律拒绝，终态不可回到任何其他状态

### Requirement: Vendor 交易与付款资格状态机

系统 SHALL 为 Vendor 角色维护交易与付款资格状态机（`PENDING_VENDOR` → `ACTIVE_VENDOR` ⇄ `PAYMENT_BLOCKED` → `INACTIVE_VENDOR`）；开通必须经闸门全项通过，付款冻结不得由本域自行放行。

#### Scenario: 开通闸门全项通过
- **WHEN** 处于 `PENDING_VENDOR` 的 Vendor 收到 `activation_passed`，且准入、银行核验、税务、付款条件与重复发票检查全部通过
- **THEN** 状态迁移为 `ACTIVE_VENDOR`，并记录闸门各项结论与时间

#### Scenario: 未经闸门不得开通
- **WHEN** 处于 `PENDING_VENDOR` 的 Vendor 被直接置位为 `ACTIVE_VENDOR` 而未经闸门
- **THEN** 系统拒绝该迁移并保持原状态


```

Full source: openspec/changes/procurement-supplier-mdm-slice/specs/procurement/supplier-mdm/spec.md
