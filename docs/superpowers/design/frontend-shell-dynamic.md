---
comet_change: frontend-shell-dynamic
role: technical-design
canonical_spec: openspec
archived-with: 2026-08-23-frontend-shell-dynamic
status: final
---

# Design Doc: Shell 动态化与测试补齐（frontend-shell-dynamic）

> Comet Classic `full` workflow 的 Superpowers Design Doc（桥梁产物）。
> 配套 OpenSpec 产物：`openspec/changes/frontend-shell-dynamic/{proposal,design,tasks}.md`
> 本文件记录**关键设计决策与选项评估**；任务拆解见 `tasks.md`。

## 背景与目标

`bone-shell` 当前完成度约 85%（qiankun 7/7 注册、布局/登录/token 共享/错误边界完整），但：菜单硬编码、微应用路由仅有 Dashboard 有登录态守卫、无个人中心/改密页、通知 Badge 写死、vitest 仅 5 文件（70% 门槛必挂）。

本 change 在已完成的 `frontend-shared-layer` 共享层之上（shared-config / shared-types / shared-services / core-event-bus），补齐 shell 动态化能力并补关键测试。

## 决策与选项评估

### 决策 1：动态菜单数据源
- **选项 A（选中）**：Shell 启动后调用 IAM `Menu` 聚合 API（由 `iam-org-menu-baseline` 提供）拉取菜单树，按当前用户角色过滤，动态生成侧边栏；IAM 不可用时保留本地 fallback 菜单。
- 选项 B：纯前端配置菜单（不依赖 IAM）。违背"按角色过滤"需求，放弃。
- **后果**：菜单统一管理在 IAM，前端仅渲染 + fallback；需 iam-org-menu-baseline 提供 API。

### 决策 2：路由守卫
- **选项 A（选中）**：微应用注册路由统一增加登录态守卫（基于 token 存在判断，未登录跳 login），保留后端 401 兜底（现有机制）。
- 选项 B：仅后端 401 兜底，前端不限流。越权风险，放弃。
- **后果**：前端减少误触未授权页，后端仍为最终权威。

### 决策 3：个人中心/改密
- **选项 A（选中）**：新增 `Profile` 页（用户信息 + 改密表单）；改密对接 IAM `/api/v1/iam/auth/change-password`（若 IAM 未提供，本 change 内先在 IAM 补 stub + 契约约定）。
- **后果**：shell 具备改密闭环；后端契约以共享类型 `shared-types` 收敛。

### 决策 4：测试补齐
- **选项 A（选中）**：vitest 补充 `shared-services/apiClient`（拦截器/token 链/401 事件）、登录流程、菜单加载关键路径，目标关键路径覆盖。
- 选项 B：追求 70% 整工程覆盖率。工作量过大，放弃，留作后续。
- **后果**：先把核心链路从 0 测试补到可用，逐步达标。

## 关键依赖与冲突

### 依赖：iam-org-menu-baseline
- 动态菜单的 Menu 聚合 API 由 `iam-org-menu-baseline` 提供；改密接口 `/api/v1/iam/auth/change-password` 可能也需该 change 补齐。
- **缓解**：本 change 在共享类型 `shared-types` 中预留 Menu/ChangePassword 契约；若依赖未就绪，前端用 mock/fallback 先行，后端契约后续对接。

### 与 frontend-shared-layer 的衔接
- 复用 `@bone/shared-config`（vite 配置）、`@bone/shared-types`（类型）、`@bone/shared-services`（apiClient）、`@bone/core-event-bus`（跨应用广播）。
- 之前 frontend-shared-layer 标记「人工待验」的 event-bus 运行时链路，可在本 change 通过 shell 动态菜单下发 + iam 响应得到进一步验证。

## 各任务设计要点

- **动态菜单**：shell bootstrap 调 IAM Menu API → 角色过滤 → 渲染侧边栏；fallback 保留本地静态菜单常量。
- **路由守卫**：在 qiankun 注册配置 / 路由封装处加 `Authorized` 包裹 + 401 拦截跳转 login。
- **个人中心/改密**：新增 `Profile` 页（React + antd Form），调 `shared-services` 的 `authService.changePassword`；通知 Badge 接 `@bone/shared-services` 未读计数接口（notification 就绪后）。
- **测试补齐**：vitest + @testing-library/react；覆盖 apiClient 拦截器（token 注入、401 → 事件 / 跳转）、登录流程、菜单加载。

## 风险与缓解

- 依赖 iam-org-menu-baseline 未实现 → 前端先做契约 + mock，后端就绪后切换。
- 改密接口契约不确定 → 在 shared-types 先定义请求/响应类型，IAM 侧按需补齐。
- 测试环境需 mock axios → 用 vitest 的 vi.mock 隔离 apiClient。
