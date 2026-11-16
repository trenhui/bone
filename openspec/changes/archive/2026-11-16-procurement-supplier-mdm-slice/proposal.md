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
