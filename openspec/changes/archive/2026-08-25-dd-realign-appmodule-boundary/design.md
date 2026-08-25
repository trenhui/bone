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

1. **同库同表、双实体类**：`bone-init.sql` 中仅一套 `bone_application`/`bone_module` 表（第 1296/1314 行，归属 bone-platform/bone-application 段，即 IAM 模块）。但 **IAM 与 metadata 两个模块各有一个 Java `BoneApplication` 实体类都映射到 `bone_application` 表**——属于"同表双映射/双 CRUD"，并非两张物理表，也非跨库。
2. `bone-metadata-server` 的 pom **不依赖** `bone-iam`——两模块进程独立，但底层共用同一个 `bone` 库（`bone-init.sql` 统一建表）。因此 metadata 的 `app_id` 可保留为**物理外键**引用 `bone_application.id`，无需跨库逻辑外键。
3. IAM `ApplicationDTO` 已含 `id(Long)`、`moduleCount`、`entityCount`、`myRole`，`ApplicationPageQuery`/`ModuleListQuery` 已提供列表与模块列表接口（`/api/v1/iam/apps`、`/api/v1/iam/apps/{id}/modules`）。
4. metadata 侧建模链：`BoneModule.app_id` → `bone_application.id`；`MetaEntity.module_id` → `bone_module.id` → `MetaField.entity_id`；`meta_entity`/`meta_field` 表**无 `app_id` 列**（仅经 module 间接归属应用）。
5. 前端：`bone-metadata-app/src/services/appModuleApi.ts` 的 `appApi`/`moduleApi` 实际调用 `/api/v1/apps` 与 `/api/v1/apps/{id}/modules`（经网关到 IAM），**并非** `/api/v1/metadata/apps`；`ApplicationManagement.tsx`/`ModuleManagement.tsx` 早已指向 IAM。`bone-iam-app` 同样调 `/api/v1/iam/apps`。即应用/模块管理前后端均已归 IAM。

> **修正说明（build 阶段核实）**：原设计假设"两套同名表 + 跨库逻辑外键"；实测 `bone-init.sql` 仅一套表，两模块共库。故方案从"跨库迁移合并"收敛为"删除 metadata 的 `BoneApplication` 实体类与 CRUD，统一由 IAM 独占 `bone_application`"。

> **二次修正 + 最终决策（build 阶段核实 + 用户拍板）**：前端应用/模块管理早已完全指向 IAM（见第 5 条），metadata 的 `AppCatalogController`（`/metadata/apps`）是前端不用的重复实现（已删）。且 IAM 的 `bone-iam/domain/app/` 已拥有 `BoneApplication` + `BoneModule` + `ModuleListQuery`，**模块(Module)本就属于 IAM 应用建模上下文**。用户决策：**继续收敛**——删除 metadata 的 `BoneModule`，让 `MetaEntity.moduleId` 直接引用 IAM 的 `bone_module`（共库物理引用，无需加冗余 appId 列）。
>
> **最终领域边界**：**IAM（身份与接入）= App + Module**；**metadata（纯建模内容）= Entity + Field + Relation**。metadata 仅保留两个只读引用实体 `IamApplicationRef`(@Table bone_application) / `IamModuleRef`(@Table bone_module) + 对应 `Validator`，用于写入实体/字段前校验 App/Module 存在性，不重复定义任何写操作。DDL 无需改动（`meta_entity.module_id` 已存在且引用 `bone_module`）。

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

## 4. 后端代码改造清单（metadata-server）

| 动作 | 文件 |
|------|------|
| 删除 | `catalog/domain/model/BoneApplication.java` |
| 删除 | `catalog/application/command/cmd/CreateAppCommand`/`UpdateAppCommand`/`DeleteAppCommand` |
| 删除 | `catalog/application/command/handler/CreateAppHandler`/`UpdateAppHandler`/`DeleteAppHandler` |
| 删除 | `catalog/application/query/qry/ApplicationPageQuery` + `handler/ApplicationPageQueryHandler` |
| 删除 | `catalog/adapter/web/controller/AppCatalogController.java` |
| 删除 | `AppPermission` 相关（应用内角色改由 IAM 提供） |
| 改造 | `BoneModule`：`app_id` → `applicationId`（语义指向 IAM Application.id） |
| 改造 | `MetaEntity`：新增 `application_id` 列与字段 |
| 改造 | module/entity 的 create handler：写入前校验 IAM `application.id` 存在（新增 `IamApplicationValidator` 或调 IAM 接口） |
| 改造 | 现有 list/query handler：按 `applicationId` 过滤替代原 `appId` |

## 5. 前端改造清单

| 应用 | 页面 | 改造 |
|------|------|------|
| `bone-iam-app` | 应用管理 | 成为**唯一**应用管理入口（接入+成员+角色）；确认 `/api/v1/iam/apps` 已覆盖 |
| `bone-metadata-app` | `ApplicationManagement.tsx` | **删除或改为只读跳转**——应用管理不再在 metadata 内；建议移除该页，模块/实体选择改从 IAM App 下拉 |
| `bone-metadata-app` | `ModuleManagement.tsx` | 模块列表/创建改调 `/api/v1/iam/apps/{id}/modules` 或保留 metadata module 但 `applicationId` 来自 IAM App 选择器 |
| `bone-metadata-app` | `appModuleApi.ts` | 修正"后端未实现"错误注释；模块/实体接口 base 指向 IAM App 选择器数据 |
| `bone-metadata-app` | `EntityManagement`/`FieldManagement` | 选择应用时从 IAM `/api/v1/iam/apps` 拉取，而非 metadata apps |

> 前端边界原则：应用（App）的 CRUD 全在 iam-app；模块（Module）作为建模入口，其"归属哪个应用"由 IAM App 选择决定，模块本身的建模（字段/实体）在 metadata-app。

## 6. 测试策略（TDD）

1. **单元测试**：`IamApplicationValidator`——传入不存在的 IAM appId 时 metadata 写入被拒；存在时通过。
2. **集成测试**：module/entity 创建经改造后，按 `applicationId` 查询能正确回溯；旧 `app_id` 路径移除。
3. **迁移测试**：迁移脚本对 fixtures（含重复 `(tenant_id,code)`、空 metadata app）幂等执行，mapping 正确、回滚可恢复。
4. **契约测试**：metadata 调 IAM `/api/v1/iam/apps` 的响应结构与 `ApplicationDTO` 一致（防止 IAM 接口变更打断下游）。
5. **E2E 冒烟**：应用管理在 iam-app 可用；metadata-app 建模页能选 IAM App 并建模块/实体/字段。

## 7. 风险与缓解

- **数据丢失风险**：迁移前全量备份；mapping 表保留可回滚。
- **跨上下文一致性**：metadata 写入强依赖 IAM 可用——引入 `IamApplicationValidator` 失败时返回明确错误（非静默）。
- **前端双入口混乱期**：先保留 metadata 应用页只读跳转到 iam-app，避免用户迷失。
- **独立数据源**：无分布式事务，迁移脚本一次性离线执行，运行期靠校验保证。

## 8. 非目标（本次不做）

- 不引入跨模块物理 FK、不引入事件总线同步（保持简单，应用层校验）。
- 不改 IAM Application 语义。
- masterdata/integration 的"实体"重叠问题不在本 change。
