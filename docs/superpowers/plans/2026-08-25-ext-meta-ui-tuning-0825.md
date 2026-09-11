---
archived-with: ext-meta-ui-tuning-0825
status: final
---
# 实施计划：扩展前端路由修复 + 元数据服务端 E2E 测试补全

- **Comet Change**: `ext-meta-ui-tuning-0825`
- **Workflow**: full | **Language**: zh-CN
- **日期**: 2026-08-25
- **Design Doc**: `openspec/changes/ext-meta-ui-tuning-0825/design.md`
- **Tasks**: `openspec/changes/ext-meta-ui-tuning-0825/tasks.md`

> 本计划基于 Design Doc 与 tasks.md 拆解。build 阶段要求先记录 plan 才能写源码。

## 事实基线（build 阶段核实）

- **前端**：`bone-extension-app/src/App.tsx` 的 `/logs` 路由（第 68 行）错误指向 `SandboxManagement`，与 `/sandbox`（第 64 行）重叠；`/deploy` 路由（第 67 行）渲染 `DeploymentStateDiagram` 但未传 `state`/`pluginId`，始终显示空占位。`extensionApi.ts` 已具备 `listExecutionLogs` / `getDeploymentState` / `getDependencyGraph` / `getSandboxConfig` / `listAuditLogs` 等调用，后端 `ExtensionManagementController` 前缀 `/api/v1/extension` 前后端一致。
- **后端**：`bone-metadata-server`（:9001）现有测试仅分层门禁 + validator 单测，缺 Controller 集成测试与真实 MySQL E2E。对照 `bone-extension-studio` 已有范式：`ExtensionManagementControllerTest`、`StudioMetadataMysqlIT`（真实 MySQL）、`StudioRuntimeSyncRedisE2ETest`。本地 MySQL（`MYSQL_DATABASE=bone`、`BONE_DB_PASSWORD=mysql123`）与 Redis 已就绪。
- **runtime 归属**：`bone-metadata-app` 前端 `metadataApi.ts` 含 `RUNTIME='/api/v1/runtime'`（`executeFunction`/`previewSql`），需 build 阶段核对 `bone-metadata-engine` 的挂载形态（任务 4.1）。

## 执行步骤（对齐 tasks.md）

### 阶段一：扩展前端路由与页面修复
- [S1] 新建 `ExecutionLogPage.tsx`：从 `SandboxManagement` 抽取 `loadLogs` 逻辑（调 `listExecutionLogs`），独立渲染运行日志视图（状态筛选 + 分页加载），日志查询逻辑仅此一处定义。
- [S2] 修改 `App.tsx`：`/logs` 指向 `ExecutionLogPage`；`SandboxManagement` 移除执行日志区（保留沙箱配置 + 审计日志 `listAuditLogs`），避免逻辑重复。验证 `grep -rn "loadLogs"` 仅一处定义。
- [S3] 新建 `DeploymentManagementPage.tsx`：提供插件选择器，调 `getDeploymentState(pluginId)` + `getDependencyGraph()`，将结果传给 `DeploymentStateDiagram` 渲染真实状态机/依赖图；无数据时显示"暂无部署数据"空态。`/deploy` 指向该页。若 pluginId 来源在 UI 无入口，则降级为下架菜单项并留 TODO。

### 阶段二：元数据服务端 Controller 集成测试
- [S4] 新增 `MetadataManagementControllerTest`（`@SpringBootTest` + MockMvc），覆盖实体 CRUD（`/api/v1/metadata/entities`）。参考 `ExtensionManagementControllerTest` 的装配方式。
- [S5] 扩展集成测试：字段创建 + 校验规则写入后查询实体详情包含该定义（`/fields`、`/validations`）。
- [S6] 扩展集成测试：视图与审批流接口（`/views`、`/approval-flows`）可达且返回结构化结果。

### 阶段三：元数据服务端真实 MySQL E2E
- [S7] 新增 MySQL E2E 测试类（参考 `StudioMetadataMysqlIT`），复用 `BONE_DB_*` 变量连本地 `bone` 库。
- [S8] E2E 固化 moduleId 引用 IAM 校验异常路径：创建实体指定不存在的 `moduleId` → `IamModuleValidator.requireExists` 拒绝且无 `meta_entity` 脏数据。
- [S9] E2E 验证建模数据真实落库并可经接口回读（实体 + 字段）。

### 阶段四：runtime 引擎归属核对
- [S10] 搜索 `bone-metadata-engine` 是否暴露 `/api/v1/runtime` 或被 `bone-metadata-server` 装配；在 `design.md` 新增"Runtime 核对结论"小节记录结论（"已挂载于 X" 或 "后端归属待定，前端调用将 404，已在 doc 标注"）。不阻塞其余任务。

### 阶段五：验证与收尾
- [S11] 运行 `bone-metadata-server` 全部测试，确认集成测试 + MySQL E2E 真实通过（本地 MySQL 可达）。
- [S12] `bone-extension-app` 运行 `npm run typecheck` + `npm run lint` 全绿。
- [S13] `openspec validate --change ext-meta-ui-tuning-0825` 通过。

## 关键风险
- **MySQL E2E 依赖本地库状态** → 测试使用独立测试数据并清理/幂等；复用 extension-studio 已验证的连接参数。
- **部署页接入需 pluginId 但 UI 无选择器** → S3 已给降级路径（下架 + TODO），不阻塞主链路。
- **runtime 引擎若独立服务，前端调用会 404** → S10 核对后明确契约，必要时在 doc 标注，不强行修前端。
- **full workflow 写源码前必须 plan 已记录** → 本文件即 plan，`comet state set ... plan` 后恢复 build 写入。
