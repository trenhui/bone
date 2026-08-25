## Context

元数据 / 扩展引擎模块前后端功能与联调审查（见 proposal.md - Why）暴露两类问题：

- **前端**：`bone-extension-app` 的 `/logs` 路由（App.tsx:68）错误地指向 `SandboxManagement`，与 `/sandbox` 重叠；`DeploymentStateDiagram.tsx` 为静态占位，不调用任何后端接口。后端 `ExtensionManagementController` 已提供 `/sandbox/config`、`/marketplace`、`/audit-logs`、`/dependency-graph`、`/deployment-state` 等完整接口，前端 `extensionApi.ts` 亦已封装 `getSandboxConfig` / `listMarketplaceItems` / `getDeploymentState` / `getDependencyGraph` 等调用，前缀 `/api/v1/extension` 前后端一致。
- **后端**：`bone-metadata-server` 仅有分层门禁与 validator 单测，缺少 Controller 集成测试与真实 MySQL E2E；对照 `bone-extension-studio` 已有 `ExtensionManagementControllerTest`、`StudioMetadataMysqlIT`、`StudioRuntimeSyncRedisE2ETest` 等范式可复用。本地 MySQL（`MYSQL_DATABASE=bone`，`BONE_DB_PASSWORD=mysql123`）与 Redis 已就绪。

设计目标：以最小改动补齐测试保障与前端路由缺陷，不引入新框架、不改业务领域逻辑。

## Goals / Non-Goals

**Goals:**
- 为 metadata-server 补齐 Controller 集成测试 + 真实 MySQL E2E，固化 moduleId 引用 IAM 校验异常路径。
- 修复 extension-app `/logs` 路由，抽出独立运行日志页。
- 让 `DeploymentStateDiagram` 接入真实后端数据（优先接入，其次下架）。
- 在 build 阶段核对 metadata `runtime` 引擎（`/api/v1/runtime`）的后端归属与可达性，并落结论。

**Non-Goals:**
- 不引入前端浏览器级 E2E（Playwright/Cypress）。
- 不改动后端业务领域模型 / 写侧逻辑（仅新增测试）。
- 不处理 `PluginManagement.tsx` 拆分、模块 runbook（P2 后续项）。
- 不触碰 IAM / 其他平台模块。

## Decisions

### D1: 测试范式复用 extension-studio 而非另起炉灶
- **选择**：metadata-server 的集成测试采用 `@SpringBootTest` + `MockMvc`（参考 `ExtensionManagementControllerTest`），真实 MySQL E2E 直接连本地库（参考 `StudioMetadataMysqlIT`），复用 `BONE_DB_*` 环境变量，不引 Testcontainers。
- **理由**：团队已有成熟范式，降低维护认知成本；本地 MySQL 已配置，真实库 E2E 更贴近生产。
- **替代**：Testcontainers 隔离更好但需 Docker，当前环境未强制，故不采用。

### D2: 前端日志逻辑抽取为共享函数/组件
- **选择**：从 `SandboxManagement` 抽取 `loadLogs` 相关逻辑到独立 `ExecutionLogPage.tsx`（或共享 hook），`/logs` 指向新页面，`/sandbox` 保留沙箱配置视图（仍可内嵌日志区）。共享逻辑不重复实现。
- **理由**：消除路由重叠缺陷，同时保留沙箱页内查看日志的便利性。
- **替代**：仅改路由指向不改结构 → 仍会导致逻辑重复，故抽取。

### D3: 部署页优先接入真实数据
- **选择**：`DeploymentStateDiagram` 接入 `getDeploymentState(pluginId)` + `getDependencyGraph()` 渲染状态机 / 依赖图；若 pluginId 来源在 UI 无入口，则先加插件选择器，仍不通则临时下架菜单项并留 TODO。
- **理由**：静态占位对用户有误导，接入能真正闭合"部署管理"能力。

### D4: runtime 引擎归属核对方式
- **选择**：build 阶段搜索 `bone-metadata-engine` 是否暴露 `/api/v1/runtime`（或被 metadata-server 装配），并在 design/tasks 记录结论；若 runtime 引擎未挂载 HTTP，则前端 `runtime` 调用需在 memory/doc 标注"后端归属待定"，不强行伪造。
- **理由**：澄清"前端有调用、后端在哪"的悬空契约，避免误导后续联调。

## Risks / Trade-offs

- **[Risk] MySQL E2E 依赖本地库状态** → Mitigation：测试使用独立测试数据并清理，或对写操作做幂等/回滚；复用 extension-studio 已验证的本地库连接参数。
- **[Risk] 部署页接入需 pluginId 但 UI 无选择器** → Mitigation：D3 已给降级路径（下架 + TODO），不阻塞主链路。
- **[Risk] runtime 引擎若为独立服务，前端调用会 404** → Mitigation：D4 核对后明确契约，必要时在 doc 标注，不强行修前端。
- **[Trade-off] 真实 MySQL 而非内存库**：测试更真实，但要求本地 MySQL 可达；团队已具备该环境。

## Open Questions

- metadata `runtime` 引擎（`/api/v1/runtime`）的确切后端归属：build 阶段（D4）核对后填入本 design 的"Runtime 核对结论"小节，不阻塞 specs/tasks 拆分。

## Runtime 核对结论（S10，2026-08-25）

经 build 阶段核对：`bone-metadata-server` 内存在 `runtime/adapter/web/RuntimeRecordController.java`（包 `com.bone.metadata.runtime.adapter.web`），即 **runtime 引擎（`/api/v1/runtime`）是挂载在 `bone-metadata-server`（:9001）内部的**，并非独立服务。

结论：
- 前端 `bone-metadata-app` 的 `metadataApi.ts` 中 `RUNTIME='/api/v1/runtime'`（`executeFunction` / `previewSql` 等）调用的后端归属**明确且正确**，前缀 `/api/v1/runtime` 由 metadata-server 自身提供，无 404 风险。
- 无需新增跨服务调用或文档标注"后端归属待定"；前端契约与后端一致，联调无悬空依赖。
