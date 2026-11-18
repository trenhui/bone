---
comet_change: procurement-supplier-mdm-slice
role: technical-design
canonical_spec: openspec
archived-with: 2026-11-16-procurement-supplier-mdm-slice
status: final
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
| `ACTIVE` | `risk_exceeded` | 风险事件 / 绩效不达标 | `SUSPENDED` 或 `CORRECTIVE` |
| `SUSPENDED` | `restored` | 整改复核通过 + 审批 | `ACTIVE` |
| `ACTIVE` / `CORRECTIVE` | `exit_approved` | 零未结 PO + 未结合同 + 未清应付 + 全部 Vendor 已 `INACTIVE_VENDOR` | `EXITED` |

**Vendor 状态机**（LLD `01 卷 §5`）

| From | Event | Guard | To |
|---|---|---|---|
| `PENDING_VENDOR` | `activation_passed` | 准入 + 银行核验 + 税务 + 付款条件 + 重复发票检查全 PASS | `ACTIVE_VENDOR` |
| `ACTIVE_VENDOR` | `payment_block_raised` | 财务/风控事件（**本域不可自发放行**） | `PAYMENT_BLOCKED` |
| `PAYMENT_BLOCKED` | `payment_block_released` | 财务事件 + 人工复核留痕 | `ACTIVE_VENDOR` |
| 任一非终态 | `deactivate` | 无进行中 PO 或存量 PO 允许走完 | `INACTIVE_VENDOR` |

**禁止迁移**：`EXITED → 任意`（终态不可回写）；`INACTIVE_VENDOR → ACTIVE_VENDOR` 须重新走开通闸门；`PENDING_VENDOR → ACTIVE_VENDOR` 未经闸门直接置位一律拒绝。

**理由**：LLD `01 卷 §5` 明文要求「状态须落为枚举 + 显式迁移表 `Map<State, Map<Event, Transition>>`，守卫集中在 domain 层；迁移表未列出即拒绝（fail-closed）」。与本仓既有「领域内核负责内容正确性」的分工一致，避免状态判断散落在应用/适配层。

**备选**：在 application 层用 if/else 判断状态合法性——被否（守卫分散、易漏、违反 LLD 明文要求）。

### D6 — 跨域 Guard 用可注入断言端口，不硬编码也不伪造通过

**决策**：`EXITED` 的「零未结 PO / 未结合同 / 未清应付」与 Vendor 的「银行核验 / 重复发票检查」在本切片无对应上游域，设计为域内声明式前置条件端口：

```
SupplierExitGuard      { boolean canExit(SupplierExitContext ctx) }
VendorActivationGuard  { boolean canActivate(VendorActivationContext ctx) }
```

application 层负责编排外部事实（本切片由内存夹具提供），domain 只消费布尔结论 + 原因码。**不写死 `true`、不伪造通过**。

**理由**：本切片没有跨域 adapter（红线禁止接生产库），若把守卫留空或恒真，`EXITED` 的前置校验就无法被验证，spec 的「退出前置校验」Scenario 会变成假绿。用端口 + 布尔结论 + 原因码，既能在本切片用负向探针精确注入「存在未结 PO → 退出必红」，又能在上游域就绪时替换端口实现而领域语义零变化。

**备选**：守卫恒返回 `true`——被否（假绿，Scenario 无法验证）。直接注入跨域 Repository——被否（红线：不接生产库）。

### D7 — `PAYMENT_BLOCKED` 的「本域不可自发放行」用命令来源校验

**决策**：`payment_block_raised` / `payment_block_released` 只接受声明为外部财务事件的命令来源；域内来源一律拒绝。

**理由**：与 LLD「Finance/风控事件（本域不可自发放行）」一致，且与本仓既有「AI 不碰钱」基线同构——能力存在但默认不可达。本切片无 Finance 上游，只验证这条 fail-closed 语义，不宣称已接财务。

**备选**：允许域内直接解除——被否（越权放行付款，违反财务侧 Owner 边界）。

## Risks / Trade-offs

- [内存适配 ≠ 生产持久化] → 明确标注为 R-2 前置，拓扑与契约以端口固定；后续替换 adapter 时领域语义零变化；验收只针对领域/应用/HTTP 行为，不宣称生产落库能力。
- [跨域 Guard 用端口，可能被误读为「已接上游」] → 端口为 `boolean` 显式结论 + 原因码；负向探针覆盖「未结 PO → 必红」；文档如实标注跨域 adapter 属 owner 前置。
- [`PAYMENT_BLOCKED` 无 Finance 上游] → 只验证「本域不可自发放行」这条 fail-closed 语义。
- [Design 阶段修正了 Open 时的角色语义] → 变化摘要随 Design Wait 展示；批准绑定当前提案哈希，不复用旧哈希；旧 spec 断言不得作为验收依据。
- [同进程复用内核的耦合] → 采购域仅通过既有公开类型调用，不改内核；若后续物理拆模块，改为对 `bone-masterdata` 的显式依赖即可，接口不变。
- [能力默认关闭导致「看不见」] → 测试通过开关显式开启 + 负向探针验证关闭时不注册端点；文档如实标注当前态，不写成已上线。
- [与存量 `bone-app-purchase` 的关系] → 本切片不接触存量模块（其付款/审批 handler 收敛属 deferred backlog 6.x，留 owner）。

## Migration Plan

1. 无数据库迁移、无 DDL、无数据回灌（红线）。
2. 代码以新包形式增量加入 `bone-masterdata`，默认开关关闭，对现有功能零影响。
3. 回滚：移除新增包即可；无持久化副作用，不存在数据迁移回滚需求。
4. 生产启用前置：R-1（架构评审冻结）+ R-2（DDL L3 审批）+ R-5/R-6 由 owner 闭合后，另行 change 接生产 adapter 与真实跨域 Guard 实现并开启开关。

## Open Questions

（无会改变 specs/方案/任务分解的待决项。生产 DDL、CI、跨域 adapter、法务结论属已明确归 owner 的红线外事项，非本 change 的开放问题。）
