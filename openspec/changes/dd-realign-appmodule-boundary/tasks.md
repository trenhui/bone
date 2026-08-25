# 任务清单：应用/模块领域边界重划分

- **Change**: `dd-realign-appmodule-boundary`
- **Workflow**: full
- **Language**: zh-CN
- **最终领域边界（build 核实 + 用户决策）**：IAM（身份与接入）= App + Module；metadata（纯建模内容）= Entity + Field + Relation。metadata 删除 `BoneApplication` 与 `BoneModule` 双聚合根，其 `MetaEntity.moduleId` 直接引用 IAM 的 `bone_module`（共库物理引用）。应用/模块管理后端由 IAM 独占；前端 `appModuleApi.ts` 早已调 `/api/v1/apps`（IAM 路径），仅修正过时注释即可。

## 阶段一：后端收敛（metadata-server 删除 App/Module 双聚合根）

- [x] T1. 删除 metadata `BoneApplication` 全套：`domain/model/BoneApplication.java`、`AppPermission.java`、`domain/repository/BoneApplicationRepository.java`、`AppPermissionRepository.java`、`command/cmd`(Create/Update/DeleteAppCommand, GrantPermissionCommand)、`command/handler`(Create/Update/DeleteAppHandler, Grant/RevokePermissionHandler)、`query/handler/AppQueryHandler.java`、`query/qry/AppPageQuery.java`、`query/dto/AppDTO.java`、`PermissionDTO.java`、`adapter/web/controller/AppCatalogController.java`（共 17 文件）。
- [x] T2. 删除 metadata `BoneModule` 全套：`domain/model/BoneModule.java`、`domain/repository/BoneModuleRepository.java`、`command/handler`(Create/Update/DeleteModuleHandler)、`command/cmd`(Create/UpdateModuleCommand)、`query/qry/ModulePageQuery.java`、`query/dto/ModuleDTO.java`（共 9 文件）。`MetaEntity`/`MetaField` 保留 `moduleId`（Long）引用 IAM `bone_module`，无需改 DDL、无需加冗余 appId。
- [x] T3. 新增只读引用实体与校验：`IamApplicationRef`(@Table bone_application)、`IamApplicationRepository`、`IamApplicationValidator`；`IamModuleRef`(@Table bone_module)、`IamModuleRepository`、`IamModuleValidator`。共库直查，不引远程调用、不重复定义写。
- [x] T4. 移除 metadata 对 App/Module 写入口（原 `AppCatalogController`/`ModuleCatalogController` 已删）；模块管理统一由 IAM `/api/v1/apps/{id}/modules` 提供，metadata 仅消费。

## 阶段二：后端测试（TDD）

- [x] T5. 单元测试 `IamApplicationValidator`（appId 为空/不存在拒绝、存在通过）— `IamApplicationValidatorTest`。
- [x] T6. 单元测试 `IamModuleValidator`（moduleId 为空/不存在拒绝、存在通过）— `IamModuleValidatorTest`。
- [x] T7. 接入 `IamModuleValidator`：`CreateMetaEntityHandler` 在 `moduleId != null` 时校验其存在于 IAM `bone_module`（null 放行以兼容历史数据）。编译 + 全量测试通过。
- [ ] T8. 契约测试：确认 metadata 调 IAM `/api/v1/iam/apps`、`/apps/{id}/modules` 响应结构与 IAM `ApplicationDTO`/`ModuleDTO` 一致（前端已用，后端契约稳定）。

## 阶段三：前端修正（仅注释，逻辑无需改）

> 经核实：前端 `bone-metadata-app/src/services/appModuleApi.ts` 的 `appApi`/`moduleApi` 已指向 `/api/v1/apps`、`/api/v1/apps/{id}/modules`（IAM 路径），`ApplicationManagement.tsx`/`ModuleManagement.tsx` 早已调 IAM。原 design 假设的"前端调 /metadata/apps 需改"不成立。

- [x] T9. 修正 `appModuleApi.ts` 过时错误注释（"后端未实现/走演示数据兜底"为虚假断言，实际调真后端 IAM 路径）。
- [x] T10. 确认前端无 `/metadata/apps`/`AppCatalog` 残留引用；`ApplicationManagement.tsx`/`ModuleManagement.tsx` 仅依赖 `appApi`/`moduleApi`（IAM 路径），无需改动。

## 阶段四：联调与收尾

- [ ] T11. E2E 冒烟：iam-app 应用管理 + 模块管理可用；metadata-app 选 IAM App/Module 建实体/字段全链路通过。
- [ ] T12. 更新 MEMORY.md 与架构文档，标注领域边界变更（IAM=App+Module，metadata=Entity+Field+Relation）。
