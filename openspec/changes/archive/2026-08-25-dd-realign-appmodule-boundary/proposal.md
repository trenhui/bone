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
