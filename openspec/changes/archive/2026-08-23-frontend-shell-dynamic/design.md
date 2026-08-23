# Design: Shell 动态化与测试补齐

## Context
`bone-shell` 85%：qiankun 7/7 注册、布局/登录/token 共享/错误边界完整。但菜单硬编码写死、微应用路由无登录态守卫（仅 Dashboard 有 Authorized）、个人中心无页、改密接口未接、通知 Badge 写死。vitest 全工程仅 5 文件，70% 门槛必挂。

## Decision 1：动态菜单
- Shell 启动后调用 IAM `Menu` 聚合 API（`iam-org-menu-baseline` 提供）拉取菜单树，按当前用户角色过滤，动态生成侧边栏。
- 保留本地 fallback 菜单（IAM 不可用时）。

## Decision 2：路由守卫
- 微应用注册路由增加登录态守卫：未登录跳转 login；基于 token 存在 + 后端 401 兜底（现有机制保留）。

## Decision 3：个人中心/改密
- 新增 `Profile` 页（用户信息展示 + 改密表单），改密调 IAM `/api/v1/iam/auth/change-password`（若存在）或新增。
- 通知 Badge 接真实未读计数（如 notification 模块就绪后）。

## Decision 4：测试
- vitest 补充：`shared-services/apiClient`（拦截器/token 链/401 事件）、登录流程、菜单加载。目标关键路径覆盖。

## Risks
- 动态菜单需 iam-org-menu-baseline 先完成（依赖）。
- 改密接口若 IAM 未提供需先在 IAM 补齐。
