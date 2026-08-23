# Design: 系统字典与定时任务

## Context
`bone-system` 后端 45%，已有配置/日志/监控/告警/部署，但缺字典与定时任务。字典是主数据"数据标准"的落地基座；定时任务是审计清理/质量检查的共享调度能力。前端 system-app 已有字典/任务留位页待替换。

## Decision 1：字典聚合
- `SysDict` 聚合：含 `type`（字典类型）、`typeName`、`code`、`label`、`value`、`sort`、`status`。查询按 type 分组。
- 复用 `TenantAbstractEntity` + `BaseRepository`。

## Decision 2：定时任务
- `ScheduleTask` 聚合：含 `name`、`cron`、`beanName`/`handler`（执行目标）、`status`（启用/停用）、`lastRunAt`、`nextRunAt`。
- 调度器：基于 Spring `TaskScheduler` + 动态注册（`SchedulingConfigurer` 或 `ThreadPoolTaskScheduler` 编程式注册 Cron）。启用/停用通过增删 `ScheduledFuture` 实现。
- 执行目标用 `ApplicationContext.getBean(handler)` 调用 `Runnable`/`@Scheduled` 方法，或统一 `TaskHandler` 接口。

## Decision 3：前端
- system-app 新增 `DictManagement.tsx`、`ScheduleTaskManagement.tsx`，替换现有留位；用 ProTable + 启停 Switch。

## Risks
- 动态 Cron 注册需线程池隔离，避免阻塞主调度。
- 任务执行失败需记录日志（复用现有 sys_log）。
