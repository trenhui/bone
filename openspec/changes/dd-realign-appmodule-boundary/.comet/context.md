# Comet Design Handoff

- Change: dd-realign-appmodule-boundary
- Phase: design
- Mode: compact
- Context hash: 1a012dc6a0edf5991779ee5995f8382096f33ef1b0dc9bd52879cf8a878060c3

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/dd-realign-appmodule-boundary/proposal.md

- Source: openspec/changes/dd-realign-appmodule-boundary/proposal.md
- Lines: 1-60
- SHA256: 1c37bc409bc8be4fe00e137a83ac5988fd61ed628436d18045296e59cea24d8f

```md
# 提案：应用/模块领域边界重划分（DDD 对齐）

- **Change**: `dd-realign-appmodule-boundary`
- **Workflow**: full
- **Language**: zh-CN
- **日期**: 2026-08-25

## 1. 问题陈述（已核实的证据）

在 Bone 代码库中，`App → Module` 这一聚合根被 **IAM 与 metadata 两个限界上下文各自实现了一遍**，且物理表同名、字段几乎一致、互不引用：

| 关注点 | IAM 模块 | metadata 模块 |
|--------|----------|---------------|
| App 实体 | `bone-iam/domain/app/BoneApplication`，表 `bone_application` | `bone-metadata/catalog/domain/model/BoneApplication`，表 `bone_application`（**同名**） |
| Module 实体 | `bone-iam/domain/app/BoneModule`，表 `bone_module`，`appId` 外键 | `bone-metadata/.../BoneModule`，`module.app_id` 外键 |
| 下游 | `ApplicationDTO` 已含 `moduleCount`/`entityCount`/`myRole`（应用内角色） | 再延伸到 `MetaEntity`/`MetaField`/`MetaRelation` |
| 跨模块依赖 | **无**（`bone-metadata-server` 的 pom 不依赖 `bone-iam`） | **无** |

**核心问题**：同一个"应用（租户下的业务系统/产品）"概念被重复建模，导致：
- 租户隔离、应用内角色、模块入口在两处各维护一份，数据不一致风险；
- 前端两套入口并存（`bone-iam-app` 调 `/api/v1/iam/apps`，`bone-metadata-app` 调 `/api/v1/metadata/apps`）；
- 违反 DDD 单一聚合根归属原则与康威定律（一个概念多个团队/模块拥有）。

## 2. 目标

基于 DDD 最佳实践，将领域边界清晰化：
- **IAM（身份与接入上下文）** 持有**唯一的应用接入根** `Application` + `Module` + 应用内角色（admin/developer/viewer），负责租户隔离、谁可进入、有哪些模块入口。
- **metadata（元数据建模上下文）** 退化为纯建模内容：`Module / MetaEntity / MetaField / MetaRelation` 不再自建 `BoneApplication`，改为持有 `applicationId` 外键引用 IAM 的 `Application`。
- 依赖方向收敛为：`metadata.Module → iam.Application`（下游引用上游，Customer/Supplier 关系）。

## 3. 方案概要（WHAT）

1. **保留 IAM 的 `bone_application` + `bone_module`** 作为唯一应用接入根（IAM 侧不动表结构）。
2. **metadata 侧**：
   - 删除 `bone-metadata/catalog/domain/model/BoneApplication` 及其 command/query/handler/controller（`AppCatalogController`、App/Module 的命令与查询）。
   - 在 `BoneModule`（metadata）、`MetaEntity`、`MetaField` 增加 `applicationId` 字段（外键语义，逻辑引用 IAM 的 `Application.id`；因两模块不共享数据源，采用逻辑外键 + 应用层校验，而非物理 FK 约束）。
   - `MetaEntity` 现有 `module_id` 链路已能回溯到 `BoneModule`，再通过 `BoneModule.applicationId` 回溯到应用；或直接在 `MetaEntity` 冗余 `applicationId` 以简化查询。
3. **前端归位**：
   - 应用管理（接入 + 成员 + 角色）归 `bone-iam-app`（`/api/v1/iam/apps`）。
   - 模块/实体/字段建模页面保留在 `bone-metadata-app`，模块选择器数据来源切换为 IAM 的 App/Module（`/api/v1/iam/apps` + `/api/v1/iam/apps/{id}/modules`）。

## 4. 影响范围（跨模块）

- **后端**：`bone-platform/bone-iam`（保持，作为上游）、`bone-engine/bone-metadata-server`（删除 App 建模、加 `applicationId`）、可能涉及的 DTO/mapper。
- **前端**：`bone-metadata-app`（ApplicationManagement/ModuleManagement 改为调 IAM 接口）、`bone-iam-app`（应用管理页成为唯一入口）。
- **数据**：需数据迁移脚本——将 metadata 侧 `bone_application` 记录并入 IAM 侧（按 `tenant_id`+`code` 去重映射），把 metadata `bone_module/MetaEntity/MetaField` 的 `app_id` 重写为对应 IAM `application.id`。

## 5. 非目标（本次不做）

- 不改动 IAM `Application` 的认证/OAuth 语义。
- 不引入跨模块物理外键约束（保持两模块独立数据源）。
- 不重划分其他模块（masterdata/integration 的"实体"概念是否也与 metadata 重叠，留待后续独立 change）。

## 6. 风险与待确认

- **跨库数据迁移**：两套 `bone_application` 已有数据需合并映射，存在去重与 id 重写风险（需用户确认迁移策略：新建映射表 vs 直接重写 id）。
- **前端破坏性**：`bone-metadata-app/ApplicationManagement.tsx` 当前重度依赖 metadata 的 App 接口，需改造为调 IAM 接口。
- 这是**架构级重构**，改动面大；建议 branch 隔离、逐任务提交、TDD 回归。

> 注：本提案为 open 阶段 WHAT，详细 HOW（数据迁移脚本、字段改造、前端改造、测试策略）在 design 阶段经 brainstorming 后落 Design Doc。

```

## openspec/changes/dd-realign-appmodule-boundary/design.md

- Source: openspec/changes/dd-realign-appmodule-boundary/design.md
- Lines: 1-128
- SHA256: 4a5f84b3ba1e3bfd70cfd8131aa4dfb7bf578f99a73cd053a7c34f711dcd7604

[TRUNCATED]

```md
---
comet_change: dd-realign-appmodule-boundary
role: technical-design
canonical_spec: openspec
status: final
---

# 设计文档：应用/模块领域边界重划分（DDD 对齐）

- **Change**: `dd-realign-appmodule-boundary`
- **Workflow**: full
- **Language**: zh-CN
- **日期**: 2026-08-25
- **阶段**: design（brainstorming 深度设计）

> Comet Classic `full` workflow 的 Superpowers Design Doc（桥梁产物）。
> 配套的 OpenSpec 产物：`openspec/changes/dd-realign-appmodule-boundary/{proposal,design,tasks}.md`
> 本文件记录**关键设计决策与选项评估**；任务拆解见 `tasks.md`。

## 0. 设计约束（已核实的事实基础）

1. 两套 `App→Module` 并存：IAM `bone_application`/`bone_module`（表名，含 `appId` 外键）与 metadata `bone_application`/`bone_module`（同名表，各自独立数据源、互不引用）。
2. `bone-metadata-server` 的 pom **不依赖** `bone-iam`——两模块使用独立数据源，无法建物理外键，只能逻辑外键 + 应用层校验。
3. IAM `ApplicationDTO` 已含 `id(Long)`、`moduleCount`、`entityCount`、`myRole`，`ApplicationPageQuery`/`ModuleListQuery` 已提供列表与模块列表接口（`/api/v1/iam/apps`、`/api/v1/iam/apps/{id}/modules`）。
4. metadata 侧建模链：`BoneModule.module.app_id` → `MetaEntity.module_id` → `MetaField.entity_id`；`MetaEntity`/`MetaField` 自带 `tenant_id`。
5. 前端：`bone-metadata-app/ApplicationManagement.tsx` + `ModuleManagement.tsx` + `appModuleApi.ts` 调 `/api/v1/metadata/apps`；`bone-iam-app` 调 `/api/v1/iam/apps`。

## 1. 领域边界划分（DDD 上下文映射）

采用 **Customer/Supplier（客户-供应商）** 关系：

```
┌─────────────────────────────────────────────────────────┐
│ IAM 上下文（上游 / Supplier）                              │
│   聚合根: Application(tenant_id, name, code, ...)          │
│   实体:   Module(appId → Application)                      │
│   职责:   应用接入、租户隔离、成员、应用内角色、模块入口    │
│   接口:   GET /api/v1/iam/apps                             │
│           GET /api/v1/iam/apps/{id}/modules                │
└─────────────────────────────────────────────────────────┘
                      ▲ 逻辑引用 (applicationId)
┌─────────────────────────────────────────────────────────┐
│ metadata 上下文（下游 / Customer）                         │
│   聚合根: Module(applicationId → IAM.Application)          │
│   实体:   MetaEntity(module_id → Module)                   │
│           MetaField(entity_id → MetaEntity)               │
│           MetaRelation(...)                                │
│   职责:   纯建模——模块、实体、字段、关系的定义与运行时      │
└─────────────────────────────────────────────────────────┘
```

**关键决策**：一个聚合根 `Application` 只归属 IAM 一个上下文（单一职责）；metadata 不再拥有 Application，只通过 `applicationId` 引用。

## 2. 数据模型变更

### 2.1 IAM 侧（保持，作为上游）
- `bone_application`（IAM）：不变，成为唯一应用根。
- `bone_module`（IAM）：不变，`app_id` 指向 IAM 的 application。

### 2.2 metadata 侧（改造）
- **删除** `bone-metadata/catalog/domain/model/BoneApplication` 及其全部 command/query/handler/controller（`AppCatalogController`、`CreateAppCommand`/`UpdateAppCommand`/`DeleteAppCommand` 及 handler、`ApplicationPageQuery`/handler、`AppPermission`）。
- **改造** `bone-metadata/.../BoneModule`：保留 `module.app_id`，但语义改为"引用 IAM 的 `Application.id`"（重命名为 `applicationId` 以明确跨上下文引用，去掉 metadata 自建 app 的歧义）。
- **新增** `MetaEntity.application_id` 冗余列（便于按应用查询实体，避免每次 JOIN module）。`MetaField` 已有 `entity_id` 可回溯，无需再加。
- 物理实现：metadata 侧表新增列 `application_id BIGINT NOT NULL`（逻辑外键，无 FK 约束）；通过应用层在写入 Module/Entity 时校验 IAM `Application.id` 存在（调用 IAM 接口或共享读写契约）。

> 注：因为两模块独立数据源，无法物理 FK。一致性由"metadata 写入前校验 IAM 应用存在"保证；数据迁移时一次性对齐。

## 3. 数据迁移策略（高风险，需脚本）

**目标**：将 metadata 侧 `bone_application` 数据并入 IAM 侧，使 metadata 的 module/entity/field 的 `app_id` 指向正确的 IAM `application.id`。

**去重映射规则**（按 `tenant_id` + `code` 唯一键）：
1. 扫描 metadata `bone_application`，对每个 `(tenant_id, code)`：
   - 若 IAM `bone_application` 已存在同 `(tenant_id, code)` → 复用 IAM 的 `id` 作为目标 `application_id`。
   - 否则 → 在 IAM `bone_application` 插入一条（沿用 metadata 的 name/description/icon/status），新生成 IAM `id` 作为目标。
2. 建立映射表 `app_id_mapping(metadata_old_id, iam_new_id, tenant_id)`。
3. 按映射重写 metadata `bone_module.app_id`、新增 `MetaEntity.application_id`。
4. 删除 metadata `bone_application` 表（迁移完成后）。

**迁移脚本产物**：`docs/migrations/dd-realign-appmodule-boundary.sql`（或 Java migration），含幂等（mapping 表去重）与回滚段（保留 mapping 表、IAM 侧仅新增的 app 可据 mapping 回删）。

```

Full source: openspec/changes/dd-realign-appmodule-boundary/design.md

## openspec/changes/dd-realign-appmodule-boundary/tasks.md

- Source: openspec/changes/dd-realign-appmodule-boundary/tasks.md
- Lines: 1-37
- SHA256: fa412fd6f5e588f2763175616377e6041941a6a53cfb2eff7d3e2636e4b3762d

```md
# 任务清单：应用/模块领域边界重划分

- **Change**: `dd-realign-appmodule-boundary`
- **Workflow**: full
- **Language**: zh-CN

## 阶段一：数据迁移脚本（先于代码改造，避免数据悬挂）

- [ ] T1. 编写元数据导出与去重映射脚本：扫描 metadata `bone_application`，按 `(tenant_id, code)` 与 IAM `bone_application` 建立 `app_id_mapping(metadata_old_id, iam_new_id, tenant_id)`。
- [ ] T2. 实现 IAM 侧 App 补齐插入（仅 metadata 有而 IAM 无的 `(tenant_id, code)`），生成 IAM `application.id` 写入 mapping。
- [ ] T3. 重写 metadata `bone_module.app_id` 与新增 `MetaEntity.application_id`（按 mapping），写完校验一致性。
- [ ] T4. 删除 metadata `bone_application` 表（保留 mapping 表用于回滚），脚本幂等且含回滚段。

## 阶段二：metadata 后端收敛

- [ ] T5. 删除 metadata `BoneApplication` 及其 command/query/handler/controller（`AppCatalogController`、`AppPermission` 等），移除 `/api/v1/metadata/apps` 端点。
- [ ] T6. 改造 `BoneModule`：`app_id` → `applicationId`（语义指向 IAM Application.id）；`MetaEntity` 新增 `application_id` 字段与持久化列。
- [ ] T7. 新增 `IamApplicationValidator`：module/entity 写入前校验 IAM `application.id` 存在（调 IAM 接口或共享读契约），失败返回明确错误。
- [ ] T8. 改造 module/entity 的 create/list handler：改用 `applicationId` 过滤与关联，移除旧 `app_id` 路径。

## 阶段三：后端测试（TDD）

- [ ] T9. 单元测试 `IamApplicationValidator`（不存在的 appId 拒绝、存在通过）。
- [ ] T10. 集成测试：module/entity 按 `applicationId` 查询回溯正确；迁移 fixtures 幂等执行测试。
- [ ] T11. 契约测试：metadata 调 IAM `/api/v1/iam/apps` 响应结构与 `ApplicationDTO` 一致。

## 阶段四：前端改造

- [ ] T12. `bone-metadata-app`：修正 `appModuleApi.ts` 错误注释（"后端未实现"）；模块/实体选择数据来源切换为 IAM `/api/v1/iam/apps`。
- [ ] T13. `bone-metadata-app/ApplicationManagement.tsx`：移除或改为只读跳转至 iam-app 应用管理页。
- [ ] T14. `bone-metadata-app/ModuleManagement.tsx`：模块归属应用由 IAM App 选择器决定，创建/列表适配 `applicationId`。
- [ ] T15. `bone-iam-app`：确认应用管理页为唯一入口，覆盖接入+成员+角色（`/api/v1/iam/apps` 已提供）。

## 阶段五：联调与收尾

- [ ] T16. E2E 冒烟：iam-app 应用管理可用；metadata-app 建模页选 IAM App 建模块/实体/字段全链路通过。
- [ ] T17. 更新 MEMORY.md 与架构文档，标记领域边界变更。

```
