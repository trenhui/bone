## Why

在元数据 / 扩展引擎模块的前后端功能与联调审查中，发现两类问题阻碍"功能完成 + 联调通过"的闭环：

1. **前端路由缺陷（Bug 级）**：`bone-extension-app` 的 `/logs` 路由错误地指向沙箱页（`SandboxManagement`），与 `/sandbox` 重复，导致"运行日志"菜单无独立页面；`DeploymentStateDiagram` 部署管理页为静态占位，不消费任何后端接口。
2. **后端联调保障缺失（测试债，P0）**：`bone-metadata-server`（:9001，核心数据面）仅有分层门禁与 validator 单测，**缺少 Controller 级集成测试与真实 MySQL E2E**。相较之下 `bone-extension-studio` 已有完整的 Controller 集成测试 + MySQL/Redis E2E。一旦元数据主链路（实体/字段/校验规则/审批流 + moduleId 引用 IAM 校验）回归，无自动化拦截，风险最高。

extension-studio 已证明后端真实 E2E 链路可用，本次以"后端 E2E 为主"补齐 metadata-server 的缺口，并修复 extension 前端路由/占位，使两个引擎模块均达到"功能完成 + 联调有自动化保障"的状态。

## What Changes

- 修复 `bone-extension-app/src/App.tsx` 的 `/logs` 路由：新建独立的运行日志页（从 `SandboxManagement` 抽取 `loadLogs` 逻辑），`/logs` 指向它，消除与 `/sandbox` 的重叠。
- 处理 `DeploymentStateDiagram` 静态占位：接入后端 `getDeploymentState` / `getDependencyGraph` 渲染真实部署状态图；若评估成本过高则先从菜单移除，避免误导（以接入为优先目标）。
- 为 `bone-metadata-server` 新增 **Controller 集成测试**（`@SpringBootTest` + MockMvc），覆盖实体、字段、校验规则、视图、审批流的主链路增删改查。
- 为 `bone-metadata-server` 新增 **真实 MySQL E2E 测试**，固化已验证的"moduleId 引用 IAM 校验"路径（`IamModuleValidator.requireExists`，moduleId 不存在时返回校验错误）。
- 核对 `bone-metadata-app` 前端 `runtime` 引擎（`/api/v1/runtime` 的 `executeFunction` / `previewSql` 等）的后端归属与可达性，明确"前端有调用、后端在哪"，并在 design 中记录结论。

**非目标（明确不做）**：
- 不引入前端浏览器级 E2E 框架（Playwright/Cypress）。
- 不改动后端业务领域逻辑（仅新增测试与修复前端路由/占位）。
- 不处理 `PluginManagement.tsx` 拆分与模块 runbook（P2，留待后续 change）。
- 不涉及 IAM / 其他平台模块。

## Capabilities

### New Capabilities

- `metadata-server-e2e`：元数据服务端到端测试能力。要求 `bone-metadata-server` 具备可自动化验证的 Controller 集成测试与真实 MySQL E2E，覆盖主链路（实体/字段/校验规则/审批流）及 moduleId 引用 IAM 校验的异常路径。
- `extension-frontend-routing`：扩展引擎前端路由正确性能力。要求扩展微应用的路由与页面一一对应，运行日志有独立页面，部署管理页消费真实后端数据（或明确下架）。

### Modified Capabilities

（无。本 change 不修改既有 spec 的需求。）

## Impact

- **后端代码**：`bone-engine/bone-metadata-server/src/test/java/...` 新增集成测试与 E2E 测试类；可能新增测试用 Spring profile / testcontainer 或复用本地 MySQL（`BONE_DB_*` 环境变量已配置）。
- **前端代码**：`bone-frontend/apps/bone-extension-app/src/App.tsx`（路由修复）、新增 `ExecutionLogPage.tsx`、修改 `DeploymentStateDiagram.tsx`；`bone-frontend/apps/bone-metadata-app` 仅核对 runtime 归属（大概率无改动，仅文档结论）。
- **依赖**：测试沿用现有 `spring-boot-starter-test` / JUnit 5 / MockMvc，不引入新测试框架。
- **系统**：依赖本地 MySQL（`MYSQL_DATABASE=bone`）与 Redis（extension-studio E2E 已用），metadata-server E2E 复用同套基础设施。
- **验收影响**：`openspec validate` 须通过；新增测试在本地 MySQL 真实运行通过；前端路由修复后 `/logs` 渲染独立日志页。
