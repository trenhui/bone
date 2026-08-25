---
archived-with: 2026-08-25-dd-realign-appmodule-boundary
status: final
---
# 实施计划：应用/模块领域边界重划分（DDD 对齐）

- **Comet Change**: `dd-realign-appmodule-boundary`
- **Workflow**: full | **Language**: zh-CN
- **日期**: 2026-08-25
- **Design Doc**: `openspec/changes/dd-realign-appmodule-boundary/design.md`
- **Tasks**: `openspec/changes/dd-realign-appmodule-boundary/tasks.md`

> 本计划基于 Design Doc 与 tasks.md 拆解。build 阶段要求先记录 plan 才能写源码。

## 事实基线（build 阶段核实修正）

- `bone-init.sql` 仅一套 `bone_application`/`bone_module` 表（同库），但 IAM 与 metadata 两模块各有一个 Java `BoneApplication` 实体类映射到该表（**同表双映射**）。
- 方案从"跨库合并双表"收敛为：**删除 metadata 模块的 `BoneApplication` 实体类与全部 CRUD，统一由 IAM 独占 `bone_application`；metadata 的 `BoneModule.app_id` 保留物理引用 `bone_application.id`**。
- `meta_entity`/`meta_field` 表无 `app_id` 列，经 `bone_module` 间接归属应用，无需加列。

## 执行步骤（对齐 tasks.md T5–T17）

### 阶段一：后端收敛（metadata-server）
- [S1] 删除 metadata `catalog/domain/model/BoneApplication.java` 及其 command（`CreateAppCommand`/`UpdateAppCommand`/`DeleteAppCommand`）+ handler + query（`ApplicationPageQuery`+handler）+ `AppCatalogController` + `AppPermission` 相关。
- [S2] 保留 `BoneModule.app_id`（语义明确为引用 IAM `bone_application.id`），移除原本对 metadata 自建 App 的依赖；`MetaEntity`/`MetaField` 不动（已无 app 字段）。
- [S3] 新增 `IamApplicationValidator`：module 写入前校验 `bone_application.id` 存在（跨模块共库，直接查表或调 IAM 接口）；不存在返回明确错误。
- [S4] 改造 module create/list handler：按 `app_id` 过滤/关联（原逻辑不变，仅去掉对 metadata App 的隐含假设）。

### 阶段二：后端测试（TDD）
- [S5] 单测 `IamApplicationValidator`（不存在 appId 拒绝、存在通过）。
- [S6] 集成测试：module 按 app_id 查询回溯正确；`/api/v1/metadata/apps` 端点已移除、列表/创建走 IAM。
- [S7] 契约测试：metadata 调 IAM `/api/v1/iam/apps` 响应结构与 `ApplicationDTO` 一致。

### 阶段三：前端改造
- [S8] 修正 `bone-metadata-app/src/services/appModuleApi.ts` 错误注释（"后端未实现"）。
- [S9] `bone-metadata-app/ApplicationManagement.tsx`：移除或改为只读跳转至 iam-app 应用管理。
- [S10] `bone-metadata-app/ModuleManagement.tsx`：模块归属应用由 IAM App 选择器决定（调 `/api/v1/iam/apps`）。
- [S11] `bone-iam-app`：确认应用管理页为唯一入口（接入+成员+角色）。

### 阶段四：联调收尾
- [S12] E2E 冒烟：iam-app 应用管理可用；metadata-app 建模页选 IAM App 建模块/实体/字段全链路通过。
- [S13] 更新 MEMORY.md 与架构文档标注领域边界变更。

## 关键风险
- metadata 模块进程不依赖 IAM，但共库——`IamApplicationValidator` 直接查 `bone_application` 表（同库）即可，无需远程调用；若未来分库再升级为远程校验。
- 删除 metadata App CRUD 后，前端 ApplicationManagement 必须改调 IAM，否则功能丢失（S9/S10 必做）。
