# Proposal: 系统字典与定时任务

## 目标（Why）

`bone-system` 后端 45%，已有配置/日志/监控/告警，但缺字典管理与定时任务。字典是主数据「数据标准」的落地基座；定时任务是审计清理/质量检查的共享调度能力。前端 system-app 已有字典/任务留位页待替换为真实 API。

## 范围（What）

1. **字典聚合**：新增 `SysDict` 聚合（`type`/`typeName`/`code`/`label`/`value`/`sort`/`status`）+ `DictRepository` + Create/Update/Delete/QueryByType Command/Handler + `DictController`（`/api/v1/system/dicts`）。按 type 分组查询。
2. **定时任务**：新增 `ScheduleTask` 聚合（`name`/`cron`/`handler`/`status`/`lastRunAt`/`nextRunAt`）+ `ScheduleTaskRepository` + 动态调度器（`ThreadPoolTaskScheduler` 编程式注册 Cron）+ 启停 Command/Handler（管理 `ScheduledFuture`）+ `ScheduleTaskController`（`/api/v1/system/schedule-tasks`）。执行目标用统一 `TaskHandler` 接口，`ApplicationContext.getBean(handler)`。
3. **前端**：system-app 新增 `DictManagement.tsx`（类型分组）、`ScheduleTaskManagement.tsx`（启停 Switch + CRON 编辑），替换留位页，接真实 API。

## 非目标（Non-Goals）

- 不做分布式任务调度（XXL-JOB/Quartz 集群）、不做可视化任务编排。

## 验收标准（Acceptance Criteria）

- [ ] Dict CRUD 可用（类型分组 + 键值）。
- [ ] ScheduleTask 启停/CRON 管理可用，任务真实执行。
- [ ] 前端字典/任务页接后端 API 替换留位。
- [ ] ArchUnit 通过、spotless 格式一致。
