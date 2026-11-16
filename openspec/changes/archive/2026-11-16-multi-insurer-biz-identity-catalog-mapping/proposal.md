# Proposal: multi-insurer-biz-identity-catalog-mapping

> **基线说明**：多保司主数据映射的 P0 内核（7 张身份型/记录型表 + fail-closed 解析内核 + 未命中队列 + 应用服务/REST）正由并行会话按「权威设计」`doc/ai-product/synapClaim/04-design/多保司保单配置面与主数据映射设计.md` 与 build plan `docs/superpowers/plans/2026-11-03-multi-insurer-master-data-mapping.md` 落地（当前为未提交工作区代码）。本 change 的规格是该内核之上的验收契约与剩余范围；实现完成后按本 spec 复核。

## Why

理赔模块的多保司地基（承保主体主档 `ClaimInsurer`、三层映射体系 `CodeMapping`/`InsurerCodeItem`/`MappingMiss`/`FieldBinding`/`StdCodeAlias`/`StdCodeSplit`）在权威设计中定型、内核实现进行中，但配置面尚未接上：保单的 `bizIdentityCode` 只透传不校验、映射无批量导入、前端完全缺管理面（保单表单主体仍是裸输入）。运营接入第二家保司时无法自助完成「注册主体 → 导入映射 → 处置未命中 → 差异化配置」闭环。本 change 把内核接到配置面，并补齐责任级差异化覆盖与保单聚合详情。

## What Changes

- **P0-1 主体引用收紧**：保单新建/编辑校验 `bizIdentityCode` 引用本租户 ACTIVE 的 `ClaimInsurer`（读取不强校验，存量自由文本可读不可存）；提供存量对齐清单 dry-run 脚本。复用既有主体主档，不新建主体表。
- **P0-2 映射批量导入与管理面**：`CodeMappingApplicationService` 新增 Excel（.xlsx，POI）批量导入（全量校验整批原子，成功行走版本化发布语义，单批 ≤2000）；补齐映射/码表/未命中队列的分页管理查询。
- **P1-3 标准/覆盖两段式**：新增责任级覆盖表——按主体覆盖责任的适用名单/费用类别规则，记录标准值快照，应用层实时合并（标准/覆盖/生效/来源），发布确认后生效，停用回落，支持「同步标准」；与保单 `configStatus` 三态兼容；理算引擎零改动。
- **P1-4 保单聚合详情视图**：只读详情页（Tab：基础信息/责任全貌/适用被保人假名名单/额度池），复用 `QuotaAccountRepository`。
- **前端管理面（bone-claim-app）**：新增主体管理页、映射工作台（导入+行级错误+未命中处置）、保单详情页；保单表单主体改可搜索下拉（仅 ACTIVE 主体）。
- **实现复核**：P0 内核完成后，按本 change specs 与权威设计的七条不变量复核并行会话的实现（fail-closed、一码一行、版本化回放链、唯一键策略、软删偏离的 SDK 兼容）。
- 明确不做（P2）：保全额度联动、职业列表第五库、快捷理算、双保单号改造、配置控制台单页树、maker-checker/bitemporal（§7 为后续 change）；保单维度重复维护库名单；明文证件存储。

## Capabilities

### New Capabilities

- `claim/biz-identity-registry`: 承保主体注册与保单引用约束——复用既有承保主体主档，规范保单对主体的引用（引用须存在且启用）与主体管理面。
- `claim/reference-catalog-mapping`: 保司码→标准码映射的导入与运营处置——批量导入、未命中队列闭环、管理面查询；标准库仍为唯一真源。
- `claim/policy-config-two-stage`: 责任级标准/覆盖两段式配置——覆盖项管理、合并预览、发布与回落。
- `claim/policy-detail-view`: 保单聚合详情视图——只读聚合展示责任全貌、适用名单、额度池。

### Modified Capabilities

（无——既有主 specs 无 claim 域能力；本 change 全部为新能力声明。）

## Impact

- **后端**：`bone-apps/synap-claim`——`ClaimConfigApplicationService` 新建/编辑校验引用；`CodeMappingApplicationService` 增导入与管理查询；新增 `CoverageOverrideApplicationService`、`PolicyDetailApplicationService`；REST 全走 `ApiResponse<T>`。
- **数据库**：仅新增 `t_claim_coverage_override` 一张表（`bone-init.sql` + `scripts/migration/`）；主体/映射表无 DDL 变更。
- **前端**：`bone-frontend/apps/bone-claim-app`——3 个新页面 + 保单表单改造 + 路由/权限常量。
- **理算引擎**：零改动（覆盖只影响配置面生效值来源；映射解析已有调用约定）。
