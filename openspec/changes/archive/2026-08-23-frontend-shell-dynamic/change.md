# Change: Shell 动态化与测试补齐

**Type**: Feature / Test（前端里子工程）
**Status**: Proposed
**Source**: 用户统一优先级规划（阶段3 T10+T12）。基于扫描：bone-shell 85%，菜单硬编码、微应用路由无登录态守卫、个人中心/改密未对接；vitest 仅 5 文件，70% 门槛必挂。
**Depends on**: iam-org-menu-baseline（依赖动态菜单数据）

## Intent
将 Shell 菜单改为 IAM 动态下发、补充微应用路由登录态守卫、对接个人中心/改密接口，并补齐关键路径测试。

## Scope (In)
- `bone-shell`：菜单数据源改为 IAM（Menu 聚合）动态下发；微应用路由加登录态守卫；个人中心页 + 改密接口对接；通知 Badge 接真实数据。
- 各 app：vitest 补充（优先 shared-services/apiClient、关键页面）。

## Scope (Out / Non-Goals)
- 不做全新设计系统。
- 不做移动端/小程序。

## Assumptions
- iam-org-menu-baseline change 已提供 Menu 聚合 API。
- 登录态已通过 token 在 shell 与子应用共享。

## Acceptance Criteria
- [ ] 菜单来自 IAM 动态下发（按角色过滤）。
- [ ] 微应用路由有登录态守卫。
- [ ] 改密接口对接可用。
- [ ] 关键路径（apiClient、登录流程）有 vitest 覆盖。
