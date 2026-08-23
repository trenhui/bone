# Proposal: Shell 动态化与测试补齐

## Why

`bone-shell` 当前完成度约 85%（qiankun 7/7 注册、布局/登录/token 共享/错误边界完整），但存在几个关键缺口：

- **菜单硬编码**：侧边栏菜单写死在前端，无法按角色动态下发，无法满足多租户/多角色场景。
- **路由无登录态守卫**：微应用注册路由仅有 Dashboard 有 `Authorized` 包裹，其余入口未登录也可直达，存在越权风险。
- **个人中心缺失**：无用户信息与改密页面，改密接口未对接。
- **通知 Badge 写死**：未接真实未读计数。
- **测试缺口**：全工程 vitest 仅 5 文件，70% 覆盖率门槛必挂，关键路径（apiClient 拦截器/token 链/401 事件）无覆盖。

本次在已完成的前端共享层（`frontend-shared-layer`：shared-config / shared-types / shared-services / core-event-bus）基础上，补齐 shell 动态化能力并补关键测试，使 shell 达到可用、可测、可灰度状态。

## What Changes

1. **动态菜单**：Shell 启动后调用 IAM `Menu` 聚合 API（由 `iam-org-menu-baseline` 提供）拉取菜单树，按当前用户角色过滤，动态生成侧边栏；IAM 不可用时保留本地 fallback 菜单。
2. **路由守卫**：微应用注册路由统一增加登录态守卫（未登录跳 login），保留后端 401 兜底机制。
3. **个人中心/改密**：新增 `Profile` 页（用户信息 + 改密表单），改密对接 IAM `/api/v1/iam/auth/change-password`（若缺失先在 IAM 补）；通知 Badge 接真实未读计数（notification 模块就绪后）。
4. **测试补齐**：vitest 补充 `shared-services/apiClient`（拦截器/token 链/401 事件）、登录流程、菜单加载关键路径，目标关键路径覆盖（为后续 70% 门槛打底）。

## Impact

- **范围 In**：`bone-shell`（菜单数据源、路由守卫、Profile 页、改密接口、通知 Badge）；各 app 的 vitest 补充（优先 shared-services/apiClient、关键页面）。
- **范围 Out / Non-Goals**：不做全新设计系统；不做移动端/小程序。
- **依赖**：`iam-org-menu-baseline`（提供 Menu 聚合 API 与改密接口兜底）。登录态已通过 token 在 shell 与子应用共享（已由 frontend-shared-layer 落地）。
- **风险**：动态菜单需 `iam-org-menu-baseline` 先完成；改密接口若 IAM 未提供需先在 IAM 补齐（本 change 内可补 stub + 契约约定）。
